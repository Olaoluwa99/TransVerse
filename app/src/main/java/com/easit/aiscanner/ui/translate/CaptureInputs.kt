package com.easit.aiscanner.ui.translate

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.easit.aiscanner.R
import com.easit.aiscanner.scannerAI.AnalysisStatus
import com.easit.aiscanner.scannerAI.DownloadWaitReason
import com.easit.aiscanner.scannerAI.ModelDownloadProgress
import com.easit.aiscanner.ui.theme.readingTextStyle

private val CaptureMode.label: Int
    get() = when (this) {
        CaptureMode.TEXT -> R.string.translate_mode_text
        CaptureMode.IMAGE -> R.string.translate_mode_image
        CaptureMode.VOICE -> R.string.translate_mode_voice
    }

private val CaptureMode.icon: ImageVector
    get() = when (this) {
        CaptureMode.TEXT -> Icons.Rounded.TextFields
        CaptureMode.IMAGE -> Icons.Rounded.Image
        CaptureMode.VOICE -> Icons.Rounded.Mic
    }

/** Connected button group choosing how text is captured. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CaptureModeSelector(
    selected: CaptureMode,
    onSelect: (CaptureMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = CaptureMode.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            ToggleButton(
                checked = mode == selected,
                onCheckedChange = { onSelect(mode) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                icon = { Icon(imageVector = mode.icon, contentDescription = null) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(stringResource(mode.label))
            }
        }
    }
}

/** The card holding the capture controls for [mode] and the editable transcript. */
@Composable
internal fun CaptureCard(
    mode: CaptureMode,
    transcript: String,
    imagePreview: String?,
    readingImage: Boolean,
    onAction: (TranslateAction) -> Unit,
    modifier: Modifier = Modifier,
    imageSetupProgress: Float? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (mode) {
                CaptureMode.TEXT -> Unit
                CaptureMode.IMAGE -> ImageCapture(
                    imagePreview = imagePreview,
                    readingImage = readingImage,
                    imageSetupProgress = imageSetupProgress,
                    onCamera = { onAction(TranslateAction.PickFromCamera) },
                    onGallery = { onAction(TranslateAction.PickFromGallery) },
                    onScanDocument = { onAction(TranslateAction.ScanDocument) },
                )
                CaptureMode.VOICE -> VoiceCapture(
                    hasTranscript = transcript.isNotBlank(),
                    onSpeak = { onAction(TranslateAction.StartVoiceInput) },
                )
            }

            // Image and voice captures show their transcript once there is one.
            val showField = mode == CaptureMode.TEXT || transcript.isNotEmpty()
            AnimatedVisibility(visible = showField, enter = fadeIn(), exit = fadeOut()) {
                TranscriptField(
                    mode = mode,
                    transcript = transcript,
                    onTranscriptChange = { onAction(TranslateAction.TranscriptChanged(it)) },
                    onClear = { onAction(TranslateAction.ClearTranscript) },
                )
            }

            if (mode == CaptureMode.TEXT) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onAction(TranslateAction.PasteFromClipboard) }) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.translate_action_paste))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptField(
    mode: CaptureMode,
    transcript: String,
    onTranscriptChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val labelRes = when (mode) {
        CaptureMode.TEXT -> null
        CaptureMode.IMAGE -> R.string.translate_image_transcript_label
        CaptureMode.VOICE -> R.string.translate_voice_transcript_label
    }
    TextField(
        value = transcript,
        onValueChange = onTranscriptChange,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (mode == CaptureMode.TEXT) 160.dp else 96.dp),
        textStyle = readingTextStyle(),
        label = if (labelRes != null) {
            { Text(stringResource(labelRes)) }
        } else {
            null
        },
        placeholder = if (mode == CaptureMode.TEXT) {
            { Text(stringResource(R.string.translate_input_hint)) }
        } else {
            null
        },
        trailingIcon = if (transcript.isNotEmpty()) {
            {
                IconButton(onClick = onClear) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.translate_action_clear))
                }
            }
        } else {
            null
        },
        shape = MaterialTheme.shapes.large,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageCapture(
    imagePreview: String?,
    readingImage: Boolean,
    imageSetupProgress: Float?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onScanDocument: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            if (imagePreview != null) {
                AsyncImage(
                    model = Uri.parse(imagePreview),
                    contentDescription = stringResource(R.string.translate_image_preview_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.matchParentSize()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Surface(
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(32.dp))
                            }
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.translate_image_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.translate_image_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            if (readingImage) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
                ) {
                    if (imageSetupProgress != null) {
                        // First use: Play services is still downloading text recognition.
                        SetupProgress(
                            label = stringResource(R.string.translate_preparing_text_recognition),
                            progress = imageSetupProgress,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LoadingIndicator(Modifier.size(32.dp))
                            Text(stringResource(R.string.translate_reading_image), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onCamera, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.translate_action_camera))
            }
            FilledTonalButton(onClick = onGallery, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.translate_action_gallery))
            }
        }
        // Pages are found, flattened and cleaned up by the scanner, so they skip the crop step.
        FilledTonalButton(
            onClick = onScanDocument,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        ) {
            Icon(Icons.Rounded.DocumentScanner, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.translate_action_scan_document))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun VoiceCapture(hasTranscript: Boolean, onSpeak: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediumFloatingActionButton(
            onClick = onSpeak,
            shape = MaterialShapes.Cookie9Sided.toShape(),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
            modifier = Modifier.size(96.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = stringResource(R.string.translate_action_speak),
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            text = stringResource(if (hasTranscript) R.string.translate_voice_again else R.string.translate_voice_prompt),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Source (detected) language, swap, and the target language button. */
@Composable
internal fun LanguageBar(
    detectedLanguage: String?,
    target: LanguageOption,
    canSwap: Boolean,
    onSwap: () -> Unit,
    onChooseTarget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (detectedLanguage == null) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text(
                        text = stringResource(
                            if (detectedLanguage == null) R.string.translate_source_label else R.string.translate_detected
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = detectedLanguage ?: stringResource(R.string.translate_detect_language),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
        }
        IconButton(onClick = onSwap, enabled = canSwap) {
            Icon(Icons.Rounded.SwapHoriz, contentDescription = stringResource(R.string.translate_action_swap))
        }
        val chooseDescription = stringResource(R.string.translate_action_choose_language, target.name)
        FilledTonalButton(
            onClick = onChooseTarget,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { this.contentDescription = chooseDescription },
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasEndIcon = true),
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(R.string.translate_target_label),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(text = target.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
    }
}

/** The primary Translate action, replaced by progress while an analysis runs. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TranslateButton(
    status: AnalysisStatus,
    running: Boolean,
    enabled: Boolean,
    onTranslate: () -> Unit,
    modifier: Modifier = Modifier,
    setupProgress: Float? = null,
    detectedLanguage: String? = null,
    modelDownload: ModelDownloadProgress? = null,
    onCancel: (() -> Unit)? = null,
) {
    val size = ButtonDefaults.MediumContainerHeight
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onTranslate,
            enabled = enabled && !running,
            shapes = ButtonDefaults.shapesFor(size),
            contentPadding = ButtonDefaults.contentPaddingFor(size, hasStartIcon = true),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .heightIn(min = size),
        ) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
            )
            Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
            Text(
                text = stringResource(R.string.translate_action_translate),
                style = ButtonDefaults.textStyleFor(size),
            )
        }
        val progress = status.progressLabel()
        AnimatedVisibility(visible = running && progress != null) {
            if (status == AnalysisStatus.PREPARING) {
                // First use: real download progress for Play services' language detection.
                SetupProgress(
                    label = stringResource(R.string.translate_status_preparing),
                    progress = setupProgress,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            } else if (status == AnalysisStatus.DOWNLOADING_MODEL) {
                ModelDownloadInfo(
                    languageName = detectedLanguage,
                    progress = modelDownload,
                    onCancel = onCancel,
                    modifier = Modifier.widthIn(max = 480.dp),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LoadingIndicator(Modifier.size(40.dp))
                    val label = when {
                        status == AnalysisStatus.DOWNLOADING_MODEL && detectedLanguage != null ->
                            stringResource(R.string.translate_status_downloading_named, detectedLanguage)
                        progress != null -> stringResource(progress)
                        else -> null
                    }
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A language model downloading for offline use: which language, real progress from Android's
 * DownloadManager ("12 MB of 31 MB"), why it is waiting if Android has paused it, and Cancel.
 * Shared with the floating-capture overlay.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ModelDownloadInfo(
    languageName: String?,
    progress: ModelDownloadProgress?,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (languageName != null) {
                stringResource(R.string.translate_status_downloading_named, languageName)
            } else {
                stringResource(R.string.translate_status_downloading)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val fraction = progress?.fraction
        val waiting: DownloadWaitReason? = progress?.waitingFor
        if (fraction != null && waiting == null) {
            LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val detail = when {
            progress == null -> null
            waiting != null -> stringResource(
                when (waiting) {
                    DownloadWaitReason.NETWORK -> R.string.translate_download_waiting_network
                    DownloadWaitReason.WIFI -> R.string.translate_download_waiting_wifi
                    DownloadWaitReason.RETRYING -> R.string.translate_download_retrying
                    DownloadWaitReason.OTHER -> R.string.translate_download_paused
                }
            )
            progress.totalBytes > 0 -> stringResource(
                R.string.translate_download_bytes,
                Formatter.formatShortFileSize(context, progress.bytesDownloaded),
                Formatter.formatShortFileSize(context, progress.totalBytes),
            )
            progress.bytesDownloaded > 0 -> stringResource(
                R.string.translate_download_bytes_unknown_total,
                Formatter.formatShortFileSize(context, progress.bytesDownloaded),
            )
            else -> null
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (waiting != null) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.translate_action_cancel_download)) }
        }
    }
}

/**
 * A one-time setup step (an ML Kit module downloading through Play services): a label over a
 * wavy progress bar, determinate once the download size is known.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SetupProgress(label: String, progress: Float?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (progress != null) {
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
