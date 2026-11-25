@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.easit.aiscanner.ui.overlay

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Default bubble diameter before the user's bubble-size preference is applied. */
val BubbleBaseSize: Dp = 56.dp

/** Transparent margin around the bubble inside its window, so the shadow isn't clipped. */
val BubbleWindowPadding: Dp = 8.dp

/** Bottom margin (above the navigation bar) of the close target's slot. */
val CloseTargetBottomMargin: Dp = 32.dp

/** Side of the square slot the close target sits in; its centre is the drop point. */
val CloseTargetSlot: Dp = 80.dp

/**
 * State of the floating bubble shared by its three overlay windows (bubble, menu, close
 * target). Written by the service on the main thread; read by the composables.
 */
@Stable
class BubbleUiState {
    /** A finger is down on the bubble. */
    var pressed by mutableStateOf(false)

    /** Parked half off-screen at an edge after a few idle seconds. */
    var docked by mutableStateOf(false)

    /** The bubble is being dragged (the close target is showing). */
    var dragging by mutableStateOf(false)

    /** While dragging: the bubble is over the close target. */
    var overCloseTarget by mutableStateOf(false)

    /** The fan-out menu is open. */
    var menuExpanded by mutableStateOf(false)

    /** Top-left of the bubble window on screen, in pixels, for the menu window to align with. */
    var bubbleWindowPosition by mutableStateOf(IntOffset.Zero)
}

/**
 * The bubble itself: a cookie-shaped primary container with the translate icon. When [expanded]
 * the shape turns and the icon becomes a close mark.
 */
@Composable
fun FloatingBubble(
    size: Dp,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    docked: Boolean = false,
    pressed: Boolean = false,
) {
    val motion = MaterialTheme.motionScheme
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, motion.fastSpatialSpec(), label = "bubbleScale")
    val alpha by animateFloatAsState(if (docked) 0.75f else 1f, motion.defaultEffectsSpec(), label = "bubbleAlpha")
    val turn by animateFloatAsState(if (expanded) 90f else 0f, motion.defaultSpatialSpec(), label = "bubbleTurn")
    val shape = MaterialShapes.Cookie9Sided.toShape()
    val container = if (expanded) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val content = if (expanded) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = turn }
                .shadow(elevation = 6.dp, shape = shape)
                .background(container, shape)
        )
        Crossfade(targetState = expanded, animationSpec = motion.fastEffectsSpec(), label = "bubbleIcon") { isExpanded ->
            Icon(
                imageVector = if (isExpanded) Icons.Rounded.Close else Icons.Rounded.Translate,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/**
 * Content of the bubble's own small window: the bubble inside a transparent margin. Every touch
 * goes to [onTouch] with raw screen coordinates (the window moves under the finger, so local
 * coordinates are useless for dragging). Accessibility services get a plain click instead.
 */
@Composable
fun BubbleWindowContent(
    state: BubbleUiState,
    bubbleSize: Dp,
    onTouch: (MotionEvent) -> Boolean,
    onAccessibilityClick: () -> Unit,
) {
    val description = stringResource(R.string.overlay_bubble_description)
    val openMenu = stringResource(R.string.overlay_bubble_open_menu)
    Box(
        modifier = Modifier
            .size(bubbleSize + BubbleWindowPadding * 2)
            .pointerInteropFilter(onTouchEvent = onTouch)
            .semantics {
                contentDescription = description
                role = Role.Button
                onClick(label = openMenu) {
                    onAccessibilityClick()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        FloatingBubble(
            size = bubbleSize,
            docked = state.docked,
            pressed = state.pressed,
        )
    }
}

/** One action of the fan-out menu. */
data class BubbleMenuAction(
    val icon: ImageVector,
    val label: String,
    val emphasized: Boolean,
    val onClick: () -> Unit,
)

/**
 * Content of the full-screen menu window: a light scrim (tap to close), a copy of the bubble in
 * its "close" form exactly over the real one, and the actions fanned out towards the centre of
 * the screen. Animates in when shown with [BubbleUiState.menuExpanded] true and out when it turns
 * false, then calls [onCollapsed] so the window can be removed.
 */
@Composable
fun BubbleMenuContent(
    state: BubbleUiState,
    bubbleSize: Dp,
    actions: List<BubbleMenuAction>,
    onDismiss: () -> Unit,
    onCollapsed: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val progress = remember { Animatable(0f) }
    val currentOnCollapsed by rememberUpdatedState(onCollapsed)
    LaunchedEffect(state.menuExpanded) {
        if (state.menuExpanded) {
            // The window is new or was removed mid-animation: always fan out from the bubble.
            progress.snapTo(0f)
            progress.animateTo(1f, motion.fastSpatialSpec())
        } else {
            progress.animateTo(0f, motion.fastEffectsSpec())
            currentOnCollapsed()
        }
    }

    val density = LocalDensity.current
    val windowPx = with(density) { (bubbleSize + BubbleWindowPadding * 2).roundToPx() }
    val radiusPx = with(density) { (bubbleSize / 2 + 64.dp).toPx() }
    val buttonRadiusPx = with(density) { (MenuButtonSize / 2).toPx() }
    val marginPx = with(density) { 12.dp.roundToPx() }
    val closeDescription = stringResource(R.string.overlay_bubble_close_menu)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
                .clickable(interactionSource = null, indication = null, onClick = onDismiss)
        )

        val anchor = state.bubbleWindowPosition
        val screenWidthPx = constraints.maxWidth
        val onLeft = anchor.x + windowPx / 2f < screenWidthPx / 2f
        Box(
            modifier = Modifier
                .offset { anchor }
                .size(bubbleSize + BubbleWindowPadding * 2)
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClickLabel = closeDescription,
                    role = Role.Button,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            FloatingBubble(size = bubbleSize, expanded = state.menuExpanded)
        }

        Layout(
            content = {
                // The label goes on the side away from the screen edge.
                actions.forEach { action ->
                    MenuActionItem(action = action, labelOnStart = !onLeft)
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, layoutConstraints ->
            val width = layoutConstraints.maxWidth
            val height = layoutConstraints.maxHeight
            val centerX = anchor.x + windowPx / 2f
            val centerY = anchor.y + windowPx / 2f
            // Fan towards the middle of the screen, but keep the spread mostly horizontal.
            val towardsCentre = atan2(height / 2f - centerY, width / 2f - centerX)
            val base = if (onLeft) {
                towardsCentre.coerceIn(-MAX_TILT, MAX_TILT)
            } else {
                val mirrored = if (towardsCentre >= 0f) PI.toFloat() - towardsCentre else -PI.toFloat() - towardsCentre
                PI.toFloat() - mirrored.coerceIn(-MAX_TILT, MAX_TILT)
            }
            val count = measurables.size
            val placeables = measurables.map { it.measure(layoutConstraints.copy(minWidth = 0, minHeight = 0)) }
            val fraction = progress.value
            layout(width, height) {
                placeables.forEachIndexed { index, placeable ->
                    // First action on top on either side.
                    val step = (index - (count - 1) / 2f) * SPREAD
                    val angle = if (onLeft) base + step else base - step
                    val targetX = centerX + radiusPx * cos(angle) * fraction
                    val targetY = centerY + radiusPx * sin(angle) * fraction
                    var left = if (onLeft) targetX - buttonRadiusPx else targetX + buttonRadiusPx - placeable.width
                    var top = targetY - placeable.height / 2f
                    left = left.coerceIn(marginPx.toFloat(), (width - placeable.width - marginPx).toFloat().coerceAtLeast(marginPx.toFloat()))
                    top = top.coerceIn(marginPx.toFloat(), (height - placeable.height - marginPx).toFloat().coerceAtLeast(marginPx.toFloat()))
                    placeable.placeWithLayer(left.roundToInt(), top.roundToInt()) {
                        val visible = fraction.coerceIn(0f, 1f)
                        alpha = visible
                        scaleX = 0.4f + 0.6f * visible
                        scaleY = 0.4f + 0.6f * visible
                    }
                }
            }
        }
    }
}

private val MenuButtonSize = 52.dp
private val SPREAD = (52.0 / 180.0 * PI).toFloat()
private val MAX_TILT = (35.0 / 180.0 * PI).toFloat()

@Composable
private fun MenuActionItem(action: BubbleMenuAction, labelOnStart: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container = if (action.emphasized) colors.primary else colors.secondaryContainer
    val content = if (action.emphasized) colors.onPrimary else colors.onSecondaryContainer
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val label: @Composable () -> Unit = {
            Surface(
                onClick = action.onClick,
                shape = CircleShape,
                color = colors.surfaceContainerHighest,
                contentColor = colors.onSurface,
                shadowElevation = 2.dp,
            ) {
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (labelOnStart) label()
        Surface(
            onClick = action.onClick,
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = 4.dp,
            modifier = Modifier
                .size(MenuButtonSize)
                .semantics { contentDescription = action.label },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(action.icon, contentDescription = null)
            }
        }
        if (!labelOnStart) label()
    }
}

/**
 * Content of the close-target window at the bottom of the screen: a gradient and a circular
 * "X" that appear while the bubble is dragged and grow when the bubble is over them.
 *
 * @param bottomInset the navigation bar height, so the target sits above it
 */
@Composable
fun CloseTargetContent(
    visible: Boolean,
    active: Boolean,
    bottomInset: Dp,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, colors.scrim.copy(alpha = 0.4f))))
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomInset + CloseTargetBottomMargin)
                .size(CloseTargetSlot),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                val size by animateDpAsState(if (active) 76.dp else 60.dp, motion.fastSpatialSpec(), label = "closeSize")
                Surface(
                    shape = CircleShape,
                    color = if (active) colors.errorContainer else colors.surfaceContainerHighest,
                    contentColor = if (active) colors.onErrorContainer else colors.onSurface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(size),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.overlay_close_target))
                    }
                }
            }
        }
    }
}

/** The menu's actions, in fan order. */
@Composable
fun rememberBubbleMenuActions(
    onCapture: () -> Unit,
    onSpeak: () -> Unit,
    onClose: () -> Unit,
): List<BubbleMenuAction> {
    val capture = stringResource(R.string.overlay_menu_capture)
    val speak = stringResource(R.string.overlay_menu_speak)
    val close = stringResource(R.string.overlay_menu_close)
    val latestCapture by rememberUpdatedState(onCapture)
    val latestSpeak by rememberUpdatedState(onSpeak)
    val latestClose by rememberUpdatedState(onClose)
    return remember(capture, speak, close) {
        listOf(
            BubbleMenuAction(Icons.Rounded.CropFree, capture, emphasized = true) { latestCapture() },
            BubbleMenuAction(Icons.Rounded.Mic, speak, emphasized = false) { latestSpeak() },
            BubbleMenuAction(Icons.Rounded.Close, close, emphasized = false) { latestClose() },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FloatingBubblePreview() {
    TransVerseTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FloatingBubble(size = BubbleBaseSize)
            FloatingBubble(size = BubbleBaseSize, pressed = true)
            FloatingBubble(size = BubbleBaseSize, expanded = true)
            FloatingBubble(size = BubbleBaseSize, docked = true)
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun BubbleMenuPreview() {
    val state = remember {
        BubbleUiState().apply {
            menuExpanded = true
            bubbleWindowPosition = IntOffset(0, 600)
        }
    }
    TransVerseTheme {
        BubbleMenuContent(
            state = state,
            bubbleSize = BubbleBaseSize,
            actions = rememberBubbleMenuActions({}, {}, {}),
            onDismiss = {},
            onCollapsed = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun CloseTargetPreview() {
    TransVerseTheme {
        CloseTargetContent(visible = true, active = true, bottomInset = 0.dp)
    }
}
