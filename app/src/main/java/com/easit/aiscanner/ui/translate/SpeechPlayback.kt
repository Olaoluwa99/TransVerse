package com.easit.aiscanner.ui.translate

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * Speaks translations: synthesizes text to a wav file with the platform TTS engine, then plays
 * that file with a [MediaPlayer] and reports progress. Owned by a ViewModel so playback survives
 * rotation; call from the main thread and [release] when the owner is cleared.
 */
class SpeechPlayback(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var source: File? = null
    private var player: MediaPlayer? = null
    private var progressJob: Job? = null

    /** Points playback at [file], or at nothing. Stops what was playing. */
    suspend fun load(file: File?) {
        releasePlayer()
        source = file
        val exists = file != null && withContext(Dispatchers.IO) { file.isFile && file.length() > 0 }
        if (source == file) {
            _state.value = PlaybackState(available = exists)
            if (!exists) source = null
        }
    }

    /** Forgets the current file immediately. */
    fun clear() {
        releasePlayer()
        source = null
        _state.value = PlaybackState()
    }

    /**
     * Writes [text] spoken in [languageTag] to [output] and loads it for playback.
     *
     * @param speechRate 1.0 is normal speed
     * @param pitch 1.0 is normal pitch
     * @return false when the engine is unavailable, lacks the language, or fails
     */
    suspend fun synthesize(
        text: String,
        languageTag: String,
        speechRate: Float,
        pitch: Float,
        output: File,
    ): Boolean {
        if (text.isBlank()) return false
        clear()
        _state.update { it.copy(preparing = true) }
        var engine: TextToSpeech? = null
        val written = try {
            withContext(Dispatchers.IO) { output.delete() }
            engine = createEngine() ?: return false
            val languageResult = engine.setLanguage(Locale.forLanguageTag(languageTag))
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                return false
            }
            engine.setSpeechRate(speechRate.coerceAtLeast(MIN_RATE))
            engine.setPitch(pitch.coerceAtLeast(MIN_RATE))
            synthesizeToFile(engine, text, output)
        } finally {
            engine?.stop()
            engine?.shutdown()
            _state.update { it.copy(preparing = false) }
        }
        if (written) load(output)
        return written && _state.value.available
    }

    /** Plays or pauses. Returns false when there is nothing playable. */
    fun togglePlay(): Boolean {
        val current = player
        if (current != null) {
            if (current.isPlaying) {
                current.pause()
                progressJob?.cancel()
                _state.update { it.copy(playing = false, positionMs = current.currentPosition) }
            } else {
                current.start()
                _state.update { it.copy(playing = true) }
                startProgressUpdates()
            }
            return true
        }

        val file = source ?: return false
        return try {
            val created = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                prepare()
                setOnCompletionListener { finished ->
                    progressJob?.cancel()
                    finished.seekTo(0)
                    _state.update { it.copy(playing = false, positionMs = 0) }
                }
                start()
            }
            player = created
            _state.update {
                it.copy(available = true, playing = true, positionMs = 0, durationMs = created.duration)
            }
            startProgressUpdates()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not play ${file.name}", e)
            releasePlayer()
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not play ${file.name}", e)
            releasePlayer()
            false
        }
    }

    fun seekTo(positionMs: Int) {
        val current = player ?: return
        val target = positionMs.coerceIn(0, current.duration.coerceAtLeast(0))
        current.seekTo(target)
        _state.update { it.copy(positionMs = target) }
    }

    fun release() {
        clear()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val current = player ?: break
                _state.update {
                    it.copy(positionMs = current.currentPosition, durationMs = current.duration)
                }
                if (!current.isPlaying) break
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        progressJob = null
        player?.release()
        player = null
        _state.update { it.copy(playing = false, positionMs = 0, durationMs = 0) }
    }

    private suspend fun createEngine(): TextToSpeech? = suspendCancellableCoroutine { continuation ->
        var engine: TextToSpeech? = null
        engine = TextToSpeech(appContext) { status ->
            val ready = engine
            if (status == TextToSpeech.SUCCESS && ready != null) {
                continuation.resume(ready)
            } else {
                ready?.shutdown()
                continuation.resume(null)
            }
        }
        continuation.invokeOnCancellation { engine?.shutdown() }
    }

    private suspend fun synthesizeToFile(engine: TextToSpeech, text: String, output: File): Boolean =
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            fun finish(success: Boolean) {
                if (finished.compareAndSet(false, true)) continuation.resume(success)
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) = finish(true)

                @Deprecated("Deprecated by the platform")
                override fun onError(utteranceId: String?) = finish(false)

                override fun onError(utteranceId: String?, errorCode: Int) = finish(false)
            })
            val result = engine.synthesizeToFile(text, Bundle(), output, UTTERANCE_ID)
            if (result == TextToSpeech.ERROR) finish(false)
        }

    private companion object {
        const val TAG = "SpeechPlayback"
        const val UTTERANCE_ID = "translated_speech"
        const val PROGRESS_INTERVAL_MS = 250L
        const val MIN_RATE = 0.1f
    }
}
