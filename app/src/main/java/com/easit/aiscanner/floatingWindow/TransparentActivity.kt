package com.easit.aiscanner.floatingWindow

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import com.easit.aiscanner.R

class TransparentActivity : AppCompatActivity() {
    private val REQUEST_CODE = 1001
    var DeX = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent)

        //Unknown action: To remove later
        val config = resources.configuration
        try {
            val configClass: Class<*> = config.javaClass
            if (configClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(configClass) ==
                configClass.getField("semDesktopModeEnabled").getInt(config)
            ) {
                DeX = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        startProjection()
        val container: ConstraintLayout = findViewById(R.id.container)
        container.setOnClickListener { finish() }
    }

    private fun startProjection() {
        val mProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    private fun startProjectionNew() {
        val mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)
        var mediaProjection : MediaProjection

        val startMediaProjection = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result ->
            if (result.resultCode == RESULT_OK){
                mediaProjection = mediaProjectionManager.getMediaProjection(
                    result.resultCode, result.data!!
                )!!
                startService(FloatingWindow.getStartIntent(this, result.resultCode, result.data!!))
            }
        }
        startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                //val i = FloatingWindow.getStartIntent(this, resultCode, data)
                startService(FloatingWindow.getStartIntent(this, resultCode, data))
                finish()
            }
        }
    }
}