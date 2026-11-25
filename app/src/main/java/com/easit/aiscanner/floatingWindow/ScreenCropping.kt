package com.easit.aiscanner.floatingWindow

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.easit.aiscanner.R
import com.easit.aiscanner.data.AppearanceStore
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.floatingWindow.NotificationUtils.CapturePhase
import com.easit.aiscanner.scannerAI.MlKitModules
import com.easit.aiscanner.ui.overlay.CaptureEditor
import com.easit.aiscanner.ui.overlay.CaptureEditorModel
import com.easit.aiscanner.ui.overlay.OverlayWindow
import com.easit.aiscanner.ui.theme.TransVerseTheme
import com.easit.aiscanner.ui.translate.RecentLanguages
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Freeze-frame screen capture: grabs ONE frame of the whole screen through MediaProjection, then
 * shows the capture editor over that frozen screenshot in a focusable overlay, where the user
 * selects text at their own pace and translates it in place.
 *
 * Normally the projection stops right after the frame. In capture session mode
 * ([Preference.captureSessionEnabled], only while the bubble runs) it stays open between captures
 * with the virtual display's surface detached, and the bubble triggers [ACTION_CAPTURE] directly.
 * The session ends when the bubble closes, from the notification, or when Android stops the
 * projection (e.g. from the status-bar chip). Android 14+: one consent = one projection, so the
 * consent result is never reused for a second projection.
 */
@AndroidEntryPoint
class ScreenCropping : Service() {

    @Inject lateinit var preference: Preference
    @Inject lateinit var mlKitModules: MlKitModules
    @Inject lateinit var repository: ScanRepository
    @Inject lateinit var recentLanguages: RecentLanguages
    @Inject lateinit var appearanceStore: AppearanceStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler

    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var sessionMode = false
    private var captureJob: Job? = null
    private var editorWindow: OverlayWindow? = null
    private var editorModel: CaptureEditorModel? = null

    /** This instance claimed the capture slot and must release it. */
    private var ownsCapture = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post { onProjectionStopped() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        captureThread = HandlerThread("TransVerseScreenCapture").apply { start() }
        captureHandler = Handler(captureThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromConsent(intent)
            ACTION_CAPTURE -> captureInSession()
            ACTION_STOP, ACTION_END_SESSION -> {
                endSession()
                stopIfIdle()
            }
            else -> stopIfIdle()
        }
        return START_NOT_STICKY
    }

    // region Starting a capture

    private fun startFromConsent(intent: Intent) {
        ownsCapture = true
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val projectionData = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)

        // Started with startForegroundService, so startForeground must always be called. On
        // Android 14+ the mediaProjection type is refused without fresh consent, which only
        // happens for a malformed start; fail quietly instead of crashing.
        val inForeground = runCatching { promoteToForeground(CapturePhase.CAPTURING) }
            .onFailure { Log.w(TAG, "Could not start screen capture in the foreground", it) }
            .isSuccess
        if (!inForeground || resultCode != Activity.RESULT_OK || projectionData == null) {
            failCapture(R.string.overlay_error_capture_failed)
            return
        }
        if (captureJob?.isActive == true || editorWindow != null) {
            // A capture is already on screen; don't start a second projection on top of it.
            return
        }

        // Never more than one projection.
        releaseProjection()
        val newProjection = try {
            getSystemService(MediaProjectionManager::class.java).getMediaProjection(resultCode, projectionData)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start the projection", e)
            null
        }
        if (newProjection == null) {
            failCapture(R.string.overlay_error_capture_failed)
            return
        }
        // Android 14+ requires the callback before the virtual display is created.
        newProjection.registerCallback(projectionCallback, captureHandler)
        projection = newProjection
        capturer = ScreenCapturer(this, newProjection, captureHandler)
        sessionMode = preference.captureSessionEnabled && CaptureCoordinator.bubbleRunning.value
        if (sessionMode) CaptureCoordinator.setSessionActive(true)
        runCapture(afterConsent = true)
    }

    private fun captureInSession() {
        if (captureJob?.isActive == true || editorWindow != null) return
        ownsCapture = true
        if (!sessionMode || projection == null || capturer == null) {
            CaptureCoordinator.setSessionActive(false)
            failCapture(R.string.overlay_error_session_ended)
            return
        }
        runCapture(afterConsent = false)
    }

    private fun runCapture(afterConsent: Boolean) {
        val activeCapturer = capturer ?: run {
            failCapture(R.string.overlay_error_capture_failed)
            return
        }
        updateNotification(CapturePhase.CAPTURING)
        captureJob = scope.launch {
            val frame = try {
                activeCapturer.capture(settle = {
                    if (afterConsent) {
                        // Wait for the consent activity to leave the screen.
                        withTimeoutOrNull(CONSENT_GONE_TIMEOUT_MS) {
                            CaptureCoordinator.consentVisible.first { visible -> !visible }
                        }
                        delay(CONSENT_SETTLE_MS)
                    } else {
                        // The bubble was just hidden; let that reach the screen.
                        delay(SESSION_SETTLE_MS)
                    }
                })
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "No frame arrived in time", e)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Screen capture failed", e)
                null
            }

            if (sessionMode && projection != null) capturer?.detach() else releaseProjection()

            if (frame == null) {
                failCapture(R.string.overlay_error_capture_failed)
                return@launch
            }
            val image = withContext(Dispatchers.Default) { frame.downscaledIfHuge() }
            showEditor(image)
        }
    }

    // endregion

    // region Editor

    private fun showEditor(image: Bitmap) {
        val model = CaptureEditorModel(
            context = this,
            bitmap = image,
            modules = mlKitModules,
            repository = repository,
            preference = preference,
            recentLanguages = recentLanguages,
            // Deferred: the editor's own click handler is still running when it asks to close.
            onClose = { mainHandler.post { closeEditor() } },
        )
        val window = OverlayWindow(this)
        window.setContent {
            val appearance by appearanceStore.settings.collectAsState()
            TransVerseTheme(settings = appearance) {
                CaptureEditor(model)
            }
        }
        val params = OverlayWindow.fullScreenParams(focusable = true).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        if (!window.show(params)) {
            window.destroy()
            model.release()
            failCapture(R.string.overlay_error_overlay_failed)
            return
        }
        editorWindow = window
        editorModel = model
        model.start()
        updateNotification(if (sessionMode) CapturePhase.SESSION else CapturePhase.EDITING)
    }

    private fun closeEditor() {
        val window = editorWindow ?: return
        editorWindow = null
        window.destroy()
        editorModel?.release()
        editorModel = null
        releaseCaptureSlot()
        if (sessionMode && projection != null) {
            updateNotification(CapturePhase.SESSION)
        } else {
            stopIfIdle()
        }
    }

    // endregion

    // region Projection and lifecycle

    private fun onProjectionStopped() {
        if (projection == null) return
        // Stopped by Android or the user (status-bar chip), not by us: the session is over.
        Log.i(TAG, "The screen capture was stopped")
        capturer?.release()
        capturer = null
        projection?.unregisterCallback(projectionCallback)
        projection = null
        sessionMode = false
        CaptureCoordinator.setSessionActive(false)
        if (captureJob?.isActive == true) {
            captureJob?.cancel()
            failCapture(R.string.overlay_error_capture_failed)
            return
        }
        if (editorWindow != null) updateNotification(CapturePhase.EDITING) else stopIfIdle()
    }

    /** Ends a capture session; an open editor stays until the user closes it. */
    private fun endSession() {
        if (captureJob?.isActive == true) {
            captureJob?.cancel()
            releaseCaptureSlot()
        }
        releaseProjection()
        if (editorWindow != null) updateNotification(CapturePhase.EDITING)
    }

    private fun releaseProjection() {
        capturer?.release()
        capturer = null
        projection?.let { current ->
            runCatching { current.unregisterCallback(projectionCallback) }
            runCatching { current.stop() }
        }
        projection = null
        if (sessionMode) CaptureCoordinator.setSessionActive(false)
        sessionMode = false
    }

    private fun failCapture(@StringRes message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        releaseCaptureSlot()
        stopIfIdle()
    }

    private fun releaseCaptureSlot() {
        if (ownsCapture) {
            ownsCapture = false
            CaptureCoordinator.endCapture()
        }
    }

    /** Stops unless an editor is open, a frame is being grabbed, or a session is live. */
    private fun stopIfIdle() {
        val busy = editorWindow != null ||
            captureJob?.isActive == true ||
            (sessionMode && projection != null)
        if (!busy) stopSelf()
    }

    private fun promoteToForeground(phase: CapturePhase) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationUtils.CAPTURE_NOTIFICATION_ID,
            NotificationUtils.captureNotification(this, phase),
            type,
        )
    }

    private fun updateNotification(phase: CapturePhase) {
        NotificationUtils.update(
            this,
            NotificationUtils.CAPTURE_NOTIFICATION_ID,
            NotificationUtils.captureNotification(this, phase),
        )
    }

    override fun onDestroy() {
        editorWindow?.destroy()
        editorWindow = null
        editorModel?.release()
        editorModel = null
        captureJob?.cancel()
        releaseProjection()
        releaseCaptureSlot()
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        captureHandler.removeCallbacksAndMessages(null)
        captureThread.quitSafely()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // endregion

    companion object {
        private const val TAG = "ScreenCropping"
        private const val ACTION_START = "com.easit.aiscanner.action.START_SCREEN_CAPTURE"
        private const val ACTION_CAPTURE = "com.easit.aiscanner.action.CAPTURE_IN_SESSION"
        private const val ACTION_END_SESSION = "com.easit.aiscanner.action.END_CAPTURE_SESSION"
        internal const val ACTION_STOP = "com.easit.aiscanner.action.STOP_SCREEN_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "projection_data"
        private const val CONSENT_GONE_TIMEOUT_MS = 1_500L
        private const val CONSENT_SETTLE_MS = 300L
        private const val SESSION_SETTLE_MS = 200L

        /** Longest side of the editor's screenshot; bigger captures are scaled down. */
        private const val MAX_IMAGE_EDGE = 2_880

        /** Starts a capture with a fresh consent result (from [ImageTransparentActivity]). */
        fun getStartIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenCropping::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)

        /** Captures again in the live session (no consent prompt). */
        fun captureIntent(context: Context): Intent =
            Intent(context, ScreenCropping::class.java).setAction(ACTION_CAPTURE)

        /** Ends the capture session (the bubble closed). */
        fun endSessionIntent(context: Context): Intent =
            Intent(context, ScreenCropping::class.java).setAction(ACTION_END_SESSION)

        private fun Bitmap.downscaledIfHuge(): Bitmap {
            val longest = max(width, height)
            if (longest <= MAX_IMAGE_EDGE) return this
            val scale = MAX_IMAGE_EDGE.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(
                this,
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== this) recycle()
            return scaled
        }
    }
}
