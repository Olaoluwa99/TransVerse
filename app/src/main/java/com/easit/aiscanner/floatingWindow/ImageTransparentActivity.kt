package com.easit.aiscanner.floatingWindow

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R

/** Requests a fresh projection token for a user-initiated partial screenshot. */
class ImageTransparentActivity : AppCompatActivity() {

    private val projectionManager by lazy { getSystemService(MediaProjectionManager::class.java) }

    private val projectionPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultData = result.data
            if (result.resultCode == RESULT_OK && resultData != null) {
                ContextCompat.startForegroundService(
                    this,
                    ScreenCropping.getStartIntent(this, result.resultCode, resultData)
                )
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_MINIMIZE_APP, true)
                )
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_transparent)
        if (savedInstanceState == null) {
            projectionPermission.launch(projectionManager.createScreenCaptureIntent())
        }
    }
}
