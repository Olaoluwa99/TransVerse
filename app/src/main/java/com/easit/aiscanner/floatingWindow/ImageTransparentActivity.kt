package com.easit.aiscanner.floatingWindow

import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.easit.aiscanner.R

/**
 * Asks for a fresh MediaProjection consent for one screen capture (Android 14+: one consent
 * token per projection, never reused), hands it to [ScreenCropping] and gets out of the way so
 * the screen underneath can be captured.
 */
class ImageTransparentActivity : AppCompatActivity() {

    private val projectionManager by lazy { getSystemService(MediaProjectionManager::class.java) }

    /** The consent went to the capture service, which now owns the capture. */
    private var handedOff = false

    private val projectionPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultData = result.data
            if (result.resultCode == RESULT_OK && resultData != null) {
                // Re-claim in case the slot was released while this activity was in the
                // background (see onStop), so the bubble hides before the frame is grabbed.
                CaptureCoordinator.beginCapture()
                try {
                    ContextCompat.startForegroundService(
                        this,
                        ScreenCropping.getStartIntent(this, result.resultCode, resultData)
                    )
                    handedOff = true
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Could not start the capture service", e)
                    Toast.makeText(this, R.string.overlay_error_capture_failed, Toast.LENGTH_LONG).show()
                }
                if (handedOff) {
                    // Get out of the way so the app underneath is what gets captured. (Starting
                    // MainActivity just to background it raced this activity's own stop and
                    // crashed with "Can't start activity that is not stopped".)
                    moveTaskToBack(true)
                }
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_transparent)
        CaptureCoordinator.setConsentVisible(true)
        if (savedInstanceState == null) {
            try {
                projectionPermission.launch(consentIntent())
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not ask for screen capture consent", e)
                Toast.makeText(this, R.string.overlay_error_capture_failed, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    /**
     * The capture covers the whole screen the user is looking at, so on Android 14+ ask for the
     * entire display rather than offering a single app.
     */
    private fun consentIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else {
            projectionManager.createScreenCaptureIntent()
        }

    override fun onStop() {
        super.onStop()
        if (isFinishing || handedOff) {
            // The capture service waits for this before grabbing its frame.
            CaptureCoordinator.setConsentVisible(false)
        } else if (!isChangingConfigurations) {
            // Left (e.g. Home) without answering: give the bubble back meanwhile.
            CaptureCoordinator.endCapture()
        }
    }

    override fun onDestroy() {
        CaptureCoordinator.setConsentVisible(false)
        // Refused, cancelled or failed: give the bubble back.
        if (isFinishing && !handedOff) CaptureCoordinator.endCapture()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ImageTransparentActivity"
    }
}
