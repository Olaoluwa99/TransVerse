package com.easit.aiscanner.floatingWindow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.databinding.ServiceFloatingButtonsBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs

/** A user-controlled overlay that exposes image and speech capture shortcuts. */
@AndroidEntryPoint
class Floater : Service() {

    @Inject lateinit var preference: Preference

    private lateinit var binding: ServiceFloatingButtonsBinding
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var overlayAttached = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    private fun showOverlay() {
        binding = ServiceFloatingButtonsBinding.inflate(LayoutInflater.from(this))
        val bubbleScale = when (preference.appBubbleSize) {
            Constants.VERY_SMALL -> 0.7f
            Constants.SMALL -> 0.85f
            Constants.LARGE -> 1.15f
            else -> 1f
        }
        binding.root.scaleX = bubbleScale
        binding.root.scaleY = bubbleScale
        binding.root.pivotX = 0f
        binding.root.pivotY = 0f
        windowManager = getSystemService(WindowManager::class.java)
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = resources.displayMetrics.heightPixels / 3
        }

        try {
            windowManager.addView(binding.root, layoutParams)
        } catch (_: RuntimeException) {
            stopSelf()
            return
        }
        overlayAttached = true
        configureDragGesture()

        binding.close.setOnClickListener { stopSelf() }
        binding.capture.setOnClickListener {
            val showActions = !binding.imageText.isVisible
            binding.imageText.isVisible = showActions
            binding.audioText.isVisible = showActions
        }
        binding.imageText.setOnClickListener {
            launchCaptureActivity(ImageTransparentActivity::class.java)
        }
        binding.audioText.setOnClickListener {
            launchCaptureActivity(AudioTransparentActivity::class.java)
        }
    }

    private fun configureDragGesture() {
        binding.root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        touchX = event.rawX
                        touchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - touchX).toInt()
                        layoutParams.y = clampedY(initialY + (event.rawY - touchY).toInt())
                        updateOverlayPosition()
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        val screenWidth = resources.displayMetrics.widthPixels
                        layoutParams.x = if (event.rawX < screenWidth / 2f) {
                            0
                        } else {
                            (screenWidth - binding.root.width).coerceAtLeast(0)
                        }
                        layoutParams.y = clampedY(
                            initialY + (event.rawY - touchY).toInt()
                        )
                        updateOverlayPosition()

                        if (abs(event.rawX - touchX) < CLICK_SLOP &&
                            abs(event.rawY - touchY) < CLICK_SLOP
                        ) {
                            view.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateOverlayPosition() {
        if (overlayAttached) {
            windowManager.updateViewLayout(binding.root, layoutParams)
        }
    }

    private fun clampedY(candidate: Int): Int {
        val maximum = (resources.displayMetrics.heightPixels - binding.root.height).coerceAtLeast(0)
        return candidate.coerceIn(0, maximum)
    }

    private fun launchCaptureActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        stopSelf()
    }

    private fun createNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.floating_service_running),
                NotificationManager.IMPORTANCE_LOW
            ).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_baseline_camera_24)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_service_running))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        if (overlayAttached) {
            runCatching { windowManager.removeViewImmediate(binding.root) }
            overlayAttached = false
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "transverse_quick_capture"
        private const val NOTIFICATION_ID = 2002
        private const val CLICK_SLOP = 20f
    }
}
