package com.easit.aiscanner.floatingWindow

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.easit.aiscanner.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that turns floating capture on and off from anywhere. It shows "On" while
 * the bubble is running; [Floater] calls [requestUpdate] whenever that changes.
 */
class FloatingCaptureTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listening?.cancel()
        listening = scope.launch {
            CaptureCoordinator.bubbleRunning.collect { running -> render(running) }
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (CaptureCoordinator.bubbleRunning.value) {
            // The bubble's own foreground service is running, so this start is allowed.
            runCatching {
                startService(Intent(this, Floater::class.java).setAction(Floater.ACTION_STOP))
            }.onFailure { Log.w(TAG, "Could not stop floating capture", it) }
            return
        }
        if (isLocked) unlockAndRun { launchBubble() } else launchBubble()
    }

    /** Collapses the shade and starts the bubble through [QuickCaptureLauncherActivity]. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchBubble() {
        val intent = Intent(this, QuickCaptureLauncherActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_floating_capture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(if (running) R.string.tile_state_on else R.string.tile_state_off)
        }
        tile.contentDescription = getString(
            if (running) R.string.tile_floating_capture_on_description
            else R.string.tile_floating_capture_off_description,
        )
        tile.updateTile()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingCaptureTile"

        /** Asks Quick Settings to refresh the tile (e.g. after the bubble starts or stops). */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, FloatingCaptureTileService::class.java))
            }
        }
    }
}
