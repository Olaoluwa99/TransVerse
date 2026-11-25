package com.easit.aiscanner.ui.translate

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.easit.aiscanner.R
import com.easit.aiscanner.scannerAI.AnalysisStatus
import com.easit.aiscanner.scannerAI.ModelDownloadProgress
import com.easit.aiscanner.ui.crop.TextBlock

/** How the text to translate is captured. [scanType] is what history stores. */
enum class CaptureMode(val scanType: String) {
    TEXT("text"),
    IMAGE("image"),
    VOICE("audio"),
}

/** A translation target, with its name resolved once in the user's locale. */
@Immutable
data class LanguageOption(val code: String, val name: String)

/** The result of the last completed analysis. */
@Immutable
data class TranslationResult(
    val scanId: String,
    val sourceText: String,
    val sourceLanguageCode: String,
    val sourceLanguageName: String,
    val targetLanguageCode: String,
    val targetLanguageName: String,
    val translatedText: String,
    val entities: List<String> = emptyList(),
    val smartReplies: List<String> = emptyList(),
)

/** Spoken translation playback. */
@Immutable
data class PlaybackState(
    /** A speech file is ready to play. */
    val available: Boolean = false,
    /** Speech is being synthesized. */
    val preparing: Boolean = false,
    val playing: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
)

@Immutable
data class TranslateUiState(
    val mode: CaptureMode = CaptureMode.TEXT,
    /** Results are not saved to history. */
    val incognito: Boolean = false,
    /** Uri (as a string) of the image shown in Image mode, if any. */
    val imagePreview: String? = null,
    /** OCR is running on the current image. */
    val readingImage: Boolean = false,
    /** First use only: text-recognition module download progress (0–1) before OCR can run. */
    val imageSetupProgress: Float? = null,
    val target: LanguageOption = DEFAULT_TARGET,
    /** Detected source language name, once identified. */
    val detectedLanguage: String? = null,
    val status: AnalysisStatus = AnalysisStatus.IDLE,
    /** While [AnalysisStatus.PREPARING]: module download progress (0–1), null if size unknown. */
    val setupProgress: Float? = null,
    /** While [AnalysisStatus.DOWNLOADING_MODEL]: the language model download's progress. */
    val modelDownload: ModelDownloadProgress? = null,
    val result: TranslationResult? = null,
    val playback: PlaybackState = PlaybackState(),
    /** The detected language can become the target (see [TranslateAction.SwapLanguages]). */
    val canSwap: Boolean = false,
) {
    val isAnalyzing: Boolean
        get() = status == AnalysisStatus.PREPARING ||
            status == AnalysisStatus.IDENTIFYING ||
            status == AnalysisStatus.DOWNLOADING_MODEL ||
            status == AnalysisStatus.TRANSLATING ||
            status == AnalysisStatus.ANALYZING

    companion object {
        val DEFAULT_TARGET = LanguageOption("en", "English")
    }
}

/** State of the target-language picker. */
@Immutable
data class LanguagePickerState(
    val all: List<LanguageOption> = emptyList(),
    val recent: List<LanguageOption> = emptyList(),
    /** Codes of languages whose translation model is on the device. */
    val downloaded: Set<String> = emptySet(),
)

/**
 * An image waiting in the crop editor (camera photo, gallery pick or shared image) before it
 * becomes the capture. Held by the ViewModel so it survives rotation.
 *
 * @param id identifies this crop session (the editor's saved selection belongs to one session)
 * @param bitmap the decoded, upright image; null while it is still being decoded
 * @param textBlocks OCR blocks in [bitmap]'s pixels; null while text recognition is running
 */
@Immutable
data class CropSession(
    val id: Long,
    val bitmap: Bitmap? = null,
    val textBlocks: List<TextBlock>? = null,
) {
    /** The image is shown and text recognition is still looking for blocks. */
    val findingText: Boolean
        get() = bitmap != null && textBlocks == null
}

/** Progress text for an analysis step, or null when nothing is running. */
@StringRes
fun AnalysisStatus.progressLabel(): Int? = when (this) {
    AnalysisStatus.PREPARING -> R.string.translate_status_preparing
    AnalysisStatus.IDENTIFYING -> R.string.translate_status_identifying
    AnalysisStatus.DOWNLOADING_MODEL -> R.string.translate_status_downloading
    AnalysisStatus.TRANSLATING -> R.string.translate_status_translating
    AnalysisStatus.ANALYZING -> R.string.translate_status_analyzing
    AnalysisStatus.IDLE, AnalysisStatus.COMPLETE, AnalysisStatus.ERROR -> null
}

/** Everything the user can do on the Translate screen. */
sealed interface TranslateAction {
    data class SelectMode(val mode: CaptureMode) : TranslateAction
    data class TranscriptChanged(val text: String) : TranslateAction
    data object ClearTranscript : TranslateAction
    data object PasteFromClipboard : TranslateAction
    data object PickFromCamera : TranslateAction
    data object PickFromGallery : TranslateAction
    data object ScanDocument : TranslateAction
    data object StartVoiceInput : TranslateAction
    data object OpenLanguagePicker : TranslateAction
    data object SwapLanguages : TranslateAction
    data object Translate : TranslateAction
    data object CopyTranslation : TranslateAction
    data object ToggleListen : TranslateAction
    data class SeekAudio(val positionMs: Int) : TranslateAction
    data object ShareTranslation : TranslateAction
    data object ShareAudio : TranslateAction
    data object SaveAudio : TranslateAction
    data class OpenEntity(val value: String) : TranslateAction
    data class CopyReply(val text: String) : TranslateAction
    data object OpenBarcodeScanner : TranslateAction
    data object StartQuickCapture : TranslateAction
    data object StopQuickCapture : TranslateAction
    data object CancelTranslation : TranslateAction
}
