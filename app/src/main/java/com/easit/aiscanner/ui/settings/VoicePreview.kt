package com.easit.aiscanner.ui.settings

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Speaks a sample sentence at the chosen reading speed and pitch. The engine is created on the
 * first [speak] and shut down when the preview leaves the composition.
 */
class VoicePreview internal constructor(
    private val context: Context,
    private val onUnavailable: () -> Unit,
) {
    /** True while the sample is being spoken. */
    var isSpeaking by mutableStateOf(false)
        private set

    private var engine: TextToSpeech? = null
    private var ready = false
    private var initFailed = false
    private var pending: ((TextToSpeech) -> Unit)? = null

    fun speak(text: String, rate: Float, pitch: Float) {
        val utterance: (TextToSpeech) -> Unit = { tts ->
            tts.setSpeechRate(rate)
            tts.setPitch(pitch)
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
            if (result == TextToSpeech.ERROR) {
                isSpeaking = false
                onUnavailable()
            } else {
                isSpeaking = true
            }
        }
        val current = engine
        if (current != null && ready) {
            utterance(current)
            return
        }
        pending = utterance
        if (current == null) {
            initFailed = false
            val created = TextToSpeech(context.applicationContext) { status -> onInit(status) }
            // With no engine installed, Android reports the failure from inside the constructor.
            if (initFailed) created.shutdown() else engine = created
        }
    }

    fun stop() {
        engine?.stop()
        pending = null
        isSpeaking = false
    }

    internal fun release() {
        engine?.stop()
        engine?.shutdown()
        engine = null
        ready = false
        pending = null
        isSpeaking = false
    }

    private fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            initFailed = true
            release()
            onUnavailable()
            return
        }
        val tts = engine ?: return
        ready = true
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                isSpeaking = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
            }
        })
        pending?.invoke(tts)
        pending = null
    }

    private companion object {
        const val UTTERANCE_ID = "settings_voice_preview"
    }
}

/** Remembers a [VoicePreview] that is released when it leaves the composition. */
@Composable
fun rememberVoicePreview(onUnavailable: () -> Unit): VoicePreview {
    val context = LocalContext.current
    val latestOnUnavailable by rememberUpdatedState(onUnavailable)
    val preview = remember(context) { VoicePreview(context) { latestOnUnavailable() } }
    DisposableEffect(preview) {
        onDispose { preview.release() }
    }
    return preview
}
