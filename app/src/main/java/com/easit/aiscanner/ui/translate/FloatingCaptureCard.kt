package com.easit.aiscanner.ui.translate

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme

/**
 * Promotes floating capture, the app's headline feature, at the top of the Translate screen:
 * an invitation to start it, or its "on" state with a way to turn it off.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FloatingCaptureCard(
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    // Read in composition; the transitionSpec lambda below isn't composable.
    val fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (running) colors.secondaryContainer else colors.tertiaryContainer,
            contentColor = if (running) colors.onSecondaryContainer else colors.onTertiaryContainer,
        ),
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = { fadeIn(fadeInSpec) togetherWith fadeOut(fadeOutSpec) },
            label = "floatingCaptureCard",
        ) { isRunning ->
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    FloatingCaptureBadge(running = isRunning)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (isRunning) R.string.translate_floating_running_title
                                else R.string.translate_floating_title,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = stringResource(
                                if (isRunning) R.string.translate_floating_running_body
                                else R.string.translate_floating_body,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (isRunning) {
                    OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.translate_floating_stop))
                    }
                } else {
                    val size = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = onStart,
                        shapes = ButtonDefaults.shapesFor(size),
                        contentPadding = ButtonDefaults.contentPaddingFor(size, hasStartIcon = true),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.tertiary,
                            contentColor = colors.onTertiary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Rounded.PictureInPictureAlt,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(size)),
                        )
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(size)))
                        Text(
                            text = stringResource(R.string.translate_floating_start),
                            style = ButtonDefaults.textStyleFor(size),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The feature's emblem in an expressive cookie shape. While the feature is off it turns slowly
 * to draw the eye; once on, it rests.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingCaptureBadge(running: Boolean) {
    val colors = MaterialTheme.colorScheme
    val rotation = if (running) {
        0f
    } else {
        val spin by rememberInfiniteTransition(label = "floatingCaptureBadge").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(BADGE_TURN_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )
        spin
    }
    Box(
        modifier = Modifier.size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MaterialShapes.Cookie9Sided.toShape())
                .background(if (running) colors.secondary else colors.tertiary),
        )
        Icon(
            imageVector = Icons.Rounded.PictureInPictureAlt,
            contentDescription = null,
            tint = if (running) colors.onSecondary else colors.onTertiary,
            modifier = Modifier.size(30.dp),
        )
    }
}

private const val BADGE_TURN_MS = 12_000

@Preview(name = "Floating capture – off")
@Composable
private fun FloatingCaptureCardOffPreview() {
    TransVerseTheme { FloatingCaptureCard(running = false, onStart = {}, onStop = {}) }
}

@Preview(name = "Floating capture – on")
@Composable
private fun FloatingCaptureCardOnPreview() {
    TransVerseTheme { FloatingCaptureCard(running = true, onStart = {}, onStop = {}) }
}
