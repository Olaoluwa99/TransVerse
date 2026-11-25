package com.easit.aiscanner.floatingWindow

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.easit.aiscanner.MainActivity

/**
 * Invisible entry point that turns floating capture on from outside the app: the Quick Settings
 * tile ([FloatingCaptureTileService]) and the launcher's long-press shortcut.
 *
 * Starting the bubble from an activity keeps it within Android's foreground-service start rules
 * (a tile or launcher can't start it from the background on Android 15+). With the overlay
 * permission already granted it starts the bubble and closes straight away, leaving the user in
 * the app they were using (it runs in its own task). Otherwise it opens TransVerse, which asks
 * for the permission and then starts the bubble.
 */
class QuickCaptureLauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            if (Settings.canDrawOverlays(this)) {
                startBubble()
            } else {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_START_FLOATING_CAPTURE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
        }
        finish()
    }

    private fun startBubble() {
        if (CaptureCoordinator.bubbleRunning.value) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, Floater::class.java))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not start floating capture", e)
        }
    }

    private companion object {
        const val TAG = "QuickCaptureLauncher"
    }
}
