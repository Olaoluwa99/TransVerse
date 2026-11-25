package com.easit.aiscanner.ui.translate

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.R
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.CaptureFiles
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.scannerAI.AnalysisException
import com.easit.aiscanner.scannerAI.AnalysisFailure
import com.easit.aiscanner.scannerAI.AnalysisPipeline
import com.easit.aiscanner.scannerAI.AnalysisPolicy
import com.easit.aiscanner.scannerAI.AnalysisStatus
import com.easit.aiscanner.scannerAI.MlKitModules
import com.easit.aiscanner.scannerAI.await
import com.easit.aiscanner.ui.app.IncomingContent
import com.easit.aiscanner.ui.crop.TextBlock
import com.easit.aiscanner.ui.crop.cropBitmap
import com.easit.aiscanner.ui.crop.recognizeTextBlocks
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * The Translate workspace: captures text (typed, from an image, or spoken), analyses it with
 * [AnalysisPipeline], saves the result to history unless incognito, and speaks the translation.
 *
 * History ids: every new capture gets a new scan id, and so does analysing different text;
 * re-analysing the same text (e.g. into another language) keeps the id and updates that entry.
 * Image files are named [CaptureFiles.imageFileName] and speech files
 * [CaptureFiles.audioFileName] after the id.
 */
@HiltViewModel
class TranslateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preference: Preference,
    private val repository: ScanRepository,
    private val recentLanguages: RecentLanguages,
    private val mlKitModules: MlKitModules,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pipeline = AnalysisPipeline(mlKitModules)
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val playback = SpeechPlayback(context, viewModelScope)

    private val languages: List<LanguageOption> = TranslateLanguage.getAllLanguages()
        .map { LanguageOption(it, AnalysisPipeline.displayNameOf(it)) }
        .sortedBy { it.name }

    private val _state = MutableStateFlow(
        TranslateUiState(
            mode = savedStateHandle.get<String>(KEY_MODE)
                ?.let { name -> CaptureMode.entries.firstOrNull { it.name == name } }
                ?: CaptureMode.TEXT,
            incognito = preference.appIncognitoMode,
            imagePreview = savedStateHandle.get<String>(KEY_IMAGE_PREVIEW),
            target = optionFor(recentLanguages.currentTarget),
        )
    )

    val uiState: StateFlow<TranslateUiState> =
        combine(_state, playback.state) { state, speech ->
            state.copy(playback = speech, canSwap = canSwap(state))
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), _state.value)

    private val _picker = MutableStateFlow(LanguagePickerState(all = languages))
    val languagePicker: StateFlow<LanguagePickerState> = _picker.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    /** One-off snackbar messages (string resource ids). */
    val messages: Flow<Int> = _messages.receiveAsFlow()

    /** The editable transcript. Snapshot state, so the text field never lags behind the IME. */
    var transcript by mutableStateOf(savedStateHandle.get<String>(KEY_TRANSCRIPT).orEmpty())
        private set

    private var scanId: String = savedStateHandle.get<String>(KEY_SCAN_ID) ?: newScanId()
        set(value) {
            field = value
            savedStateHandle[KEY_SCAN_ID] = value
        }

    /** Normalized text last analysed under [scanId]; null if none yet. */
    private var lastAnalysedText: String? = savedStateHandle.get<String>(KEY_LAST_ANALYSED)
        set(value) {
            field = value
            savedStateHandle[KEY_LAST_ANALYSED] = value
        }

    /** File in `filesDir` holding the current image, or "" (image mode only). */
    private var imageFileName: String = savedStateHandle.get<String>(KEY_IMAGE_FILE) ?: ""
        set(value) {
            field = value
            savedStateHandle[KEY_IMAGE_FILE] = value
        }

    private val _cropSession = MutableStateFlow<CropSession?>(null)
    /** The image in the crop editor, or null when the editor is closed. */
    val cropSession: StateFlow<CropSession?> = _cropSession.asStateFlow()

    /** Uri (as a string) of the image in the crop editor; kept so the editor survives process death. */
    private var cropSource: String? = savedStateHandle.get<String>(KEY_CROP_SOURCE)
        set(value) {
            field = value
            savedStateHandle[KEY_CROP_SOURCE] = value
        }

    /** [cropSource] is our own camera photo in the cache, to delete when the editor closes. */
    private var cropSourceIsTemporary: Boolean = savedStateHandle.get<Boolean>(KEY_CROP_TEMPORARY) ?: false
        set(value) {
            field = value
            savedStateHandle[KEY_CROP_TEMPORARY] = value
        }

    private var cropSessionId: Long = savedStateHandle.get<Long>(KEY_CROP_SESSION_ID) ?: 0L
        set(value) {
            field = value
            savedStateHandle[KEY_CROP_SESSION_ID] = value
        }

    /** Absolute path of the file the camera app is writing to, while it is open. */
    private var pendingCameraFile: String? = savedStateHandle.get<String>(KEY_CAMERA_FILE)
        set(value) {
            field = value
            savedStateHandle[KEY_CAMERA_FILE] = value
        }

    private var analysisJob: Job? = null
    private var recognitionJob: Job? = null
    private var copyJob: Job? = null
    private var speechJob: Job? = null
    private var cropJob: Job? = null

    init {
        savedStateHandle[KEY_SCAN_ID] = scanId
        refreshRecentLanguages()
        // The process was killed while the crop editor was open: reopen it on the same image.
        // A gallery or shared image's access grant may not have survived; then it just closes.
        cropSource?.let { source ->
            _cropSession.value = CropSession(id = cropSessionId)
            loadCropImage(Uri.parse(source), cropSessionId, restoring = true)
        }
    }

    // region Settings and languages

    /** Re-reads settings that can change while this screen is in the back stack. */
    fun refreshSettings() {
        _state.update { it.copy(incognito = preference.appIncognitoMode) }
    }

    fun selectTarget(option: LanguageOption) {
        recentLanguages.useTarget(option.code)
        _state.update { it.copy(target = option) }
        refreshRecentLanguages()
    }

    /** Refreshes which translation models are on the device, for the language picker. */
    fun refreshDownloadedModels() {
        viewModelScope.launch {
            val downloaded = try {
                RemoteModelManager.getInstance()
                    .getDownloadedModels(TranslateRemoteModel::class.java)
                    .await()
                    .map { it.language }
                    .toSet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not list downloaded models", e)
                return@launch
            }
            _picker.update { it.copy(downloaded = downloaded) }
        }
    }

    /**
     * Swaps direction: the translation becomes the text to translate, and the detected source
     * language becomes the target.
     */
    fun swapLanguages() {
        val result = _state.value.result ?: return
        val newTarget = languages.firstOrNull { it.code == result.sourceLanguageCode } ?: return
        cancelCaptureWork()
        updateTranscript(result.translatedText)
        selectTarget(newTarget)
        setMode(CaptureMode.TEXT)
        _state.update { it.copy(result = null, detectedLanguage = null, status = AnalysisStatus.IDLE) }
    }

    /** Whether [swapLanguages] can do anything right now. */
    private fun canSwap(state: TranslateUiState): Boolean {
        val result = state.result ?: return false
        return result.sourceLanguageCode != result.targetLanguageCode &&
            languages.any { it.code == result.sourceLanguageCode }
    }

    private fun refreshRecentLanguages() {
        val recent = recentLanguages.recentTargets.mapNotNull { code -> languages.firstOrNull { it.code == code } }
        _picker.update { it.copy(recent = recent) }
    }

    private fun optionFor(code: String): LanguageOption =
        languages.firstOrNull { it.code == code }
            ?: languages.firstOrNull { it.code == TranslateUiState.DEFAULT_TARGET.code }
            ?: TranslateUiState.DEFAULT_TARGET

    // endregion

    // region Capture

    fun setMode(mode: CaptureMode) {
        savedStateHandle[KEY_MODE] = mode.name
        _state.update { it.copy(mode = mode) }
    }

    fun updateTranscript(text: String) {
        transcript = text
        savedStateHandle[KEY_TRANSCRIPT] = text
    }

    fun clearTranscript() {
        updateTranscript("")
        speechJob?.cancel()
        playback.clear()
        _state.update { it.copy(result = null, detectedLanguage = null, status = AnalysisStatus.IDLE) }
    }

    fun pasteFromClipboard() {
        val text = TranslateIntents.readClipboardText(context)
        if (text == null) {
            send(R.string.translate_clipboard_empty)
        } else {
            updateTranscript(text)
        }
    }

    /** Speech recognised by the voice input. */
    fun onSpeechRecognized(text: String) {
        if (text.isBlank()) return
        beginNewCapture()
        setMode(CaptureMode.VOICE)
        updateTranscript(text)
    }

    /**
     * A page from the document scanner. It is already cropped and deskewed, so it skips the crop
     * editor and goes straight to text recognition.
     */
    fun onDocumentScanned(uri: Uri) {
        beginNewCapture()
        setMode(CaptureMode.IMAGE)
        val fileName = CaptureFiles.imageFileName(scanId)
        imageFileName = fileName
        setImagePreview(uri.toString())
        copyImageToFiles(uri, fileName)
        recognizeText { withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) } }
    }

    // region Crop editor

    /**
     * A new file for the camera app to write a photo into, as a Uri it may write to, or null if
     * none could be made. Report the outcome with [onCameraResult].
     */
    fun newCameraPhotoUri(): Uri? {
        val directory = File(context.cacheDir, CAPTURES_DIR)
        val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
        return try {
            directory.mkdirs()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            pendingCameraFile = file.absolutePath
            deleteStaleCaptures(keep = setOfNotNull(file.absolutePath, cropSource?.let { localPathOf(it) }))
            uri
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "No content Uri for the camera photo", e)
            null
        }
    }

    /** The camera app closed; [saved] says whether it wrote the photo. */
    fun onCameraResult(saved: Boolean) {
        val path = pendingCameraFile ?: return
        pendingCameraFile = null
        val file = File(path)
        if (saved) {
            setMode(CaptureMode.IMAGE)
            startCrop(Uri.fromFile(file), temporary = true)
        } else {
            viewModelScope.launch(Dispatchers.IO) { file.delete() }
        }
    }

    /** An image chosen in the system photo picker. */
    fun onGalleryImagePicked(uri: Uri) {
        setMode(CaptureMode.IMAGE)
        startCrop(uri, temporary = false)
    }

    /**
     * The user confirmed the crop: the selection becomes a new image capture. [selectedText] is
     * the OCR text the selection covers; when present it becomes the transcript without a second
     * recognition pass.
     */
    fun finishCrop(outputRect: IntRect, rotationDegrees: Int, selectedText: String) {
        val source = _cropSession.value?.bitmap ?: return
        closeCropSession()
        beginNewCapture()
        setMode(CaptureMode.IMAGE)
        val fileName = CaptureFiles.imageFileName(scanId)
        imageFileName = fileName
        // The blocks were read from the unrotated image. A rotated selection usually means the
        // text was sideways, so that reading is unreliable: read the upright output instead.
        val knownText = selectedText.takeIf { it.isNotBlank() && rotationDegrees == 0 }
        // The previous capture's (cancelled) recognition must not clear this capture's progress.
        recognitionJob = null
        _state.update { it.copy(readingImage = true) }
        copyJob = viewModelScope.launch {
            val destination = File(context.filesDir, fileName)
            val cropped = try {
                withContext(Dispatchers.IO) { saveCrop(source, outputRect, rotationDegrees, destination) }
            } catch (e: CancellationException) {
                // Cancelled by other capture work; unless a newer crop replaced this one, stop showing progress.
                if (copyJob === coroutineContext[Job]) _state.update { it.copy(readingImage = false) }
                throw e
            }
            if (imageFileName != fileName) return@launch
            if (cropped == null) {
                imageFileName = ""
                _state.update { it.copy(readingImage = false) }
                send(R.string.image_crop_failed)
                return@launch
            }
            setImagePreview(Uri.fromFile(destination).toString())
            if (knownText != null) {
                updateTranscript(knownText)
                _state.update { it.copy(readingImage = false) }
            } else {
                recognizeText { InputImage.fromBitmap(cropped, 0) }
            }
        }
    }

    /** The user closed the crop editor without cropping; the current capture stays as it was. */
    fun cancelCrop() {
        closeCropSession()
    }

    private fun startCrop(uri: Uri, temporary: Boolean) {
        closeCropSession()
        val id = System.currentTimeMillis()
        cropSessionId = id
        cropSource = uri.toString()
        cropSourceIsTemporary = temporary
        _cropSession.value = CropSession(id = id)
        loadCropImage(uri, id, restoring = false)
    }

    /** Decodes the image for the editor, then looks for text blocks in it. */
    private fun loadCropImage(uri: Uri, id: Long, restoring: Boolean) {
        cropJob?.cancel()
        cropJob = viewModelScope.launch {
            val bitmap = try {
                withContext(Dispatchers.IO) { CropImageDecoder.decode(context, uri) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "Image too large for the crop editor", e)
                failCropLoad(R.string.translate_crop_image_too_large, restoring)
                return@launch
            } catch (e: Exception) {
                // IOException (unreadable or not an image), SecurityException (grant revoked)…
                Log.w(TAG, "Could not open the image for cropping", e)
                failCropLoad(R.string.image_open_failed, restoring)
                return@launch
            }
            _cropSession.update { current -> if (current != null && current.id == id) current.copy(bitmap = bitmap) else current }

            val blocks: List<TextBlock> = try {
                recognizeTextBlocks(bitmap, textRecognizer, mlKitModules)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The editor still works without blocks: the user draws a region instead.
                Log.w(TAG, "Could not find text blocks", e)
                emptyList()
            }
            _cropSession.update { current -> if (current != null && current.id == id) current.copy(textBlocks = blocks) else current }
        }
    }

    private fun failCropLoad(@StringRes message: Int, restoring: Boolean) {
        closeCropSession()
        // After process death the old Uri grant is often gone; reopening was best effort.
        if (!restoring) send(message)
    }

    /** Closes the editor and deletes its camera photo; the decoded image is let go. */
    private fun closeCropSession() {
        cropJob?.cancel()
        cropJob = null
        _cropSession.value = null
        val source = cropSource
        val temporary = cropSourceIsTemporary
        cropSource = null
        cropSourceIsTemporary = false
        if (temporary && source != null) {
            val path = localPathOf(source) ?: return
            viewModelScope.launch(Dispatchers.IO) { File(path).delete() }
        }
    }

    /** Crops, rotates and saves [source] as a JPEG; returns the cropped bitmap, or null on failure. */
    private fun saveCrop(source: Bitmap, rect: IntRect, rotationDegrees: Int, destination: File): Bitmap? =
        try {
            val cropped = cropBitmap(source, rect, rotationDegrees)
            val encoded = destination.outputStream().use { cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!encoded) throw IOException("JPEG encoding failed")
            cropped
        } catch (e: IOException) {
            Log.w(TAG, "Could not save the cropped image", e)
            destination.delete()
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Invalid crop", e)
            destination.delete()
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Not enough memory to crop", e)
            destination.delete()
            null
        }

    /** Camera photos left behind in the cache (e.g. the process was killed mid-capture). */
    private fun deleteStaleCaptures(keep: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            File(context.cacheDir, CAPTURES_DIR).listFiles()
                ?.filter { it.absolutePath !in keep }
                ?.forEach { it.delete() }
        }
    }

    /** The file path of a `file:` Uri string inside our captures cache, else null. */
    private fun localPathOf(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val captures = File(context.cacheDir, CAPTURES_DIR).absolutePath
        return path.takeIf { File(it).parentFile?.absolutePath == captures }
    }

    // endregion

    /** Takes content handed over from outside the screen. Call once per content. */
    fun takeIncoming(content: IncomingContent) {
        when (content) {
            is IncomingContent.Text -> {
                beginNewCapture()
                setMode(CaptureMode.TEXT)
                updateTranscript(content.text)
            }
            is IncomingContent.Speech -> {
                beginNewCapture()
                setMode(CaptureMode.VOICE)
                updateTranscript(content.text)
            }
            is IncomingContent.SharedImage -> {
                // Crop first, like a gallery pick. The grant is temporary, so it is decoded now.
                setMode(CaptureMode.IMAGE)
                startCrop(content.uri, temporary = false)
            }
            is IncomingContent.ScreenCapture -> {
                // Already cropped in the overlay.
                beginNewCapture()
                setMode(CaptureMode.IMAGE)
                imageFileName = content.fileName
                val uri = Uri.fromFile(File(context.filesDir, content.fileName))
                setImagePreview(uri.toString())
                recognizeText { withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) } }
            }
        }
    }

    /**
     * Starts a new history entry for a new capture. An analysis still running for the previous
     * capture is abandoned.
     */
    private fun beginNewCapture() {
        cancelCaptureWork()
        scanId = newScanId()
        lastAnalysedText = null
        imageFileName = ""
        setImagePreview(null)
        updateTranscript("")
        _state.update {
            it.copy(result = null, detectedLanguage = null, status = AnalysisStatus.IDLE, readingImage = false)
        }
    }

    private fun cancelCaptureWork() {
        analysisJob?.cancel()
        recognitionJob?.cancel()
        copyJob?.cancel()
        speechJob?.cancel()
        playback.clear()
    }

    private fun setImagePreview(uri: String?) {
        savedStateHandle[KEY_IMAGE_PREVIEW] = uri
        _state.update { it.copy(imagePreview = uri) }
    }

    private fun copyImageToFiles(source: Uri, fileName: String) {
        copyJob = viewModelScope.launch {
            val destination = File(context.filesDir, fileName)
            val copied = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(source)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } != null
                } catch (e: IOException) {
                    Log.w(TAG, "Could not copy the image", e)
                    false
                } catch (e: SecurityException) {
                    Log.w(TAG, "No access to the image", e)
                    false
                }
            }
            if (imageFileName != fileName) return@launch
            if (copied) {
                // The private copy outlives temporary URI grants (and process death).
                setImagePreview(Uri.fromFile(destination).toString())
            } else {
                imageFileName = ""
                send(R.string.image_save_failed)
            }
        }
    }

    /** Reads the text in the image [loadImage] gives into the transcript. */
    private fun recognizeText(loadImage: suspend () -> InputImage) {
        recognitionJob?.cancel()
        recognitionJob = viewModelScope.launch {
            _state.update { it.copy(readingImage = true) }
            try {
                val image = loadImage()
                // First use: wait for Play services to deliver text recognition, with progress.
                mlKitModules.ensureInstalled(listOf(textRecognizer)) { progress ->
                    _state.update { it.copy(imageSetupProgress = progress ?: 0f) }
                }
                _state.update { it.copy(imageSetupProgress = null) }
                val text = MlKitModules.retryWhileUnavailable { textRecognizer.process(image).await().text }
                updateTranscript(text)
                if (text.isBlank()) send(R.string.translate_no_text_in_image)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "Could not open the image", e)
                send(R.string.image_open_failed)
            } catch (e: Exception) {
                Log.w(TAG, "Text recognition failed", e)
                send(R.string.image_text_recognition_failed)
            } finally {
                // A newer capture may already be reading or saving its image; leave its progress.
                if (recognitionJob === coroutineContext[Job]) {
                    _state.update { it.copy(readingImage = false, imageSetupProgress = null) }
                }
            }
        }
    }

    // endregion

    // region Analysis

    fun translate() {
        val text = AnalysisPolicy.normalizeInput(transcript)
        if (text.isEmpty()) {
            send(R.string.translate_error_empty)
            return
        }
        assignIdForAnalysis(text)

        val id = scanId
        val state = _state.value
        val mode = state.mode
        val target = state.target
        val imageUrl = if (mode == CaptureMode.IMAGE) imageFileName else ""
        val audioUrl = if (mode == CaptureMode.VOICE) CaptureFiles.audioFileName(id) else ""

        analysisJob?.cancel()
        speechJob?.cancel()
        playback.clear()
        _state.update {
            it.copy(
                result = null,
                detectedLanguage = null,
                status = AnalysisStatus.IDENTIFYING,
                setupProgress = null,
            )
        }
        analysisJob = viewModelScope.launch {
            val output = try {
                pipeline.analyze(
                    text = text,
                    targetLanguageCode = target.code,
                    onStatus = { status -> _state.update { it.copy(status = status) } },
                    onLanguageDetected = { _, name -> _state.update { it.copy(detectedLanguage = name) } },
                    onSetupProgress = { progress -> _state.update { it.copy(setupProgress = progress) } },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AnalysisException) {
                Log.w(TAG, "Analysis failed: ${e.failure}", e.cause)
                fail(e.failure.message())
                return@launch
            } catch (e: Exception) {
                Log.w(TAG, "Analysis failed", e)
                fail(R.string.translate_error_translation_failed)
                return@launch
            }

            val result = TranslationResult(
                scanId = id,
                sourceText = output.sourceText,
                sourceLanguageCode = output.sourceLanguageCode,
                sourceLanguageName = output.sourceLanguageName,
                targetLanguageCode = output.targetLanguageCode,
                targetLanguageName = target.name,
                translatedText = output.translatedText,
                entities = output.entities,
                smartReplies = output.smartReplies,
            )

            val incognito = preference.appIncognitoMode
            if (!incognito) save(result, mode, imageUrl, audioUrl)
            _state.update {
                it.copy(status = AnalysisStatus.COMPLETE, result = result, incognito = incognito)
            }

            // Speech made for an earlier translation under this id is now stale.
            val speechFile = File(context.filesDir, CaptureFiles.audioFileName(id))
            withContext(Dispatchers.IO) { speechFile.delete() }
            if (mode == CaptureMode.VOICE) {
                // Voice captures keep the spoken translation with the history entry.
                speechJob = launch { synthesize(result, play = false) }
            }
        }
    }

    /**
     * Re-analysing the same text (e.g. into another language) updates the same history entry;
     * analysing different text starts a new one instead of overwriting the previous entry.
     */
    private fun assignIdForAnalysis(normalizedText: String) {
        val previous = lastAnalysedText
        if (previous != null && previous != normalizedText) {
            scanId = newScanId()
        }
        lastAnalysedText = normalizedText
    }

    private suspend fun save(result: TranslationResult, mode: CaptureMode, imageUrl: String, audioUrl: String) {
        val scan = Scan(
            id = result.scanId,
            dateTime = DATE_FORMATTER.format(LocalDateTime.now()),
            timeMilliseconds = System.currentTimeMillis(),
            scanType = mode.scanType,
            transcriptText = result.sourceText,
            translatedText = result.translatedText,
            sourceLanguage = result.sourceLanguageName,
            translatedLanguage = result.targetLanguageName,
            entities = result.entities,
            smartReplies = result.smartReplies,
            imageUrl = imageUrl,
            audioUrl = audioUrl,
            barcodeScan = "",
        )
        try {
            withContext(Dispatchers.IO) { repository.insert(scan) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the scan", e)
            send(R.string.translate_error_save_failed)
        }
    }

    private fun fail(@StringRes message: Int) {
        _state.update { it.copy(status = AnalysisStatus.ERROR) }
        send(message)
    }

    @StringRes
    private fun AnalysisFailure.message(): Int = when (this) {
        AnalysisFailure.EMPTY_INPUT -> R.string.translate_error_empty
        AnalysisFailure.LANGUAGE_SETUP_FAILED -> R.string.translate_error_setup_failed
        AnalysisFailure.LANGUAGE_UNDETERMINED -> R.string.translate_error_undetermined
        AnalysisFailure.LANGUAGE_DETECTION_FAILED -> R.string.translate_error_detection_failed
        AnalysisFailure.UNSUPPORTED_PAIR -> R.string.translate_error_unsupported_pair
        AnalysisFailure.MODEL_DOWNLOAD_TIMED_OUT -> R.string.translate_error_download_timeout
        AnalysisFailure.TRANSLATION_FAILED -> R.string.translate_error_translation_failed
    }

    // endregion

    // region Speech

    /** Plays or pauses the spoken translation, synthesizing it first when needed. */
    fun toggleListen() {
        val result = _state.value.result ?: return
        val current = playback.state.value
        if (current.preparing) return
        if (current.available) {
            if (!playback.togglePlay()) send(R.string.audio_generation_failed)
            return
        }
        speechJob?.cancel()
        speechJob = viewModelScope.launch { synthesize(result, play = true) }
    }

    fun seekAudio(positionMs: Int) = playback.seekTo(positionMs)

    /** The speech file of the current result, if it has been made. */
    fun speechFileForSharing(): File? {
        val result = _state.value.result ?: return null
        if (!playback.state.value.available) return null
        return File(context.filesDir, CaptureFiles.audioFileName(result.scanId))
    }

    fun saveAudio() {
        val file = speechFileForSharing()
        if (file == null) {
            send(R.string.no_audio_available)
            return
        }
        viewModelScope.launch { send(TranslateIntents.saveAudioToMusic(context, file)) }
    }

    private suspend fun synthesize(result: TranslationResult, play: Boolean) {
        val ok = playback.synthesize(
            text = result.translatedText,
            languageTag = result.targetLanguageCode,
            speechRate = preference.appReadingSpeed / AUDIO_SETTING_SCALE,
            pitch = preference.appSpeechPitch / AUDIO_SETTING_SCALE,
            output = File(context.filesDir, CaptureFiles.audioFileName(result.scanId)),
        )
        when {
            !ok -> send(R.string.audio_generation_failed)
            play && !playback.togglePlay() -> send(R.string.audio_generation_failed)
        }
    }

    // endregion

    private fun send(@StringRes message: Int) {
        _messages.trySend(message)
    }

    override fun onCleared() {
        // Leaving the screen for good closes the crop editor; its camera photo isn't needed.
        val source = cropSource
        if (cropSourceIsTemporary && source != null) localPathOf(source)?.let { File(it).delete() }
        playback.release()
        textRecognizer.close()
        pipeline.close()
        super.onCleared()
    }

    companion object {
        private const val TAG = "TranslateViewModel"
        private const val STOP_TIMEOUT_MS = 5_000L
        /** Reading speed and pitch are stored as 20..100 where 50 is normal. */
        private const val AUDIO_SETTING_SCALE = 50f
        private const val KEY_MODE = "mode"
        private const val KEY_TRANSCRIPT = "transcript"
        private const val KEY_SCAN_ID = "scan_id"
        private const val KEY_LAST_ANALYSED = "last_analysed"
        private const val KEY_IMAGE_FILE = "image_file"
        private const val KEY_IMAGE_PREVIEW = "image_preview"
        private const val KEY_CROP_SOURCE = "crop_source"
        private const val KEY_CROP_TEMPORARY = "crop_temporary"
        private const val KEY_CROP_SESSION_ID = "crop_session_id"
        private const val KEY_CAMERA_FILE = "camera_file"
        /** Folder in `cacheDir` for camera photos (exposed by the FileProvider as "captures"). */
        private const val CAPTURES_DIR = "captures"
        private const val JPEG_QUALITY = 92
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
        private val ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        private var lastIssuedId = 0L

        /**
         * Digit-only timestamp ids (capture file names depend on that), strictly increasing so
         * two captures in the same millisecond never share an id.
         */
        @Synchronized
        private fun newScanId(): String {
            val candidate = ID_FORMATTER.format(LocalDateTime.now()).toLong()
            lastIssuedId = maxOf(candidate, lastIssuedId + 1)
            return lastIssuedId.toString()
        }
    }
}
