package com.easit.aiscanner.floatingWindow

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import java.io.IOException

/** Captures one user-selected screen region, stores it privately, then opens analysis. */
class ScreenCropping : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var captureThread: HandlerThread
    private lateinit var captureHandler: Handler
    private val mainHandler = Handler(android.os.Looper.getMainLooper())

    private var selectorOverlay: View? = null
    private var selection: Rect? = null
    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    @Volatile private var captureStarted = false
    private var imageProcessed = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            releaseCaptureResources()
            mainHandler.post {
                if (captureStarted) finishWithError() else stopSelf()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        captureThread = HandlerThread("TransVerseScreenCapture").apply { start() }
        captureHandler = Handler(captureThread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationUtils.getNotification(this)
        startForeground(notification.first, notification.second)

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(RESULT_CODE, Activity.RESULT_CANCELED)
            ?: Activity.RESULT_CANCELED
        val projectionData = intent?.let {
            IntentCompat.getParcelableExtra(it, DATA, Intent::class.java)
        }

        if (intent?.action != ACTION_START ||
            resultCode != Activity.RESULT_OK ||
            projectionData == null
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        showRegionSelector(resultCode, projectionData)
        return START_NOT_STICKY
    }

    private fun showRegionSelector(resultCode: Int, projectionData: Intent) {
        if (selectorOverlay != null) return

        val overlay = LayoutInflater.from(this).inflate(R.layout.service_selector, null)
        val selector = overlay.findViewById<ScreenshotSelectorView>(R.id.global_screenshot_selector)
        val help = overlay.findViewById<TextView>(R.id.help)
        overlay.findViewById<View>(R.id.cancelCapture).setOnClickListener { stopSelf() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        selector.visibility = View.VISIBLE
        selector.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    help.visibility = View.GONE
                    selector.startSelection(event.x.toInt(), event.y.toInt())
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    selector.updateSelection(event.x.toInt(), event.y.toInt())
                    true
                }

                MotionEvent.ACTION_UP -> {
                    selector.performClick()
                    selector.updateSelection(event.x.toInt(), event.y.toInt())
                    val chosenRegion = selector.selectionRect?.let(::Rect)
                    if (chosenRegion == null ||
                        chosenRegion.width() < MIN_SELECTION_SIZE ||
                        chosenRegion.height() < MIN_SELECTION_SIZE
                    ) {
                        selector.stopSelection()
                        help.visibility = View.VISIBLE
                        Toast.makeText(this, R.string.selection_too_small, Toast.LENGTH_SHORT).show()
                    } else {
                        selection = chosenRegion
                        removeSelectorOverlay()
                        mainHandler.postDelayed(
                            { startCapture(resultCode, projectionData) },
                            OVERLAY_SETTLE_DELAY_MS
                        )
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    selector.stopSelection()
                    help.visibility = View.VISIBLE
                    true
                }

                else -> false
            }
        }

        selectorOverlay = overlay
        try {
            windowManager.addView(overlay, params)
        } catch (_: RuntimeException) {
            selectorOverlay = null
            finishWithError()
        }
    }

    private fun startCapture(resultCode: Int, projectionData: Intent) {
        if (captureStarted) return
        captureStarted = true

        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        val projection = projectionManager.getMediaProjection(resultCode, projectionData)
        if (projection == null) {
            finishWithError()
            return
        }
        mediaProjection = projection
        projection.registerCallback(projectionCallback, captureHandler)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ availableReader ->
            captureImage(availableReader, width, height)
        }, captureHandler)

        virtualDisplay = projection.createVirtualDisplay(
            DISPLAY_NAME,
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            captureHandler
        )

        captureHandler.postDelayed({
            if (captureStarted) finishWithError()
        }, CAPTURE_TIMEOUT_MS)
    }

    private fun captureImage(reader: ImageReader, width: Int, height: Int) {
        val image = reader.acquireLatestImage() ?: return
        if (imageProcessed) {
            image.close()
            return
        }
        imageProcessed = true
        try {
            val plane = image.planes.firstOrNull() ?: return
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
            val fullBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            val imageId = System.currentTimeMillis().toString()
            val saved = try {
                fullBitmap.copyPixelsFromBuffer(plane.buffer)
                val region = selection?.clampTo(width, height) ?: return
                val croppedBitmap = Bitmap.createBitmap(
                    fullBitmap,
                    region.left,
                    region.top,
                    region.width(),
                    region.height()
                )
                try {
                    saveImage(imageId, croppedBitmap)
                } finally {
                    croppedBitmap.recycle()
                }
            } finally {
                fullBitmap.recycle()
            }

            if (saved) {
                captureStarted = false
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SCANNED_IMAGE, imageId)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                stopSelf()
            } else {
                finishWithError()
            }
        } catch (_: RuntimeException) {
            finishWithError()
        } finally {
            image.close()
        }
    }

    private fun Rect.clampTo(width: Int, height: Int): Rect {
        val left = this.left.coerceIn(0, width - 1)
        val top = this.top.coerceIn(0, height - 1)
        val right = this.right.coerceIn(left + 1, width)
        val bottom = this.bottom.coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    private fun saveImage(imageId: String, bitmap: Bitmap): Boolean = try {
        openFileOutput("SCAN.$imageId.jpg", MODE_PRIVATE).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
        }
    } catch (_: IOException) {
        false
    }

    private fun finishWithError() {
        captureStarted = false
        mainHandler.post {
            Toast.makeText(this, R.string.screen_capture_failed, Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun removeSelectorOverlay() {
        selectorOverlay?.let { overlay ->
            runCatching { windowManager.removeViewImmediate(overlay) }
        }
        selectorOverlay = null
    }

    private fun releaseCaptureResources() {
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close()
        imageReader = null
        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onDestroy() {
        removeSelectorOverlay()
        releaseCaptureResources()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
        mainHandler.removeCallbacksAndMessages(null)
        captureHandler.removeCallbacksAndMessages(null)
        captureThread.quitSafely()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.easit.aiscanner.action.START_SCREEN_CAPTURE"
        internal const val ACTION_STOP = "com.easit.aiscanner.action.STOP_SCREEN_CAPTURE"
        private const val RESULT_CODE = "result_code"
        private const val DATA = "projection_data"
        private const val DISPLAY_NAME = "TransVerse partial screen capture"
        private const val MIN_SELECTION_SIZE = 24
        private const val JPEG_QUALITY = 92
        private const val OVERLAY_SETTLE_DELAY_MS = 180L
        private const val CAPTURE_TIMEOUT_MS = 10_000L

        fun getStartIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenCropping::class.java)
                .setAction(ACTION_START)
                .putExtra(RESULT_CODE, resultCode)
                .putExtra(DATA, data)
    }
}
