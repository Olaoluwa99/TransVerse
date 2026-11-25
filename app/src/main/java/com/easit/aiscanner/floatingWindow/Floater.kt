package com.easit.aiscanner.floatingWindow

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.app.ServiceCompat
import com.easit.aiscanner.R
import com.easit.aiscanner.data.AppearanceStore
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.ui.overlay.BubbleBaseSize
import com.easit.aiscanner.ui.overlay.BubbleMenuContent
import com.easit.aiscanner.ui.overlay.BubbleUiState
import com.easit.aiscanner.ui.overlay.BubbleWindowContent
import com.easit.aiscanner.ui.overlay.BubbleWindowPadding
import com.easit.aiscanner.ui.overlay.CloseTargetBottomMargin
import com.easit.aiscanner.ui.overlay.CloseTargetContent
import com.easit.aiscanner.ui.overlay.CloseTargetSlot
import com.easit.aiscanner.ui.overlay.OverlayWindow
import com.easit.aiscanner.ui.overlay.rememberBubbleMenuActions
import com.easit.aiscanner.ui.theme.TransVerseTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The floating quick-capture bubble: a single draggable bubble that docks half-hidden at the
 * screen edge when idle, opens a fan-out menu (Capture area, Speak, Close) on tap, and closes
 * when dropped on the target at the bottom of the screen.
 *
 * Three overlay windows: the small bubble window (moved by changing its x/y), a full-screen menu
 * window that only exists while the menu is open (so touches pass through to the app underneath
 * otherwise), and a full-width, untouchable close-target window at the bottom that is only
 * opaque while dragging.
 */
@AndroidEntryPoint
class Floater : Service() {

    @Inject lateinit var preference: Preference
    @Inject lateinit var appearanceStore: AppearanceStore

    /** Main thread with a frame clock, so position springs can run outside of composition. */
    private val scope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
    private val ui = BubbleUiState()

    private lateinit var windowManager: WindowManager
    private var bubbleWindow: OverlayWindow? = null
    private var menuWindow: OverlayWindow? = null
    private var closeWindow: OverlayWindow? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var closeParams: WindowManager.LayoutParams? = null

    private var geometry = ScreenGeometry(0, 0, 0, 0)

    /** Navigation bar height for the close target's layout; state so rotation updates it. */
    private var closeTargetInset by mutableStateOf(0.dp)
    private var bubbleSizePx = 0
    private var paddingPx = 0
    private var windowSizePx = 0
    private var edgeGapPx = 0
    private var touchSlop = 0

    private var positionJob: Job? = null
    private var dockJob: Job? = null
    private var closeFadeJob: Job? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var dragStartX = 0
    private var dragStartY = 0
    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragging = false
    private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NotificationUtils.BUBBLE_NOTIFICATION_ID,
            NotificationUtils.bubbleNotification(this),
            type,
        )
        CaptureCoordinator.setBubbleRunning(true)
        FloatingCaptureTileService.requestUpdate(this)
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_error_overlay_failed, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        windowManager = getSystemService(WindowManager::class.java)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        measure()
        createWindows()

        // Hide while a capture runs (so the bubble isn't in the screenshot), show again after.
        scope.launch {
            CaptureCoordinator.captureInProgress.collect { inProgress ->
                if (inProgress) hideAll() else showBubble()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (bubbleWindow == null) return
        val onLeft = isOnLeft()
        measure()
        collapseMenuNow()
        cancelDrag()
        closeParams = buildCloseParams().also { closeWindow?.update(it) }
        val params = bubbleParams ?: return
        val x = if (ui.docked) dockX(onLeft) else edgeX(onLeft)
        moveWindow(x, clampY(params.y))
    }

    // region Geometry

    private data class ScreenGeometry(val width: Int, val height: Int, val topInset: Int, val bottomInset: Int)

    private fun measure() {
        val density = resources.displayMetrics.density
        val scale = when (preference.appBubbleSize) {
            Constants.VERY_SMALL -> 0.75f
            Constants.SMALL -> 0.875f
            Constants.LARGE -> 1.15f
            else -> 1f
        }
        bubbleSizePx = (BubbleBaseSize.value * scale * density).roundToInt()
        paddingPx = (BubbleWindowPadding.value * density).roundToInt()
        windowSizePx = bubbleSizePx + paddingPx * 2
        edgeGapPx = (4 * density).roundToInt()
        geometry = readGeometry()
        closeTargetInset = (geometry.bottomInset / density).dp
    }

    private fun readGeometry(): ScreenGeometry {
        val fallbackTop = systemDimension("status_bar_height")
        val fallbackBottom = systemDimension("navigation_bar_height")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            val insets = runCatching {
                windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            }.getOrNull()
            return ScreenGeometry(
                width = bounds.width(),
                height = bounds.height(),
                topInset = insets?.top?.takeIf { it > 0 } ?: fallbackTop,
                bottomInset = insets?.bottom ?: fallbackBottom,
            )
        }
        val size = Point()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealSize(size)
        return ScreenGeometry(size.x, size.y, fallbackTop, fallbackBottom)
    }

    private fun systemDimension(name: String): Int {
        @Suppress("DiscouragedApi", "InternalInsetResource")
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    /** Bubble fully visible, a few pixels from the edge. */
    private fun edgeX(onLeft: Boolean): Int =
        if (onLeft) -paddingPx + edgeGapPx else geometry.width - windowSizePx + paddingPx - edgeGapPx

    /** Bubble parked with about 40% of it peeking in from the edge. */
    private fun dockX(onLeft: Boolean): Int =
        if (onLeft) {
            -paddingPx - (bubbleSizePx * DOCK_HIDDEN_FRACTION).roundToInt()
        } else {
            geometry.width - paddingPx - (bubbleSizePx * (1 - DOCK_HIDDEN_FRACTION)).roundToInt()
        }

    /** Keeps the bubble below the status bar and above the navigation bar. */
    private fun clampY(y: Int): Int {
        val min = geometry.topInset - paddingPx + edgeGapPx
        val max = (geometry.height - geometry.bottomInset - windowSizePx + paddingPx - edgeGapPx).coerceAtLeast(min)
        return y.coerceIn(min, max)
    }

    private fun isOnLeft(): Boolean {
        val params = bubbleParams ?: return false
        return params.x + windowSizePx / 2 < geometry.width / 2
    }

    private fun closeTargetCenter(): Offset {
        val density = resources.displayMetrics.density
        val slot = CloseTargetSlot.value * density
        val margin = CloseTargetBottomMargin.value * density
        return Offset(geometry.width / 2f, geometry.height - geometry.bottomInset - margin - slot / 2f)
    }

    // endregion

    // region Windows

    private fun createWindows() {
        val density = resources.displayMetrics.density
        val bubbleSizeDp = (bubbleSizePx / density).dp

        // Added first so it stays below the bubble.
        closeWindow = OverlayWindow(this).apply {
            setContent {
                val appearance by appearanceStore.settings.collectAsState()
                TransVerseTheme(settings = appearance) {
                    CloseTargetContent(
                        visible = ui.dragging,
                        active = ui.overCloseTarget,
                        bottomInset = closeTargetInset,
                    )
                }
            }
        }
        closeParams = buildCloseParams()
        closeParams?.let { closeWindow?.show(it) }

        bubbleWindow = OverlayWindow(this).apply {
            setContent {
                val appearance by appearanceStore.settings.collectAsState()
                TransVerseTheme(settings = appearance) {
                    BubbleWindowContent(
                        state = ui,
                        bubbleSize = bubbleSizeDp,
                        onTouch = ::onBubbleTouch,
                        onAccessibilityClick = ::toggleMenu,
                    )
                }
            }
        }
        val onLeft = preference.bubbleX.let { it >= 0 && it + windowSizePx / 2 < geometry.width / 2 }
        val startY = preference.bubbleY.takeIf { it >= 0 } ?: (geometry.height / 3)
        bubbleParams = OverlayWindow.floatingParams(edgeX(onLeft), clampY(startY)).apply {
            width = windowSizePx
            height = windowSizePx
        }
        ui.bubbleWindowPosition = bubbleParams.position()

        menuWindow = OverlayWindow(this).apply {
            setContent {
                val appearance by appearanceStore.settings.collectAsState()
                TransVerseTheme(settings = appearance) {
                    BubbleMenuContent(
                        state = ui,
                        bubbleSize = bubbleSizeDp,
                        // Deferred: these remove this window, which is still handling the click.
                        actions = rememberBubbleMenuActions(
                            onCapture = { scope.launch { startCapture() } },
                            onSpeak = { scope.launch { startSpeech() } },
                            onClose = { scope.launch { stopSelf() } },
                        ),
                        onDismiss = ::collapseMenu,
                        onCollapsed = ::onMenuCollapsed,
                    )
                }
            }
        }
    }

    /** Full width at the bottom; never touchable and invisible (alpha 0) unless dragging. */
    private fun buildCloseParams(): WindowManager.LayoutParams {
        val height = (CLOSE_AREA_DP * resources.displayMetrics.density).roundToInt() + geometry.bottomInset
        return OverlayWindow.floatingParams(0, geometry.height - height).apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            this.height = height
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // Fully transparent untouchable overlays don't block touches to the app underneath.
            alpha = if (ui.dragging) 1f else 0f
        }
    }

    private fun setCloseWindowOpaque(opaque: Boolean) {
        closeFadeJob?.cancel()
        val params = closeParams ?: return
        params.alpha = if (opaque) 1f else 0f
        closeWindow?.update(params)
    }

    private fun showBubble() {
        val window = bubbleWindow ?: return
        val params = bubbleParams ?: return
        if (window.isShowing) return
        if (!window.show(params)) {
            Log.w(TAG, "The bubble window was refused; stopping")
            Toast.makeText(this, R.string.overlay_error_overlay_failed, Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }
        scheduleDock()
    }

    private fun hideAll() {
        positionJob?.cancel()
        dockJob?.cancel()
        cancelDrag()
        collapseMenuNow()
        ui.pressed = false
        bubbleWindow?.hide()
    }

    private fun moveWindow(x: Int, y: Int) {
        val params = bubbleParams ?: return
        params.x = x
        params.y = y
        bubbleWindow?.update(params)
        ui.bubbleWindowPosition = IntOffset(x, y)
    }

    private fun animateWindowTo(x: Int, y: Int, onEnd: () -> Unit = {}) {
        val params = bubbleParams ?: return
        positionJob?.cancel()
        val start = Offset(params.x.toFloat(), params.y.toFloat())
        positionJob = scope.launch {
            val animatable = Animatable(start, Offset.VectorConverter)
            animatable.animateTo(
                targetValue = Offset(x.toFloat(), y.toFloat()),
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
            ) {
                moveWindow(value.x.roundToInt(), value.y.roundToInt())
            }
            onEnd()
        }
    }

    // endregion

    // region Touch, drag and docking

    private fun onBubbleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dockJob?.cancel()
                downRawX = event.rawX
                downRawY = event.rawY
                dragging = false
                ui.pressed = true
                if (ui.docked) {
                    // Fully visible while touched.
                    ui.docked = false
                    bubbleParams?.let { animateWindowTo(edgeX(isOnLeft()), it.y) }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    if (hypot(event.rawX - downRawX, event.rawY - downRawY) < touchSlop) return true
                    startDrag(event)
                }
                updateDrag(
                    dragStartX + (event.rawX - dragStartRawX).roundToInt(),
                    dragStartY + (event.rawY - dragStartRawY).roundToInt(),
                )
            }

            MotionEvent.ACTION_UP -> {
                ui.pressed = false
                if (dragging) endDrag() else toggleMenu()
            }

            MotionEvent.ACTION_CANCEL -> {
                ui.pressed = false
                if (dragging) endDrag() else scheduleDock()
            }
        }
        return true
    }

    private fun startDrag(event: MotionEvent) {
        val params = bubbleParams ?: return
        dragging = true
        positionJob?.cancel()
        collapseMenuNow()
        dragStartX = params.x
        dragStartY = params.y
        dragStartRawX = event.rawX
        dragStartRawY = event.rawY
        ui.docked = false
        ui.dragging = true
        setCloseWindowOpaque(true)
    }

    private fun updateDrag(x: Int, y: Int) {
        val target = closeTargetCenter()
        val centerX = x + windowSizePx / 2f
        val centerY = y + windowSizePx / 2f
        val over = hypot(centerX - target.x, centerY - target.y) <
            CLOSE_HIT_RADIUS_DP * resources.displayMetrics.density
        ui.overCloseTarget = over
        if (over) {
            // Magnet: centre the bubble on the target.
            moveWindow((target.x - windowSizePx / 2f).roundToInt(), (target.y - windowSizePx / 2f).roundToInt())
        } else {
            val maxX = geometry.width - windowSizePx + paddingPx
            val maxY = geometry.height - windowSizePx + paddingPx
            moveWindow(x.coerceIn(-paddingPx, maxX), y.coerceIn(-paddingPx, maxY))
        }
    }

    private fun endDrag() {
        val dropOnClose = ui.overCloseTarget
        dragging = false
        ui.dragging = false
        ui.overCloseTarget = false
        fadeOutCloseWindow()
        if (dropOnClose) {
            // Deferred: the bubble window is still dispatching this touch.
            scope.launch { stopSelf() }
            return
        }
        snapToEdge()
    }

    /** Ends a drag without snapping (the bubble is being hidden or re-laid out). */
    private fun cancelDrag() {
        if (!dragging && !ui.dragging) return
        dragging = false
        ui.dragging = false
        ui.overCloseTarget = false
        setCloseWindowOpaque(false)
    }

    private fun fadeOutCloseWindow() {
        closeFadeJob?.cancel()
        closeFadeJob = scope.launch {
            // Let the target animate out before making the window transparent again.
            delay(CLOSE_FADE_MS)
            closeParams?.let {
                it.alpha = 0f
                closeWindow?.update(it)
            }
        }
    }

    private fun snapToEdge() {
        val params = bubbleParams ?: return
        val onLeft = isOnLeft()
        val x = edgeX(onLeft)
        val y = clampY(params.y)
        animateWindowTo(x, y) {
            // The left edge position is slightly negative (shadow margin); -1 means "never
            // moved", so store non-negative values. Only the side matters for x on restore.
            preference.bubbleX = x.coerceAtLeast(0)
            preference.bubbleY = y.coerceAtLeast(0)
            scheduleDock()
        }
    }

    private fun scheduleDock() {
        dockJob?.cancel()
        dockJob = scope.launch {
            delay(DOCK_DELAY_MS)
            if (dragging || ui.menuExpanded || ui.pressed || CaptureCoordinator.captureInProgress.value) return@launch
            val params = bubbleParams ?: return@launch
            ui.docked = true
            animateWindowTo(dockX(isOnLeft()), params.y)
        }
    }

    // endregion

    // region Menu and actions

    private fun toggleMenu() {
        if (ui.menuExpanded) collapseMenu() else expandMenu()
    }

    private fun expandMenu() {
        val params = bubbleParams ?: return
        dockJob?.cancel()
        positionJob?.cancel()
        if (ui.docked) {
            ui.docked = false
            moveWindow(edgeX(isOnLeft()), params.y)
        }
        ui.bubbleWindowPosition = params.position()
        ui.menuExpanded = true
        if (menuWindow?.show(OverlayWindow.fullScreenParams(focusable = false)) != true) {
            ui.menuExpanded = false
        }
    }

    /** Animated collapse; the window goes away in [onMenuCollapsed]. */
    private fun collapseMenu() {
        if (!ui.menuExpanded) return
        ui.menuExpanded = false
    }

    private fun onMenuCollapsed() {
        if (ui.menuExpanded) return
        menuWindow?.hide()
        if (bubbleWindow?.isShowing == true) scheduleDock()
    }

    private fun collapseMenuNow() {
        ui.menuExpanded = false
        menuWindow?.hide()
    }

    private fun startCapture() {
        collapseMenuNow()
        if (!CaptureCoordinator.beginCapture()) return
        if (CaptureCoordinator.sessionActive.value) {
            // A session is live: no consent needed. Hide first so the bubble isn't captured.
            hideAll()
            try {
                startService(ScreenCropping.captureIntent(this))
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not reach the capture session", e)
                Toast.makeText(this, R.string.overlay_error_launch_failed, Toast.LENGTH_LONG).show()
                CaptureCoordinator.endCapture()
            }
        } else {
            // Start the activity while the bubble is still visible: Android 15+ only lets an
            // app with the overlay permission start activities while one of its overlays shows.
            try {
                startActivity(
                    Intent(this, ImageTransparentActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not ask for screen capture consent", e)
                Toast.makeText(this, R.string.overlay_error_launch_failed, Toast.LENGTH_LONG).show()
                CaptureCoordinator.endCapture()
                return
            }
            hideAll()
        }
    }

    private fun startSpeech() {
        collapseMenuNow()
        try {
            startActivity(
                Intent(this, AudioTransparentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start speech capture", e)
            Toast.makeText(this, R.string.overlay_error_launch_failed, Toast.LENGTH_LONG).show()
        }
        scheduleDock()
    }

    // endregion

    override fun onDestroy() {
        if (!stopping) {
            stopping = true
            scope.cancel()
            menuWindow?.destroy()
            bubbleWindow?.destroy()
            closeWindow?.destroy()
            menuWindow = null
            bubbleWindow = null
            closeWindow = null
            CaptureCoordinator.setBubbleRunning(false)
            FloatingCaptureTileService.requestUpdate(this)
            // A capture session only lives alongside the bubble.
            if (CaptureCoordinator.sessionActive.value) {
                runCatching { startService(ScreenCropping.endSessionIntent(this)) }
                    .onFailure { Log.w(TAG, "Could not end the capture session", it) }
            }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun WindowManager.LayoutParams?.position(): IntOffset =
        if (this == null) IntOffset.Zero else IntOffset(x, y)

    companion object {
        private const val TAG = "Floater"
        const val ACTION_STOP = "com.easit.aiscanner.action.STOP_QUICK_CAPTURE"
        private const val DOCK_DELAY_MS = 3_000L
        private const val CLOSE_FADE_MS = 250L
        private const val DOCK_HIDDEN_FRACTION = 0.6f
        private const val CLOSE_AREA_DP = 200
        private const val CLOSE_HIT_RADIUS_DP = 72
    }
}
