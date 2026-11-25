package com.easit.aiscanner.ui.translate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.easit.aiscanner.R
import com.easit.aiscanner.model.Scan
import com.easit.aiscanner.ui.theme.TransVerseTheme
import com.easit.aiscanner.ui.theme.readingTextStyle
import com.easit.aiscanner.utils.EntityActions
import kotlinx.coroutines.launch

/** What the user can do on a saved scan. */
internal sealed interface SavedScanAction {
    data object CopyOriginal : SavedScanAction
    data object CopyTranslation : SavedScanAction
    data object ShareTranslation : SavedScanAction
    data object TogglePlay : SavedScanAction
    data class Seek(val positionMs: Int) : SavedScanAction
    data object ShareAudio : SavedScanAction
    data object SaveAudio : SavedScanAction
    data class OpenEntity(val value: String) : SavedScanAction
    data class CopyReply(val text: String) : SavedScanAction
}

/** Read-only view of a saved history entry. */
@Composable
fun SavedScanScreen(
    scanId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedScanViewModel = hiltViewModel(),
) {
    LaunchedEffect(scanId) { viewModel.load(scanId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun showMessage(message: Int) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(context.getString(message))
        }
    }

    fun copyText(label: String, text: String) {
        TranslateIntents.copyToClipboard(context, label, text)
        showMessage(R.string.copied_to_clipboard)
    }

    SavedScanContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        modifier = modifier,
        onAction = { action ->
            val scan = (state as? SavedScanUiState.Loaded)?.scan
            when (action) {
                SavedScanAction.CopyOriginal -> scan?.let { copyText("Original text", it.transcriptText) }
                SavedScanAction.CopyTranslation -> scan?.let { copyText("Translation", it.translatedText) }
                SavedScanAction.ShareTranslation -> scan?.let {
                    TranslateIntents.shareText(context, it.translatedText)
                }
                SavedScanAction.TogglePlay -> {
                    if (!viewModel.togglePlay()) showMessage(R.string.no_audio_available)
                }
                is SavedScanAction.Seek -> viewModel.seekTo(action.positionMs)
                SavedScanAction.ShareAudio -> {
                    val file = viewModel.speechFile()
                    if (file == null || !TranslateIntents.shareAudio(context, file)) {
                        showMessage(R.string.no_audio_available)
                    }
                }
                SavedScanAction.SaveAudio -> {
                    val file = viewModel.speechFile()
                    if (file == null) {
                        showMessage(R.string.no_audio_available)
                    } else {
                        scope.launch { showMessage(TranslateIntents.saveAudioToMusic(context, file)) }
                    }
                }
                is SavedScanAction.OpenEntity -> {
                    if (EntityActions.openOrCopy(context, action.value) == EntityActions.Outcome.COPIED) {
                        showMessage(R.string.copied_to_clipboard)
                    }
                }
                is SavedScanAction.CopyReply -> copyText("Suggested reply", action.text)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SavedScanContent(
    state: SavedScanUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onAction: (SavedScanAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scan = (state as? SavedScanUiState.Loaded)?.scan
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(scan?.typeLabel() ?: R.string.translate_saved_translation)) },
                subtitle = if (scan != null) {
                    { Text(scan.dateTime) }
                } else {
                    null
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (scan != null) {
                        IconButton(onClick = { onAction(SavedScanAction.ShareTranslation) }) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.translate_action_share))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state) {
            SavedScanUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
            SavedScanUiState.NotFound -> NotFound(Modifier.padding(padding))
            is SavedScanUiState.Loaded -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SavedScanDetails(state = state, onAction = onAction)
            }
        }
    }
}

@Composable
private fun SavedScanDetails(state: SavedScanUiState.Loaded, onAction: (SavedScanAction) -> Unit) {
    val scan = state.scan
    Column(
        modifier = Modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        state.imageFile?.let { file ->
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.translate_saved_image_description),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        }

        Text(
            text = stringResource(R.string.translate_language_pair, scan.sourceLanguage, scan.translatedLanguage),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        TextBlock(
            title = stringResource(R.string.translate_saved_original),
            subtitle = scan.sourceLanguage,
            text = scan.transcriptText,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            copyLabel = stringResource(R.string.translate_action_copy_original),
            onCopy = { onAction(SavedScanAction.CopyOriginal) },
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BlockHeader(
                    title = stringResource(R.string.translate_saved_translation),
                    subtitle = scan.translatedLanguage,
                    copyLabel = stringResource(R.string.translate_action_copy),
                    onCopy = { onAction(SavedScanAction.CopyTranslation) },
                )
                SelectionContainer {
                    Text(scan.translatedText, style = readingTextStyle())
                }
                if (state.playback.available) {
                    SpeechPlayer(
                        playback = state.playback,
                        onTogglePlay = { onAction(SavedScanAction.TogglePlay) },
                        onSeek = { onAction(SavedScanAction.Seek(it)) },
                    )
                    Row {
                        IconButton(onClick = { onAction(SavedScanAction.ShareAudio) }) {
                            Icon(
                                Icons.Rounded.AudioFile,
                                contentDescription = stringResource(R.string.translate_action_share_audio),
                            )
                        }
                        IconButton(onClick = { onAction(SavedScanAction.SaveAudio) }) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = stringResource(R.string.translate_action_save_audio),
                            )
                        }
                    }
                }
            }
        }

        if (scan.entities.isNotEmpty()) {
            EntityChips(entities = scan.entities, onEntity = { onAction(SavedScanAction.OpenEntity(it)) })
        }
        if (scan.smartReplies.isNotEmpty()) {
            ReplyChips(replies = scan.smartReplies, onReply = { onAction(SavedScanAction.CopyReply(it)) })
        }
    }
}

@Composable
private fun TextBlock(
    title: String,
    subtitle: String,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    copyLabel: String,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BlockHeader(title = title, subtitle = subtitle, copyLabel = copyLabel, onCopy = onCopy)
            SelectionContainer {
                Text(text, style = readingTextStyle())
            }
        }
    }
}

@Composable
private fun BlockHeader(title: String, subtitle: String, copyLabel: String, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = copyLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NotFound(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = MaterialShapes.Cookie9Sided.toShape(),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.SearchOff, contentDescription = null, modifier = Modifier.size(40.dp))
            }
        }
        Text(
            text = stringResource(R.string.translate_saved_not_found_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = stringResource(R.string.translate_saved_not_found_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun Scan.typeLabel(): Int = when (scanType) {
    "image" -> R.string.translate_saved_type_image
    "audio" -> R.string.translate_saved_type_audio
    else -> R.string.translate_saved_type_text
}

private val PreviewScan = Scan(
    id = "20260101120000000",
    dateTime = "12:00 01/01/2026",
    timeMilliseconds = 0L,
    scanType = "audio",
    transcriptText = PreviewResult.sourceText,
    translatedText = PreviewResult.translatedText,
    sourceLanguage = "French",
    translatedLanguage = "English",
    entities = PreviewResult.entities,
    smartReplies = PreviewResult.smartReplies,
    imageUrl = "",
    audioUrl = "",
    barcodeScan = "",
)

@Preview(heightDp = 1200)
@Composable
private fun SavedScanPreview() {
    TransVerseTheme {
        SavedScanContent(
            state = SavedScanUiState.Loaded(
                scan = PreviewScan,
                imageFile = null,
                playback = PlaybackState(available = true, durationMs = 4_000),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun SavedScanNotFoundPreview() {
    TransVerseTheme {
        SavedScanContent(
            state = SavedScanUiState.NotFound,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onAction = {},
        )
    }
}
