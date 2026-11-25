@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.easit.aiscanner.ui.overlay

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easit.aiscanner.R
import com.easit.aiscanner.scannerAI.AnalysisStatus
import com.easit.aiscanner.ui.crop.CropEditor
import com.easit.aiscanner.ui.crop.CropEditorControls
import com.easit.aiscanner.ui.theme.TransVerseTheme
import com.easit.aiscanner.ui.theme.readingTextStyle
import com.easit.aiscanner.ui.translate.ModelDownloadInfo

/** Height reserved above the image for the top bar. */
private val TopBarHeight = 64.dp

/**
 * The capture editor over a frozen screenshot, shown in a focusable overlay window by the capture
 * service. Back closes the language list, then the translation, then the editor.
 */
@Composable
fun CaptureEditor(model: CaptureEditorModel) {
    val state by model.state.collectAsStateWithLifecycle()
    BackHandler(onBack = model::onBack)
    CaptureEditorContent(
        state = state,
        languages = model.languages,
        onClose = model::close,
        onTranslate = model::translate,
        onCopyText = model::copyText,
        onOpenInApp = model::openInApp,
        onOpenPicker = model::openPicker,
        onClosePicker = model::closePicker,
        onSelectLanguage = model::selectLanguage,
        onCopyTranslation = model::copyTranslation,
        onToggleSpeech = model::toggleSpeech,
        onShare = model::share,
        onShowOnImageChange = model::setShowOnImage,
        onDismissResult = model::dismissResult,
        editor = { padding ->
            CropEditor(
                image = model.image,
                state = model.cropState,
                modifier = Modifier.fillMaxSize(),
                blockTranslations = state.blockTranslations.takeIf { state.showOnImage },
                contentPadding = padding,
            )
        },
        controls = { CropEditorControls(state = model.cropState) },
    )
}

/**
 * Stateless editor chrome around an [editor] slot (the crop editor, given the padding that keeps
 * the image clear of the chrome) and a [controls] slot (the crop editor's toolbar).
 */
@Composable
fun CaptureEditorContent(
    state: CaptureEditorUiState,
    languages: List<OverlayLanguage>,
    onClose: () -> Unit,
    onTranslate: () -> Unit,
    onCopyText: () -> Unit,
    onOpenInApp: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onSelectLanguage: (OverlayLanguage) -> Unit,
    onCopyTranslation: () -> Unit,
    onToggleSpeech: () -> Unit,
    onShare: () -> Unit,
    onShowOnImageChange: (Boolean) -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
    editor: @Composable (PaddingValues) -> Unit,
    controls: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val motion = MaterialTheme.motionScheme
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    var bottomChromeHeight by remember { mutableIntStateOf(0) }
    val editorPadding = PaddingValues(
        start = safe.calculateStartPadding(layoutDirection) + 8.dp,
        top = safe.calculateTopPadding() + TopBarHeight,
        end = safe.calculateEndPadding(layoutDirection) + 8.dp,
        bottom = with(density) { bottomChromeHeight.toDp() } + 8.dp,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.text) }
    LaunchedEffect(state.message?.id) {
        if (messageText != null) snackbarHostState.showSnackbar(messageText)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim)
    ) {
        editor(editorPadding)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EditorTopBar(target = state.target, onClose = onClose, onOpenPicker = onOpenPicker)
            EditorStatusChip(state = state)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomChromeHeight = it.height }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = state.result,
                transitionSpec = {
                    (slideInVertically(motion.defaultSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                contentKey = { it != null },
                label = "editorBottom",
            ) { result ->
                if (result == null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        controls()
                        EditorActionBar(
                            state = state,
                            onTranslate = onTranslate,
                            onCopyText = onCopyText,
                            onOpenInApp = onOpenInApp,
                        )
                    }
                } else {
                    TranslationResultCard(
                        result = result,
                        showOnImage = state.showOnImage,
                        translatingBlocks = state.translatingBlocks,
                        retranslating = state.busy == EditorBusy.TRANSLATING || state.busy == EditorBusy.READING,
                        speaking = state.speaking,
                        opening = state.busy == EditorBusy.OPENING,
                        onCopy = onCopyTranslation,
                        onToggleSpeech = onToggleSpeech,
                        onShare = onShare,
                        onOpenInApp = onOpenInApp,
                        onShowOnImageChange = onShowOnImageChange,
                        onDismiss = onDismissResult,
                    )
                }
            }
        }

        // Outside the measured bottom chrome so a snackbar doesn't resize the image.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = with(density) { bottomChromeHeight.toDp() }),
        )

        AnimatedVisibility(
            visible = state.pickerOpen,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .clickable(interactionSource = null, indication = null, onClick = onClosePicker)
            )
        }
        AnimatedVisibility(
            visible = state.pickerOpen,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motion.defaultSpatialSpec()) { it },
            exit = slideOutVertically(motion.fastSpatialSpec()) { it },
        ) {
            OverlayLanguagePicker(
                languages = languages,
                recent = state.recent,
                selected = state.target,
                onSelect = onSelectLanguage,
                onDismiss = onClosePicker,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f),
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    target: OverlayLanguage,
    onClose: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TopBarHeight)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilledTonalIconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.overlay_editor_close))
        }
        Spacer(Modifier.weight(1f))
        val description = stringResource(R.string.overlay_editor_target_language, target.name)
        Surface(
            onClick = onOpenPicker,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description },
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = target.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 160.dp),
                )
                Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private enum class ChipKind { PREPARING, FINDING, HINT, NO_TEXT, FAILED }

/** A small pill under the top bar: OCR progress, the tap hint, or "no text found". */
@Composable
private fun EditorStatusChip(state: CaptureEditorUiState) {
    val kind = when {
        state.result != null -> null
        state.preparingOcr -> ChipKind.PREPARING
        state.ocr == OcrPhase.RUNNING -> ChipKind.FINDING
        state.showTapHint -> ChipKind.HINT
        state.ocr == OcrPhase.NONE -> ChipKind.NO_TEXT
        state.ocr == OcrPhase.FAILED -> ChipKind.FAILED
        else -> null
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = kind,
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f))
                .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f))
        },
        label = "statusChip",
    ) { current ->
        if (current == null) {
            Spacer(Modifier.height(0.dp))
        } else {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shadowElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (current) {
                            ChipKind.PREPARING, ChipKind.FINDING ->
                                LoadingIndicator(Modifier.size(20.dp))
                            ChipKind.HINT ->
                                Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                            ChipKind.NO_TEXT, ChipKind.FAILED -> Unit
                        }
                        Text(
                            text = stringResource(
                                when (current) {
                                    ChipKind.PREPARING -> R.string.overlay_editor_preparing
                                    ChipKind.FINDING -> R.string.overlay_editor_finding_text
                                    ChipKind.HINT -> R.string.overlay_editor_tap_text_hint
                                    ChipKind.NO_TEXT -> R.string.overlay_editor_no_text_found
                                    ChipKind.FAILED -> R.string.overlay_editor_ocr_failed
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    val progress = state.setupProgress
                    if (current == ChipKind.PREPARING && progress != null) {
                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .width(160.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Translate (primary), copy text and open in the app. */
@Composable
private fun EditorActionBar(
    state: CaptureEditorUiState,
    onTranslate: () -> Unit,
    onCopyText: () -> Unit,
    onOpenInApp: () -> Unit,
) {
    val working = state.busy == EditorBusy.READING || state.busy == EditorBusy.TRANSLATING
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 4.dp,
        modifier = Modifier.widthIn(max = 560.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onTranslate,
                    enabled = state.busy == null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                ) {
                    if (working) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = LocalContentColor.current,
                        )
                    } else {
                        Icon(Icons.Rounded.Translate, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(busyLabel(state) ?: R.string.overlay_editor_translate),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalIconButton(
                    onClick = onCopyText,
                    enabled = state.busy == null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.overlay_editor_copy_text))
                }
                FilledTonalIconButton(
                    onClick = onOpenInApp,
                    enabled = state.busy == null,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.overlay_editor_open_app))
                }
            }
            val progress = state.setupProgress
            if (state.status == AnalysisStatus.PREPARING && progress != null) {
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
            if (state.busy == EditorBusy.TRANSLATING && state.status == AnalysisStatus.DOWNLOADING_MODEL) {
                // Real progress (and why it's waiting) for the one-time language download.
                ModelDownloadInfo(
                    languageName = null,
                    progress = state.modelDownload,
                    onCancel = null,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@StringRes
private fun busyLabel(state: CaptureEditorUiState): Int? = when (state.busy) {
    null -> null
    EditorBusy.READING, EditorBusy.COPYING -> R.string.overlay_status_reading
    EditorBusy.OPENING -> R.string.overlay_status_opening
    EditorBusy.TRANSLATING -> when (state.status) {
        AnalysisStatus.PREPARING -> R.string.overlay_status_preparing
        AnalysisStatus.IDENTIFYING -> R.string.overlay_status_identifying
        AnalysisStatus.DOWNLOADING_MODEL -> R.string.overlay_status_downloading
        AnalysisStatus.ANALYZING -> R.string.overlay_status_finishing
        else -> R.string.overlay_status_translating
    }
}

/**
 * The translation, docked at the bottom over the current app: languages, the text (hidden while
 * it is shown on the image), "Show on image", and Copy / Listen / Share / Open in TransVerse.
 */
@Composable
fun TranslationResultCard(
    result: OverlayTranslation,
    showOnImage: Boolean,
    translatingBlocks: Boolean,
    retranslating: Boolean,
    speaking: Boolean,
    opening: Boolean,
    onCopy: () -> Unit,
    onToggleSpeech: () -> Unit,
    onShare: () -> Unit,
    onOpenInApp: () -> Unit,
    onShowOnImageChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(end = 8.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.overlay_result_direction, result.sourceLanguageName, result.targetLanguageName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.overlay_result_close))
                }
            }
            if (retranslating) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            AnimatedVisibility(
                visible = !showOnImage,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Text(
                    text = result.translatedText,
                    style = readingTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(end = 8.dp, top = 4.dp, bottom = 8.dp)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(end = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .toggleable(value = showOnImage, role = Role.Switch, onValueChange = onShowOnImageChange),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = stringResource(R.string.overlay_result_show_on_image),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                if (translatingBlocks) LoadingIndicator(Modifier.size(24.dp))
                Switch(checked = showOnImage, onCheckedChange = null)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ResultAction(Icons.Rounded.ContentCopy, R.string.overlay_result_copy, onCopy, Modifier.weight(1f))
                ResultAction(
                    icon = if (speaking) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                    label = if (speaking) R.string.overlay_result_stop else R.string.overlay_result_listen,
                    onClick = onToggleSpeech,
                    modifier = Modifier.weight(1f),
                )
                ResultAction(Icons.Rounded.Share, R.string.overlay_result_share, onShare, Modifier.weight(1f))
            }
            OutlinedButton(
                onClick = onOpenInApp,
                enabled = !opening,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp, top = 8.dp)
                    .heightIn(min = 48.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.overlay_editor_open_app))
            }
        }
    }
}

@Composable
private fun ResultAction(
    icon: ImageVector,
    @StringRes label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Searchable target-language list drawn inside the overlay (a real bottom sheet would need a
 * dialog window, which an overlay can't host).
 */
@Composable
fun OverlayLanguagePicker(
    languages: List<OverlayLanguage>,
    recent: List<OverlayLanguage>,
    selected: OverlayLanguage,
    onSelect: (OverlayLanguage) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(languages, query) {
        val needle = query.trim()
        if (needle.isEmpty()) languages
        else languages.filter { it.name.contains(needle, ignoreCase = true) || it.code.equals(needle, ignoreCase = true) }
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .imePadding()
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.overlay_language_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.overlay_language_close))
                }
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.overlay_language_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.overlay_language_clear_search))
                        }
                    }
                },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                if (query.isBlank() && recent.isNotEmpty()) {
                    item(key = "recent_header") { PickerHeader(R.string.overlay_language_recent) }
                    items(recent, key = { "recent_${it.code}" }) { language ->
                        LanguageRow(language, language.code == selected.code, onSelect)
                    }
                    item(key = "all_header") { PickerHeader(R.string.overlay_language_all) }
                }
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.overlay_language_no_match),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                }
                items(filtered, key = { it.code }) { language ->
                    LanguageRow(language, language.code == selected.code, onSelect)
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun LanguageRow(
    language: OverlayLanguage,
    isSelected: Boolean,
    onSelect: (OverlayLanguage) -> Unit,
) {
    ListItem(
        headlineContent = { Text(language.name) },
        trailingContent = if (isSelected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .semantics { selected = isSelected }
            .clickable { onSelect(language) }
            .padding(horizontal = 8.dp),
    )
}

// region Previews

private val PreviewResult = OverlayTranslation(
    sourceLanguageName = "French",
    targetLanguageName = "English",
    translatedText = "The museum is open every day from nine in the morning until six in the evening.",
)

private val PreviewLanguages = listOf("de", "en", "es", "fr", "ja").map { OverlayLanguage(it, it.uppercase()) }

@Composable
private fun PreviewEditor(state: CaptureEditorUiState) {
    CaptureEditorContent(
        state = state,
        languages = PreviewLanguages,
        onClose = {},
        onTranslate = {},
        onCopyText = {},
        onOpenInApp = {},
        onOpenPicker = {},
        onClosePicker = {},
        onSelectLanguage = {},
        onCopyTranslation = {},
        onToggleSpeech = {},
        onShare = {},
        onShowOnImageChange = {},
        onDismissResult = {},
        editor = { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        controls = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Spacer(Modifier.size(width = 240.dp, height = 56.dp))
            }
        },
    )
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun CaptureEditorFindingPreview() {
    TransVerseTheme {
        PreviewEditor(CaptureEditorUiState(target = OverlayLanguage("en", "English")))
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun CaptureEditorTranslatingPreview() {
    TransVerseTheme {
        PreviewEditor(
            CaptureEditorUiState(
                ocr = OcrPhase.FOUND,
                showTapHint = true,
                busy = EditorBusy.TRANSLATING,
                status = AnalysisStatus.DOWNLOADING_MODEL,
                target = OverlayLanguage("en", "English"),
            )
        )
    }
}

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun CaptureEditorResultPreview() {
    TransVerseTheme {
        PreviewEditor(
            CaptureEditorUiState(
                ocr = OcrPhase.FOUND,
                target = OverlayLanguage("en", "English"),
                result = PreviewResult,
            )
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun TranslationResultCardPreview() {
    TransVerseTheme {
        TranslationResultCard(
            result = PreviewResult,
            showOnImage = false,
            translatingBlocks = false,
            retranslating = false,
            speaking = true,
            opening = false,
            onCopy = {},
            onToggleSpeech = {},
            onShare = {},
            onOpenInApp = {},
            onShowOnImageChange = {},
            onDismiss = {},
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 560)
@Composable
private fun OverlayLanguagePickerPreview() {
    TransVerseTheme {
        OverlayLanguagePicker(
            languages = PreviewLanguages,
            recent = PreviewLanguages.take(2),
            selected = PreviewLanguages[1],
            onSelect = {},
            onDismiss = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// endregion
