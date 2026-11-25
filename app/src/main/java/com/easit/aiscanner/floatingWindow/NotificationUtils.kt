package com.easit.aiscanner.floatingWindow

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.easit.aiscanner.R

/** Foreground-service notifications for the floating bubble and the screen capture. */
object NotificationUtils {
    const val BUBBLE_NOTIFICATION_ID = 2002
    const val CAPTURE_NOTIFICATION_ID = 1337

    private const val BUBBLE_CHANNEL_ID = "transverse_quick_capture"
    private const val CAPTURE_CHANNEL_ID = "transverse_screen_capture"
    private const val REQUEST_CLOSE_BUBBLE = 1
    private const val REQUEST_STOP_CAPTURE = 2

    /** What the capture service is doing, which decides the capture notification's wording. */
    enum class CapturePhase {
        /** Grabbing a frame of the screen. */
        CAPTURING,

        /** The editor is open over the frozen screenshot. */
        EDITING,

        /** A capture session keeps the projection open between captures. */
        SESSION,
    }

    fun bubbleNotification(context: Context): Notification {
        ensureChannel(context, BUBBLE_CHANNEL_ID, R.string.overlay_notification_bubble_channel)
        val close = PendingIntent.getService(
            context,
            REQUEST_CLOSE_BUBBLE,
            Intent(context, Floater::class.java).setAction(Floater.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, BUBBLE_CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_baseline_camera_24)
            .setContentTitle(context.getString(R.string.overlay_notification_bubble_title))
            .setContentText(context.getString(R.string.overlay_notification_bubble_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(
                R.drawable.ic_baseline_close_24,
                context.getString(R.string.overlay_notification_close),
                close,
            )
            .build()
    }

    fun captureNotification(context: Context, phase: CapturePhase): Notification {
        ensureChannel(context, CAPTURE_CHANNEL_ID, R.string.overlay_notification_capture_channel)
        val builder = NotificationCompat.Builder(context, CAPTURE_CHANNEL_ID)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_baseline_camera_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
        when (phase) {
            CapturePhase.CAPTURING -> builder
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.overlay_notification_capturing))

            CapturePhase.EDITING -> builder
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.overlay_notification_editing))

            CapturePhase.SESSION -> {
                val stop = PendingIntent.getService(
                    context,
                    REQUEST_STOP_CAPTURE,
                    Intent(context, ScreenCropping::class.java).setAction(ScreenCropping.ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder
                    .setContentTitle(context.getString(R.string.overlay_notification_session_title))
                    .setContentText(context.getString(R.string.overlay_notification_session_text))
                    .addAction(
                        R.drawable.ic_baseline_close_24,
                        context.getString(R.string.overlay_notification_stop_session),
                        stop,
                    )
            }
        }
        return builder.build()
    }

    /** Replaces a posted notification (e.g. a running foreground service's). */
    fun update(context: Context, id: Int, notification: Notification) {
        // Without the notification permission this is silently dropped, which is fine here.
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, notification) }
    }

    private fun ensureChannel(context: Context, id: String, @StringRes name: Int) {
        val channel = NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_LOW)
            .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
