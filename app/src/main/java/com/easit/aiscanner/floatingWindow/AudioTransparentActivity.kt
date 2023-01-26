package com.easit.aiscanner.floatingWindow

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.speech.RecognizerIntent
import android.text.Editable
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Constants.REQUEST_RECORD_AUDIO
import java.util.*

class AudioTransparentActivity : AppCompatActivity() {
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_transparent)

        //Container close
        val container: ConstraintLayout = findViewById(R.id.container)
        container.setOnClickListener { finish() }

        //AUDIO SPECIFIC CODES
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say something!")

        //Record audio to file
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            Toast.makeText(this, "Record Permission is granted", Toast.LENGTH_SHORT)
                .show()
            //recordAudioOnClick()
        }

        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
                val y = result
                if (result!!.resultCode == RESULT_OK && result.data != null) {
                    val speechText =
                        result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS) as ArrayList<Editable>
                    val intentMain = Intent(this, MainActivity::class.java)
                    val y = speechText
                    intent.putExtra("transcribedText", speechText)
                    startActivity(intentMain)
                }
            }

        try {
            activityResultLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Speech recognition is not available",
                Toast.LENGTH_SHORT
            ).show()
        }
        finish()
    }

}