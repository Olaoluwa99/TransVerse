package com.easit.aiscanner.ui.translate

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme
import com.easit.aiscanner.ui.theme.readingTextStyle
import com.easit.aiscanner.utils.EntityActions
import java.util.Locale

/** The translation with its actions, spoken playback, useful details and suggested replies. */
@Composable
internal fun ResultCard(
    result: TranslationResult,
    playback: PlaybackState,
    onAction: (TranslateAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = result.targetLanguageName,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SelectionContainer {
                Text(
                    text = result.translatedText,
                    style = readingTextStyle(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            ResultActions(playback = playback, onAction = onAction)

            AnimatedVisibility(visible = playback.available || playback.preparing) {
                SpeechPlayer(
                    playback = playback,
                    onTogglePlay = { onAction(TranslateAction.ToggleListen) },
                    onSeek = { onAction(TranslateAction.SeekAudio(it)) },
                )
            }

            if (result.entities.isNotEmpty()) {
                EntityChips(
                    entities = result.entities,
                    onEntity = { onAction(TranslateAction.OpenEntity(it)) },
                )
            }
            if (result.smartReplies.isNotEmpty()) {
                ReplyChips(
                    replies = result.smartReplies,
                    onReply = { onAction(TranslateAction.CopyReply(it)) },
                )
            }
        }
    }
}

@Composable
private fun ResultActions(
    playback: PlaybackState,
    onAction: (TranslateAction) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        ListenButton(playback = playback, onClick = { onAction(TranslateAction.ToggleListen) })
        Spacer(Modifier.width(4.dp))
        ActionIcon(
            icon = Icons.Rounded.ContentCopy,
            label = stringResource(R.string.translate_action_copy),
            onClick = { onAction(TranslateAction.CopyTranslation) },
        )
        ActionIcon(
            icon = Icons.Rounded.Share,
            label = stringResource(R.string.translate_action_share),
            onClick = { onAction(TranslateAction.ShareTranslation) },
        )
        if (playback.available) {
            ActionIcon(
                icon = Icons.Rounded.AudioFile,
                label = stringResource(R.string.translate_action_share_audio),
                onClick = { onAction(TranslateAction.ShareAudio) },
            )
            ActionIcon(
                icon = Icons.Rounded.Download,
                label = stringResource(R.string.translate_action_save_audio),
                onClick = { onAction(TranslateAction.SaveAudio) },
            )
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ListenButton(playback: PlaybackState, onClick: () -> Unit) {
    val label = when {
        playback.preparing -> R.string.translate_preparing_audio
        playback.playing -> R.string.translate_action_pause
        else -> R.string.translate_action_listen
    }
    FilledTonalButton(
        onClick = onClick,
        enabled = !playback.preparing,
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
    ) {
        if (playback.preparing) {
            LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
        } else {
            Icon(
                imageVector = if (playback.playing) Icons.Rounded.Pause else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label))
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = label)
    }
}

/** Play/pause with a seekable position. */
@Composable
internal fun SpeechPlayer(
    playback: PlaybackState,
    onTogglePlay: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seekDescription = stringResource(R.string.translate_audio_position)
    // Holds the thumb while dragging so progress updates don't fight the finger.
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val duration = playback.durationMs.coerceAtLeast(0)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledIconButton(onClick = onTogglePlay, enabled = playback.available) {
            Icon(
                imageVector = if (playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(
                    if (playback.playing) R.string.translate_action_pause_audio
                    else R.string.translate_action_play_audio
                ),
            )
        }
        Column(Modifier.weight(1f)) {
            Slider(
                value = dragPosition ?: playback.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = { dragPosition = it },
                onValueChangeFinished = {
                    dragPosition?.let { onSeek(it.toInt()) }
                    dragPosition = null
                },
                enabled = duration > 0,
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                modifier = Modifier.semantics { contentDescription = seekDescription },
            )
            Text(
                text = stringResource(
                    R.string.translate_audio_time,
                    formatTime(dragPosition?.toInt() ?: playback.positionMs),
                    formatTime(duration),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Extracted details (links, emails, phone numbers…) that open in the right app. */
@Composable
internal fun EntityChips(entities: List<String>, onEntity: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.translate_entities_heading))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            entities.forEach { entity ->
                AssistChip(
                    onClick = { onEntity(entity) },
                    label = { Text(entity) },
                    leadingIcon = {
                        Icon(
                            imageVector = entityIcon(entity),
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

private fun entityIcon(entity: String): ImageVector = when (EntityActions.kindOf(entity)) {
    EntityActions.Kind.LINK -> Icons.Rounded.Link
    EntityActions.Kind.EMAIL -> Icons.Rounded.Email
    EntityActions.Kind.PHONE -> Icons.Rounded.Phone
    EntityActions.Kind.OTHER -> Icons.Rounded.Sell
}

/** Suggested replies; tapping one copies it. */
@Composable
internal fun ReplyChips(replies: List<String>, onReply: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(stringResource(R.string.translate_replies_heading))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            replies.forEach { reply ->
                SuggestionChip(
                    onClick = { onReply(reply) },
                    label = { Text(reply) },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(SuggestionChipDefaults.IconSize),
                        )
                    },
                )
            }
        }
    }
}

internal val PreviewResult = TranslationResult(
    scanId = "20260101120000000",
    sourceText = "Bonjour, appelez-moi au +33 1 23 45 67 89 ou visitez example.com",
    sourceLanguageCode = "fr",
    sourceLanguageName = "French",
    targetLanguageCode = "en",
    targetLanguageName = "English",
    translatedText = "Hello, call me at +33 1 23 45 67 89 or visit example.com",
    entities = listOf("+33 1 23 45 67 89", "example.com"),
    smartReplies = listOf("Sure!", "Thanks", "Will do"),
)

@Preview
@Composable
private fun ResultCardPreview() {
    TransVerseTheme {
        ResultCard(
            result = PreviewResult,
            playback = PlaybackState(available = true, playing = true, positionMs = 2_000, durationMs = 6_000),
            onAction = {},
        )
    }
}
