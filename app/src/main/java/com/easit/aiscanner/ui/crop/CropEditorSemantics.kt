package com.easit.aiscanner.ui.crop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.R
import kotlin.math.min
import kotlin.math.roundToInt

/** Accessibility step for nudging the region: this fraction of the image's shorter side. */
private const val NUDGE_FRACTION = 0.05f

/** The 8 handles: on-screen sides and their spoken names, in reading order. */
private val HandleSides = listOf(
    Sides.LEFT or Sides.TOP to R.string.crop_handle_top_left,
    Sides.TOP to R.string.crop_handle_top,
    Sides.RIGHT or Sides.TOP to R.string.crop_handle_top_right,
    Sides.RIGHT to R.string.crop_handle_right,
    Sides.RIGHT or Sides.BOTTOM to R.string.crop_handle_bottom_right,
    Sides.BOTTOM to R.string.crop_handle_bottom,
    Sides.LEFT or Sides.BOTTOM to R.string.crop_handle_bottom_left,
    Sides.LEFT to R.string.crop_handle_left,
)

/**
 * Invisible nodes placed over the region and its handles, so TalkBack can find them by touch and
 * adjust the region with custom actions (the drawing itself is one canvas).
 */
@Composable
internal fun RegionSemantics(state: CropEditorState, viewport: CropViewport) {
    val touchSize = with(LocalDensity.current) { 48.dp.toPx() }
    val regionLabel = stringResource(R.string.crop_region)
    val crop = state.cropRect
    val regionState = stringResource(
        R.string.crop_region_state,
        (crop.width * 100f / state.imageWidth.coerceAtLeast(1)).roundToInt(),
        (crop.height * 100f / state.imageHeight.coerceAtLeast(1)).roundToInt(),
    )
    val expand = stringResource(R.string.crop_action_expand)
    val shrink = stringResource(R.string.crop_action_shrink)
    val moveLeft = stringResource(R.string.crop_action_move_left)
    val moveRight = stringResource(R.string.crop_action_move_right)
    val moveUp = stringResource(R.string.crop_action_move_up)
    val moveDown = stringResource(R.string.crop_action_move_down)
    val outward = stringResource(R.string.crop_action_outward)
    val inward = stringResource(R.string.crop_action_inward)
    val handleLabels = HandleSides.map { stringResource(it.second) }

    val regionActions = remember(state, viewport, expand, shrink, moveLeft, moveRight, moveUp, moveDown) {
        listOf(
            CustomAccessibilityAction(expand) { viewport.inflateRegion(state, grow = true); true },
            CustomAccessibilityAction(shrink) { viewport.inflateRegion(state, grow = false); true },
            CustomAccessibilityAction(moveLeft) { viewport.moveRegion(state, Offset(-1f, 0f)); true },
            CustomAccessibilityAction(moveRight) { viewport.moveRegion(state, Offset(1f, 0f)); true },
            CustomAccessibilityAction(moveUp) { viewport.moveRegion(state, Offset(0f, -1f)); true },
            CustomAccessibilityAction(moveDown) { viewport.moveRegion(state, Offset(0f, 1f)); true },
        )
    }
    val handleActions = remember(state, viewport, outward, inward) {
        HandleSides.map { (sides, _) ->
            listOf(
                CustomAccessibilityAction(outward) { viewport.nudgeHandle(state, sides, outward = true); true },
                CustomAccessibilityAction(inward) { viewport.nudgeHandle(state, sides, outward = false); true },
            )
        }
    }

    PositionedNodes(
        count = 1 + HandleSides.size,
        rectOf = { index ->
            val t = viewport.transform ?: return@PositionedNodes null
            val r = t.toView(state.cropRect)
            if (index == 0) r else handleRect(r, HandleSides[index - 1].first, touchSize)
        },
    ) { index ->
        if (index == 0) {
            Box(
                Modifier.semantics {
                    contentDescription = regionLabel
                    stateDescription = regionState
                    customActions = regionActions
                }
            )
        } else {
            val label = handleLabels[index - 1]
            val actions = handleActions[index - 1]
            Box(
                Modifier.semantics {
                    contentDescription = label
                    customActions = actions
                }
            )
        }
    }
}

/** One checkbox-like node per text block: spoken text, selected state, double-tap toggles. */
@Composable
internal fun BlockSemantics(
    state: CropEditorState,
    viewport: CropViewport,
    translations: List<String>?,
) {
    val blocks = state.textBlocks
    val selected = state.selectedBlocks
    val toggleLabel = stringResource(R.string.crop_block_toggle)
    PositionedNodes(
        count = blocks.size,
        rectOf = { index -> blocks.getOrNull(index)?.let { viewport.transform?.toView(it.bounds) } },
    ) { index ->
        val block = blocks[index]
        val translation = translations?.getOrNull(index)?.trim().orEmpty()
        val description = if (translation.isEmpty()) block.text else "$translation\n${block.text}"
        val isSelected = index in selected
        Box(
            Modifier.semantics {
                role = Role.Checkbox
                contentDescription = description
                toggleableState = ToggleableState(isSelected)
                onClick(label = toggleLabel) {
                    state.toggleBlock(index)
                    true
                }
            }
        )
    }
}

/**
 * Lays out [count] children at the view rects [rectOf] gives (null = not placed). Rects are read
 * during measure, so zoom/pan only re-measures these nodes, never recomposes them.
 */
@Composable
private fun PositionedNodes(
    count: Int,
    rectOf: (Int) -> Rect?,
    content: @Composable (Int) -> Unit,
) {
    Layout(
        content = { repeat(count) { content(it) } },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val rects = List(measurables.size) { rectOf(it) }
        val placeables = measurables.mapIndexed { index, measurable ->
            val r = rects[index]
            if (r == null) {
                measurable.measure(Constraints.fixed(0, 0))
            } else {
                measurable.measure(
                    Constraints.fixed(
                        r.width.roundToInt().coerceAtLeast(1),
                        r.height.roundToInt().coerceAtLeast(1),
                    )
                )
            }
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val r = rects[index] ?: return@forEachIndexed
                placeable.place(r.left.roundToInt(), r.top.roundToInt())
            }
        }
    }
}

/** A [size]-square touch rect centred on the handle for [sides] of [r]. */
private fun handleRect(r: Rect, sides: Int, size: Float): Rect {
    val x = when {
        Sides.has(sides, Sides.LEFT) -> r.left
        Sides.has(sides, Sides.RIGHT) -> r.right
        else -> r.center.x
    }
    val y = when {
        Sides.has(sides, Sides.TOP) -> r.top
        Sides.has(sides, Sides.BOTTOM) -> r.bottom
        else -> r.center.y
    }
    return Rect(Offset(x, y), size / 2f)
}

private fun nudgeStep(state: CropEditorState): Float =
    (min(state.imageWidth, state.imageHeight) * NUDGE_FRACTION).coerceAtLeast(1f)

/** Moves the on-screen [sides] outward or inward by one step. */
internal fun CropViewport.nudgeHandle(state: CropEditorState, sides: Int, outward: Boolean) {
    val t = transform ?: return
    val step = nudgeStep(state) * t.scale // in view px
    var dx = 0f
    var dy = 0f
    if (Sides.has(sides, Sides.LEFT)) dx -= step
    if (Sides.has(sides, Sides.RIGHT)) dx += step
    if (Sides.has(sides, Sides.TOP)) dy -= step
    if (Sides.has(sides, Sides.BOTTOM)) dy += step
    if (!outward) {
        dx = -dx
        dy = -dy
    }
    setCrop(
        resizeRect(
            state.cropRect,
            viewSidesToImageSides(sides, t.rotation),
            t.viewDeltaToImage(Offset(dx, dy)),
            state.imageWidth,
            state.imageHeight,
            CropEditorState.MIN_SIZE_PX,
        )
    )
}

/** Moves the region one step in the on-screen [direction] (unit vector). */
internal fun CropViewport.moveRegion(state: CropEditorState, direction: Offset) {
    val t = transform ?: return
    val step = nudgeStep(state) * t.scale
    setCrop(moveRect(state.cropRect, t.viewDeltaToImage(direction * step), state.imageWidth, state.imageHeight))
}

/** Grows or shrinks every side of the region by one step. */
internal fun CropViewport.inflateRegion(state: CropEditorState, grow: Boolean) {
    val step = nudgeStep(state).roundToInt() * (if (grow) 1 else -1)
    setCrop(
        inflateRect(state.cropRect, step, step, state.imageWidth, state.imageHeight, CropEditorState.MIN_SIZE_PX)
    )
}
