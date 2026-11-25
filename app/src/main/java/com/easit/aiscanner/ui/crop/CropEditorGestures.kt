package com.easit.aiscanner.ui.crop

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

/** Touch zone around each handle: 24dp each way = a 48dp target. */
private val HandleTouchRadius = 24.dp
private val BlockTouchSlack = 6.dp
private val DoubleTapSlop = 40.dp

/**
 * All editor touch handling in one detector, so gestures never fight:
 *  - one finger on a handle / inside the region (REGION mode): resize / move;
 *  - one finger elsewhere while zoomed: pan;
 *  - two fingers: pinch-zoom and pan (cancels a handle drag);
 *  - tap: toggle a text block (TEXT mode); double tap: zoom 1× ⇄ 2.5× around the tap.
 */
internal fun Modifier.cropGestures(
    state: CropEditorState,
    viewport: CropViewport,
    scope: CoroutineScope,
    zoomSpec: () -> AnimationSpec<Float>,
): Modifier = pointerInput(state, viewport) {
    val handleRadius = HandleTouchRadius.toPx()
    val blockSlack = BlockTouchSlack.toPx()
    val doubleTapSlop = DoubleTapSlop.toPx()
    var lastTapTime = 0L
    var lastTapPosition = Offset.Zero
    var lastTapBlock = -1

    fun onTap(position: Offset, time: Long) {
        val t = viewport.transform ?: return
        val block = if (state.mode == CropMode.TEXT) {
            hitTestBlocks(state.textBlocks, t.toImage(position), blockSlack / t.scale)
        } else {
            -1
        }
        val isDoubleTap = lastTapTime != 0L &&
            time - lastTapTime <= viewConfiguration.doubleTapTimeoutMillis &&
            (position - lastTapPosition).getDistance() <= doubleTapSlop &&
            block == lastTapBlock
        if (isDoubleTap) {
            // The first tap toggled the block right away (no waiting); undo it and zoom instead.
            if (block >= 0) state.toggleBlock(block)
            lastTapTime = 0L
            val target = if (viewport.zoom > 1.05f) CropViewport.MIN_ZOOM else CropViewport.DOUBLE_TAP_ZOOM
            viewport.launchAnimation(scope) { animateZoomTo(target, position, zoomSpec()) }
        } else {
            if (block >= 0) state.toggleBlock(block)
            lastTapTime = time
            lastTapPosition = position
            lastTapBlock = block
        }
    }

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        viewport.cancelAnimation()
        val start = viewport.transform
        val startRect = state.cropRect
        val grabbed = if (state.mode == CropMode.REGION && start != null) {
            hitTestSelection(start.toView(startRect), down.position, handleRadius)
        } else {
            Sides.NONE
        }
        val imageSides = if (start != null) viewSidesToImageSides(grabbed, start.rotation) else Sides.NONE
        val slop = viewConfiguration.touchSlop
        var moved = false
        var dragging = false
        var panning = false
        var multiTouch = false
        var upTime = down.uptimeMillis

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed == 0) {
                    upTime = event.changes.firstOrNull()?.uptimeMillis ?: upTime
                    break
                }
                if (pressed >= 2) {
                    if (!multiTouch) {
                        multiTouch = true
                        moved = true
                        dragging = false
                        viewport.activeSides = Sides.NONE
                    }
                    viewport.transformBy(
                        centroid = event.calculateCentroid(useCurrent = true),
                        zoomChange = event.calculateZoom(),
                        panChange = event.calculatePan(),
                    )
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                    continue
                }
                val change = event.changes.firstOrNull { it.pressed } ?: continue
                if (multiTouch) {
                    // One finger left after a pinch: keep panning with it.
                    viewport.panBy(change.positionChange())
                    change.consume()
                    continue
                }
                val total = change.position - down.position
                if (!moved && total.getDistance() > slop) {
                    moved = true
                    if (grabbed != Sides.NONE && start != null) {
                        dragging = true
                        viewport.activeSides = grabbed
                        if (Sides.isHandle(grabbed)) viewport.loupeSides = grabbed
                    } else if (viewport.zoom > 1.01f) {
                        panning = true
                    }
                }
                if (dragging && start != null) {
                    // Absolute from the gesture start, so the region tracks the finger exactly.
                    val delta = start.viewDeltaToImage(total)
                    val rect = if (grabbed == Sides.ALL) {
                        moveRect(startRect, delta, state.imageWidth, state.imageHeight)
                    } else {
                        resizeRect(
                            startRect, imageSides, delta,
                            state.imageWidth, state.imageHeight, CropEditorState.MIN_SIZE_PX,
                        )
                    }
                    viewport.setCrop(rect)
                    viewport.fingerPosition = change.position
                    change.consume()
                } else if (panning) {
                    viewport.panBy(change.positionChange())
                    change.consume()
                }
            }
        } finally {
            viewport.activeSides = Sides.NONE
        }
        if (!moved) onTap(down.position, upTime)
    }
}
