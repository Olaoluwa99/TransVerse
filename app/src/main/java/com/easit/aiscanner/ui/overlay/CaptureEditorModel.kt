package com.easit.aiscanner.ui.overlay

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntRect
import com.easit.aiscanner.MainActivity
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.scannerAI.AnalysisException
import com.easit.aiscanner.scannerAI.AnalysisFailure
import com.easit.aiscanner.scannerAI.AnalysisOutput
import com.easit.aiscanner.scannerAI.AnalysisPipeline
import com.easit.aiscanner.scannerAI.AnalysisStatus
import com.easit.aiscanner.scannerAI.MlKitModules
import com.easit.aiscanner.ui.crop.CropEditorState
import com.easit.aiscanner.ui.crop.CropMode
import com.easit.aiscanner.ui.crop.cropBitmap
import com.easit.aiscanner.ui.crop.recognizeTextBlocks
import com.easit.aiscanner.ui.translate.RecentLanguages
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A translation target. */
@Immutable
data class OverlayLanguage(val code: String, val name: String)

/** Progress of the text recognition that runs as soon as the editor opens. */
enum class OcrPhase { RUNNING, FOUND, NONE, FAILED }

/** A longer action the editor is busy with (its button shows progress). */
enum class EditorBusy { READING, TRANSLATING, COPYING, OPENING }

@Immutable
data class OverlayTranslation(
    val sourceLanguageName: String,
    val targetLanguageName: String,
    val translatedText: String,
)

/** A one-off message; shown once per [id]. */
@Immutable
data class OverlayMessage(@StringRes val text: Int, val id: Long)

@Immutable
data class CaptureEditorUiState(
    val ocr: OcrPhase = OcrPhase.RUNNING,
    /** Briefly true after text was found, to hint that blocks can be tapped. */
    val showTapHint: Boolean = false,
    /** Play services is downloading the text recognition module. */
    val preparingOcr: Boolean = false,
    /** Module download progress (OCR or language identification), or null if unknown. */
    val setupProgress: Float? = null,
    val busy: EditorBusy? = null,
    val status: AnalysisStatus = AnalysisStatus.IDLE,
    val target: OverlayLanguage = OverlayLanguage("en", "English"),
    val recent: List<OverlayLanguage> = emptyList(),
    val pickerOpen: Boolean = false,
    val result: OverlayTranslation? = null,
    val showOnImage: Boolean = false,
    val translatingBlocks: Boolean = false,
    /** Parallel to the editor's text blocks while "show on image" is on. */
    val blockTranslations: List<String>? = null,
    val speaking: Boolean = false,
    val message: OverlayMessage? = null,
)

/**
 * Logic behind the capture editor shown over a frozen screenshot: OCR, translation, history,
 * clipboard, read-aloud, sharing and handing over to the app. Owned by the capture service, which
 * shows the editor in an overlay window; [release] when the window goes away.
 *
 * Call from the main thread.
 */
class CaptureEditorModel(
    private val context: Context,
    /** The frozen screenshot; recycled by [release]. */
    val bitmap: Bitmap,
    private val modules: MlKitModules,
    private val repository: ScanRepository,
    private val preference: Preference,
    private val recentLanguages: RecentLanguages,
    private val onClose: () -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val image: ImageBitmap = bitmap.asImageBitmap()

    private val initialCrop = defaultCrop(bitmap.width, bitmap.height)
    val cropState = CropEditorState(bitmap.width, bitmap.height, initialCrop)

    val languages: List<OverlayLanguage> = TranslateLanguage.getAllLanguages()
        .map(::languageOf)
        .sortedBy { it.name }

    private val _state = MutableStateFlow(
        CaptureEditorUiState(
            target = languageOf(recentLanguages.currentTarget),
            recent = recentLanguages.recentTargets.map(::languageOf),
        )
    )
    val state: StateFlow<CaptureEditorUiState> = _state.asStateFlow()

    private var recognizer: TextRecognizer? = null
    private val pipeline = AnalysisPipeline(modules)
    private var translateJob: Job? = null
    private var blocksJob: Job? = null
    private var blocksWatchJob: Job? = null
    private var saved: SavedCapture? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: Pair<String, String>? = null
    private var released = false

    private data class SavedCapture(val id: String, val fileName: String, val rect: IntRect, val rotation: Int)

    /** Starts text recognition on the whole screenshot. */
    fun start() {
        scope.launch { recognizeScreen() }
    }

    private suspend fun recognizeScreen() {
        val recognizer = recognizer()
        try {
            modules.ensureInstalled(listOf<OptionalModuleApi>(recognizer)) { progress ->
                _state.update { it.copy(preparingOcr = true, setupProgress = progress) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // recognizeTextBlocks waits for the module itself; carry on.
            Log.w(TAG, "Text recognition module check failed", e)
        }
        _state.update { it.copy(preparingOcr = false, setupProgress = null) }

        val blocks = try {
            recognizeTextBlocks(bitmap, recognizer, modules)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Text recognition failed", e)
            _state.update { it.copy(ocr = OcrPhase.FAILED) }
            return
        }
        cropState.textBlocks = blocks
        if (blocks.isEmpty()) {
            _state.update { it.copy(ocr = OcrPhase.NONE) }
            return
        }
        // Tapping text is the quickest way to pick it, so switch to it, unless the user has
        // already started framing a region.
        val untouched = cropState.mode == CropMode.REGION &&
            cropState.cropRect == initialCrop &&
            cropState.rotationDegrees == 0
        if (untouched) cropState.mode = CropMode.TEXT
        _state.update { it.copy(ocr = OcrPhase.FOUND, showTapHint = untouched) }
        if (untouched) {
            delay(TAP_HINT_MS)
            _state.update { it.copy(showTapHint = false) }
        }
    }

    // region Actions

    fun translate() {
        if (translateJob?.isActive == true) return
        translateJob = scope.launch {
            _state.update { it.copy(busy = EditorBusy.READING, status = AnalysisStatus.IDLE) }
            try {
                val text = selectionText()
                if (text.isBlank()) {
                    show(R.string.overlay_message_no_text)
                    return@launch
                }
                val target = _state.value.target
                _state.update { it.copy(busy = EditorBusy.TRANSLATING) }
                val output = pipeline.analyze(
                    text = text,
                    targetLanguageCode = target.code,
                    onStatus = { status -> _state.update { it.copy(status = status) } },
                    onLanguageDetected = { _, _ -> },
                    onSetupProgress = { progress -> _state.update { it.copy(setupProgress = progress) } },
                )
                _state.update {
                    it.copy(
                        result = OverlayTranslation(
                            sourceLanguageName = output.sourceLanguageName,
                            targetLanguageName = target.name,
                            translatedText = output.translatedText,
                        ),
                    )
                }
                if (!preference.appIncognitoMode) saveToHistory(output, target)
                if (_state.value.showOnImage) refreshBlockTranslations()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnalysisException) {
                Log.w(TAG, "Translation failed: ${e.failure}", e)
                show(messageFor(e.failure))
            } catch (e: Exception) {
                Log.w(TAG, "Translation failed", e)
                show(R.string.overlay_message_translation_failed)
            } finally {
                _state.update { it.copy(busy = null, status = AnalysisStatus.IDLE, setupProgress = null) }
            }
        }
    }

    fun copyText() {
        if (_state.value.busy != null) return
        scope.launch {
            _state.update { it.copy(busy = EditorBusy.COPYING) }
            try {
                val text = selectionText()
                if (text.isBlank()) show(R.string.overlay_message_no_text) else copyToClipboard(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the selection", e)
                show(R.string.overlay_message_no_text)
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    fun copyTranslation() {
        _state.value.result?.let { copyToClipboard(it.translatedText) }
    }

    /** Saves the selection as a screen capture and opens it on the Translate screen. */
    fun openInApp() {
        if (_state.value.busy == EditorBusy.OPENING) return
        scope.launch {
            _state.update { it.copy(busy = EditorBusy.OPENING) }
            try {
                val capture = ensureSavedImage()
                if (capture == null) {
                    show(R.string.overlay_message_save_failed)
                    return@launch
                }
                // Started while the overlay is still visible: Android 15+ only allows the
                // background launch while the app shows an overlay window.
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_SCANNED_IMAGE, capture.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                onClose()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                Log.w(TAG, "Could not open the app", e)
                show(R.string.overlay_message_open_failed)
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    fun share() {
        val text = _state.value.result?.translatedText ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, context.getString(R.string.overlay_share_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            // The share sheet would open underneath this full-screen overlay, so close it.
            context.startActivity(chooser)
            onClose()
        } catch (e: ActivityNotFoundException) {
            show(R.string.overlay_message_share_failed)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not share", e)
            show(R.string.overlay_message_share_failed)
        }
    }

    fun toggleSpeech() {
        if (_state.value.speaking) {
            tts?.stop()
            pendingSpeech = null
            _state.update { it.copy(speaking = false) }
            return
        }
        val result = _state.value.result ?: return
        speak(result.translatedText, _state.value.target.code)
    }

    fun setShowOnImage(show: Boolean) {
        _state.update { it.copy(showOnImage = show, blockTranslations = if (show) it.blockTranslations else null) }
        blocksWatchJob?.cancel()
        blocksJob?.cancel()
        if (!show) {
            _state.update { it.copy(translatingBlocks = false) }
            return
        }
        refreshBlockTranslations()
        // Follow selection changes while it's on.
        blocksWatchJob = scope.launch {
            snapshotFlow { cropState.coveredBlocks }
                .drop(1)
                .collectLatest {
                    delay(SELECTION_SETTLE_MS)
                    refreshBlockTranslations()
                }
        }
    }

    fun openPicker() {
        _state.update { it.copy(pickerOpen = true) }
    }

    fun closePicker() {
        _state.update { it.copy(pickerOpen = false) }
    }

    fun selectLanguage(language: OverlayLanguage) {
        recentLanguages.useTarget(language.code)
        _state.update {
            it.copy(
                target = language,
                recent = recentLanguages.recentTargets.map(::languageOf),
                pickerOpen = false,
            )
        }
        if (_state.value.result != null) {
            stopSpeech()
            translateJob?.cancel()
            translate()
        }
    }

    fun dismissResult() {
        stopSpeech()
        blocksWatchJob?.cancel()
        blocksJob?.cancel()
        _state.update {
            it.copy(result = null, showOnImage = false, blockTranslations = null, translatingBlocks = false)
        }
    }

    /** Back: picker → result → editor. */
    fun onBack() {
        val current = _state.value
        when {
            current.pickerOpen -> closePicker()
            current.result != null -> dismissResult()
            else -> close()
        }
    }

    fun close() {
        onClose()
    }

    /** Stops all work and frees the screenshot. Call after the editor window is gone. */
    fun release() {
        if (released) return
        released = true
        scope.cancel()
        runCatching { pipeline.close() }
        recognizer?.let { runCatching { it.close() } }
        recognizer = null
        tts?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        tts = null
        bitmap.recycle()
    }

    // endregion

    // region Helpers

    private fun recognizer(): TextRecognizer =
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }

    /**
     * The text to work on: the selected blocks' text, or, when the selection covers no detected
     * block, OCR of the cropped selection. In TEXT mode with nothing tapped, everything.
     */
    private suspend fun selectionText(): String {
        if (cropState.mode == CropMode.TEXT && cropState.selectedBlocks.isEmpty() && cropState.textBlocks.isNotEmpty()) {
            cropState.selectAllBlocks()
        }
        cropState.selectedText.takeIf { it.isNotBlank() }?.let { return it }
        val cropped = croppedSelection()
        return try {
            recognizeTextBlocks(cropped, recognizer(), modules)
                .joinToString("\n\n") { it.text.trim() }
                .trim()
        } finally {
            if (cropped !== bitmap) cropped.recycle()
        }
    }

    private suspend fun croppedSelection(): Bitmap {
        val rect = cropState.outputRect
        val rotation = cropState.rotationDegrees
        return withContext(Dispatchers.Default) { cropBitmap(bitmap, rect, rotation) }
    }

    /** Writes the current selection to `SCAN.<id>.jpg` once per selection; null on failure. */
    private suspend fun ensureSavedImage(): SavedCapture? {
        val rect = cropState.outputRect
        val rotation = cropState.rotationDegrees
        saved?.let { if (it.rect == rect && it.rotation == rotation) return it }
        val id = ScanIds.next()
        val fileName = "SCAN.$id.jpg"
        val cropped = croppedSelection()
        val written = try {
            withContext(Dispatchers.IO) {
                context.openFileOutput(fileName, Context.MODE_PRIVATE).use { stream ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the capture", e)
            false
        } finally {
            if (cropped !== bitmap) cropped.recycle()
        }
        if (!written) return null
        return SavedCapture(id, fileName, rect, rotation).also { saved = it }
    }

    /** One history entry per saved selection; re-translating it updates the same entry. */
    private suspend fun saveToHistory(output: AnalysisOutput, target: OverlayLanguage) {
        val capture = ensureSavedImage() ?: run {
            show(R.string.overlay_message_save_failed)
            return
        }
        val scan = Scan(
            id = capture.id,
            dateTime = DATE_FORMATTER.format(LocalDateTime.now()),
            timeMilliseconds = System.currentTimeMillis(),
            scanType = SCAN_TYPE_IMAGE,
            transcriptText = output.sourceText,
            translatedText = output.translatedText,
            sourceLanguage = output.sourceLanguageName,
            translatedLanguage = target.name,
            entities = output.entities,
            smartReplies = output.smartReplies,
            imageUrl = capture.fileName,
            audioUrl = "",
            barcodeScan = "",
        )
        try {
            withContext(Dispatchers.IO) { repository.insert(scan) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save to history", e)
            show(R.string.overlay_message_save_failed)
        }
    }

    private fun refreshBlockTranslations() {
        blocksJob?.cancel()
        blocksJob = scope.launch {
            val covered = cropState.coveredBlocks
            if (covered.isEmpty()) {
                _state.update { it.copy(blockTranslations = null) }
                show(R.string.overlay_message_nothing_on_image)
                return@launch
            }
            _state.update { it.copy(translatingBlocks = true) }
            try {
                val translated = pipeline.translateBlocks(
                    texts = covered.map { it.text },
                    targetLanguageCode = _state.value.target.code,
                )
                val all = cropState.textBlocks
                val overlay = MutableList(all.size) { "" }
                covered.forEachIndexed { coveredIndex, block ->
                    val index = all.indexOf(block)
                    if (index >= 0) overlay[index] = translated.translations.getOrElse(coveredIndex) { "" }
                }
                _state.update { it.copy(blockTranslations = overlay) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnalysisException) {
                Log.w(TAG, "Block translation failed: ${e.failure}", e)
                show(messageFor(e.failure))
            } catch (e: Exception) {
                Log.w(TAG, "Block translation failed", e)
                show(R.string.overlay_message_translation_failed)
            } finally {
                _state.update { it.copy(translatingBlocks = false) }
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.overlay_clip_label), text))
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not copy", e)
            return
        }
        // Android 13+ confirms copies itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) show(R.string.overlay_message_copied)
    }

    private fun speak(text: String, languageCode: String) {
        val engine = tts
        if (engine == null || !ttsReady) {
            pendingSpeech = text to languageCode
            if (engine == null) {
                tts = TextToSpeech(context) { status ->
                    scope.launch {
                        if (status == TextToSpeech.SUCCESS) {
                            ttsReady = true
                            pendingSpeech?.let { (pendingText, code) -> speakNow(pendingText, code) }
                        } else {
                            show(R.string.overlay_message_speech_unavailable)
                        }
                        pendingSpeech = null
                    }
                }
            }
            return
        }
        speakNow(text, languageCode)
    }

    private fun speakNow(text: String, languageCode: String) {
        val engine = tts ?: return
        val availability = engine.setLanguage(Locale.forLanguageTag(languageCode))
        if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
            show(R.string.overlay_message_speech_unavailable)
            return
        }
        engine.setSpeechRate(preference.appReadingSpeed / AUDIO_SETTING_SCALE)
        engine.setPitch(preference.appSpeechPitch / AUDIO_SETTING_SCALE)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                scope.launch { _state.update { it.copy(speaking = false) } }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                scope.launch { _state.update { it.copy(speaking = false) } }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                scope.launch { _state.update { it.copy(speaking = false) } }
            }
        })
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.SUCCESS) {
            _state.update { it.copy(speaking = true) }
        } else {
            show(R.string.overlay_message_speech_unavailable)
        }
    }

    private fun stopSpeech() {
        pendingSpeech = null
        if (_state.value.speaking) {
            tts?.stop()
            _state.update { it.copy(speaking = false) }
        }
    }

    private fun show(@StringRes message: Int) {
        _state.update { it.copy(message = OverlayMessage(message, System.nanoTime())) }
    }

    // endregion

    companion object {
        private const val TAG = "CaptureEditorModel"
        private const val TAP_HINT_MS = 3_500L
        private const val SELECTION_SETTLE_MS = 400L
        private const val JPEG_QUALITY = 92
        private const val SCAN_TYPE_IMAGE = "image"
        private const val UTTERANCE_ID = "transverse_overlay"

        /** Reading speed and pitch are stored as 20..100 where 50 is normal. */
        private const val AUDIO_SETTING_SCALE = 50f
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")

        fun languageOf(code: String) = OverlayLanguage(code, AnalysisPipeline.displayNameOf(code))

        /** A centred band of the screen, where the content people want to translate usually is. */
        fun defaultCrop(width: Int, height: Int) = IntRect(
            left = (width * 0.06f).toInt(),
            top = (height * 0.3f).toInt(),
            right = (width * 0.94f).toInt(),
            bottom = (height * 0.62f).toInt(),
        )

        @StringRes
        fun messageFor(failure: AnalysisFailure): Int = when (failure) {
            AnalysisFailure.EMPTY_INPUT -> R.string.overlay_message_no_text
            AnalysisFailure.LANGUAGE_SETUP_FAILED -> R.string.overlay_message_setup_failed
            AnalysisFailure.LANGUAGE_UNDETERMINED -> R.string.overlay_message_language_undetermined
            AnalysisFailure.LANGUAGE_DETECTION_FAILED -> R.string.overlay_message_detection_failed
            AnalysisFailure.UNSUPPORTED_PAIR -> R.string.overlay_message_unsupported
            AnalysisFailure.MODEL_DOWNLOAD_TIMED_OUT -> R.string.overlay_message_model_timeout
            AnalysisFailure.TRANSLATION_FAILED -> R.string.overlay_message_translation_failed
        }
    }
}

/**
 * Digit-only, strictly increasing `yyyyMMddHHmmssSSS` ids, like the rest of the app's history
 * ids (capture file names depend on them being digits only).
 */
private object ScanIds {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
    private var last = 0L

    @Synchronized
    fun next(): String {
        var candidate = FORMAT.format(LocalDateTime.now()).toLong()
        if (candidate <= last) candidate = last + 1
        last = candidate
        return candidate.toString()
    }
}
