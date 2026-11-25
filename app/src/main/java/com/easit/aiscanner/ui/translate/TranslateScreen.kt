package com.easit.aiscanner.ui.translate

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easit.aiscanner.R
import com.easit.aiscanner.floatingWindow.CaptureCoordinator
import com.easit.aiscanner.floatingWindow.Floater
import com.easit.aiscanner.scannerAI.AnalysisStatus
import com.easit.aiscanner.ui.app.IncomingContent
import com.easit.aiscanner.ui.theme.TransVerseTheme
import com.easit.aiscanner.utils.EntityActions
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The main workspace: capture text by typing, image (camera/gallery/screen capture) or voice,
 * then translate and act on it.
 *
 * @param incoming content handed over from outside (share sheet, overlay, barcode); take it,
 *   then call [onIncomingConsumed]
 */
@Composable
fun TranslateScreen(
    incoming: IncomingContent?,
    onIncomingConsumed: () -> Unit,
    onOpenBarcodeScanner: () -> Unit,
    onStartQuickCapture: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TranslateViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker by viewModel.languagePicker.collectAsStateWithLifecycle()
    val cropSession by viewModel.cropSession.collectAsStateWithLifecycle()
    val floatingCaptureOn by CaptureCoordinator.bubbleRunning.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }

    fun showMessage(message: Int) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(message))
        }
    }

    // The ViewModel takes incoming content once; consuming it stops a rotation from repeating it.
    LaunchedEffect(incoming) {
        if (incoming != null) {
            viewModel.takeIncoming(incoming)
            onIncomingConsumed()
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { showMessage(it) }
    }
    // Incognito can be switched in Settings while this screen is in the back stack.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshSettings() }

    // Camera: the photo goes to a cache file, then into the crop editor.
    val takePictureLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            viewModel.onCameraResult(saved)
        }
    fun launchCamera() {
        val uri = viewModel.newCameraPhotoUri()
        if (uri == null) {
            showMessage(R.string.image_open_failed)
            return
        }
        try {
            takePictureLauncher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            viewModel.onCameraResult(false)
            showMessage(R.string.translate_camera_unavailable)
        }
    }
    // The app declares CAMERA (barcode scanner), so the camera app may only be used once granted.
    val cameraPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else showMessage(R.string.translate_camera_permission_denied)
        }

    // Gallery: the system photo picker needs no storage permission.
    val galleryLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onGalleryImagePicked(uri)
        }

    // Document scanner: already cropped and deskewed, so it goes straight to text recognition.
    val activity = LocalActivity.current
    val scannerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_OK -> {
                    val uri = DocumentScanner.scannedImage(result.data)
                    if (uri != null) viewModel.onDocumentScanned(uri) else showMessage(R.string.translate_scan_failed)
                }
                Activity.RESULT_CANCELED -> Unit
                else -> showMessage(R.string.translate_scan_failed)
            }
        }
    fun launchDocumentScanner() {
        if (activity == null) {
            showMessage(R.string.translate_scanner_unavailable)
            return
        }
        DocumentScanner.startScanIntent(activity)
            .addOnSuccessListener { sender ->
                try {
                    scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } catch (e: IllegalStateException) {
                    // The screen left composition while Play services was preparing the scanner.
                    Log.w(TAG, "Document scanner result launcher is gone", e)
                }
            }
            .addOnFailureListener { error ->
                // Play services missing or outdated, or the scanner module can't be installed.
                Log.w(TAG, "Document scanner unavailable", error)
                showMessage(R.string.translate_scanner_unavailable)
            }
    }

    val speechLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (result.resultCode == Activity.RESULT_OK && text.isNotBlank()) {
                viewModel.onSpeechRecognized(text)
            }
        }
    val speechPrompt = stringResource(R.string.speech_prompt)
    fun launchSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.speech_unavailable)
        }
    }
    val recordPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchSpeechRecognition() else showMessage(R.string.translate_record_permission_denied)
        }

    TranslateContent(
        state = state,
        transcript = viewModel.transcript,
        snackbarHostState = snackbarHostState,
        floatingCaptureOn = floatingCaptureOn,
        modifier = modifier,
        onAction = { action ->
            when (action) {
                is TranslateAction.SelectMode -> viewModel.setMode(action.mode)
                is TranslateAction.TranscriptChanged -> viewModel.updateTranscript(action.text)
                TranslateAction.ClearTranscript -> viewModel.clearTranscript()
                TranslateAction.PasteFromClipboard -> viewModel.pasteFromClipboard()
                TranslateAction.PickFromCamera -> {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera()
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
                TranslateAction.PickFromGallery -> try {
                    galleryLauncher.launch(
                        PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            .build()
                    )
                } catch (_: ActivityNotFoundException) {
                    showMessage(R.string.image_open_failed)
                }
                TranslateAction.ScanDocument -> launchDocumentScanner()
                TranslateAction.StartVoiceInput -> {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (granted) launchSpeechRecognition()
                    else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
                TranslateAction.OpenLanguagePicker -> {
                    viewModel.refreshDownloadedModels()
                    showLanguagePicker = true
                }
                TranslateAction.SwapLanguages -> viewModel.swapLanguages()
                TranslateAction.Translate -> viewModel.translate()
                TranslateAction.CopyTranslation -> state.result?.let {
                    TranslateIntents.copyToClipboard(context, "Translation", it.translatedText)
                    showMessage(R.string.copied_to_clipboard)
                }
                TranslateAction.ToggleListen -> viewModel.toggleListen()
                is TranslateAction.SeekAudio -> viewModel.seekAudio(action.positionMs)
                TranslateAction.ShareTranslation -> state.result?.let {
                    TranslateIntents.shareText(context, it.translatedText)
                }
                TranslateAction.ShareAudio -> {
                    val file = viewModel.speechFileForSharing()
                    if (file == null || !TranslateIntents.shareAudio(context, file)) {
                        showMessage(R.string.no_audio_available)
                    }
                }
                TranslateAction.SaveAudio -> viewModel.saveAudio()
                is TranslateAction.OpenEntity -> {
                    if (EntityActions.openOrCopy(context, action.value) == EntityActions.Outcome.COPIED) {
                        showMessage(R.string.copied_to_clipboard)
                    }
                }
                is TranslateAction.CopyReply -> {
                    TranslateIntents.copyToClipboard(context, "Suggested reply", action.text)
                    showMessage(R.string.copied_to_clipboard)
                }
                TranslateAction.OpenBarcodeScanner -> onOpenBarcodeScanner()
                TranslateAction.StartQuickCapture -> onStartQuickCapture()
                TranslateAction.StopQuickCapture -> runCatching {
                    context.startService(Intent(context, Floater::class.java).setAction(Floater.ACTION_STOP))
                }
            }
        },
    )

    if (showLanguagePicker) {
        LanguagePickerSheet(
            picker = picker,
            selectedCode = state.target.code,
            onSelect = viewModel::selectTarget,
            onDismiss = { showLanguagePicker = false },
        )
    }

    cropSession?.let { session ->
        CropImageDialog(
            session = session,
            onCancel = viewModel::cancelCrop,
            onDone = viewModel::finishCrop,
        )
    }
}

private const val TAG = "TranslateScreen"

/** Stateless Translate screen. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslateContent(
    state: TranslateUiState,
    transcript: String,
    snackbarHostState: SnackbarHostState,
    onAction: (TranslateAction) -> Unit,
    modifier: Modifier = Modifier,
    floatingCaptureOn: Boolean = false,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.translate_title)) },
                subtitle = if (state.incognito) {
                    { IncognitoNotice() }
                } else {
                    null
                },
                actions = {
                    IconButton(onClick = { onAction(TranslateAction.OpenBarcodeScanner) }) {
                        Icon(
                            Icons.Rounded.QrCodeScanner,
                            contentDescription = stringResource(R.string.translate_action_scan_code),
                        )
                    }
                    // The headline feature gets its own always-visible toggle, not a ⋮ menu entry.
                    FilledTonalIconToggleButton(
                        checked = floatingCaptureOn,
                        onCheckedChange = { turnOn ->
                            onAction(
                                if (turnOn) TranslateAction.StartQuickCapture
                                else TranslateAction.StopQuickCapture,
                            )
                        },
                    ) {
                        Icon(
                            Icons.Rounded.PictureInPictureAlt,
                            contentDescription = stringResource(
                                if (floatingCaptureOn) R.string.translate_action_stop_floating_capture
                                else R.string.translate_action_floating_capture,
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FloatingCaptureCard(
                    running = floatingCaptureOn,
                    onStart = { onAction(TranslateAction.StartQuickCapture) },
                    onStop = { onAction(TranslateAction.StopQuickCapture) },
                )
                CaptureModeSelector(
                    selected = state.mode,
                    onSelect = { onAction(TranslateAction.SelectMode(it)) },
                )
                CaptureCard(
                    mode = state.mode,
                    transcript = transcript,
                    imagePreview = state.imagePreview,
                    readingImage = state.readingImage,
                    imageSetupProgress = state.imageSetupProgress,
                    onAction = onAction,
                )
                LanguageBar(
                    detectedLanguage = state.detectedLanguage,
                    target = state.target,
                    canSwap = state.canSwap && !state.isAnalyzing,
                    onSwap = { onAction(TranslateAction.SwapLanguages) },
                    onChooseTarget = { onAction(TranslateAction.OpenLanguagePicker) },
                )
                TranslateButton(
                    status = state.status,
                    setupProgress = state.setupProgress,
                    detectedLanguage = state.detectedLanguage,
                    running = state.isAnalyzing,
                    enabled = transcript.isNotBlank() && !state.readingImage,
                    onTranslate = { onAction(TranslateAction.Translate) },
                )
                val result = state.result
                AnimatedVisibility(visible = result != null) {
                    if (result != null) {
                        ResultCard(result = result, playback = state.playback, onAction = onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun IncognitoNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = stringResource(R.string.translate_incognito_notice),
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

// region Previews

@Composable
private fun PreviewScreen(state: TranslateUiState, transcript: String = "") {
    TransVerseTheme {
        TranslateContent(
            state = state,
            transcript = transcript,
            snackbarHostState = remember { SnackbarHostState() },
            onAction = {},
        )
    }
}

@Preview(name = "Text mode")
@Composable
private fun TextModePreview() = PreviewScreen(TranslateUiState())

@Preview(name = "Image mode")
@Composable
private fun ImageModePreview() = PreviewScreen(
    TranslateUiState(mode = CaptureMode.IMAGE, readingImage = true),
)

@Preview(name = "Voice mode, incognito")
@Composable
private fun VoiceModePreview() = PreviewScreen(
    TranslateUiState(mode = CaptureMode.VOICE, incognito = true),
    transcript = "Où est la gare ?",
)

@Preview(name = "Translating")
@Composable
private fun LoadingPreview() = PreviewScreen(
    TranslateUiState(status = AnalysisStatus.DOWNLOADING_MODEL, detectedLanguage = "French"),
    transcript = PreviewResult.sourceText,
)

@Preview(name = "Result", heightDp = 1400)
@Composable
private fun ResultPreview() = PreviewScreen(
    TranslateUiState(
        status = AnalysisStatus.COMPLETE,
        detectedLanguage = "French",
        result = PreviewResult,
        canSwap = true,
        playback = PlaybackState(available = true, durationMs = 5_000),
    ),
    transcript = PreviewResult.sourceText,
)

// endregion
