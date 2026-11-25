package com.easit.aiscanner.ui.crop

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The editor's view of [state]: layout size, content insets, the user's zoom and pan, and what
 * is being dragged. Produces the [ImageTransform] everything (drawing, gestures, semantics) uses,
 * so there is exactly one image px ⇄ view px model.
 */
@Stable
internal class CropViewport(private val state: CropEditorState) {

    var size: IntSize by mutableStateOf(IntSize.Zero)
    var insets: ViewInsets by mutableStateOf(ViewInsets())

    /** User zoom on top of the fit scale: 1 = whole image fitted. */
    var zoom: Float by mutableFloatStateOf(1f)
        private set

    /** Offset of the image centre from the content area's centre, view px. */
    var pan: Offset by mutableStateOf(Offset.Zero)
        private set

    /** View sides being dragged right now ([Sides.NONE] when idle, [Sides.ALL] when moving). */
    var activeSides: Int by mutableIntStateOf(Sides.NONE)

    /** The last handle dragged; kept after release so the loupe can fade out in place. */
    var loupeSides: Int by mutableIntStateOf(Sides.NONE)

    /** Where the finger dragging a handle is, view px. */
    var fingerPosition: Offset by mutableStateOf(Offset.Zero)

    /** The last crop rect the editor itself set, to tell its own changes from the host's. */
    var lastEditorRect: IntRect? = null
        private set

    private var animationJob: Job? = null

    /** The content area (inside the insets), view px. */
    val area: Rect get() = contentArea(size, insets)

    /** The current image ⇄ view mapping, or null before the editor has a size. */
    val transform: ImageTransform? by derivedStateOf {
        transformFor(state.rotationDegrees, zoom, pan)
    }

    fun transformFor(rotation: Int, zoom: Float, pan: Offset): ImageTransform? {
        if (size.width <= 0 || size.height <= 0 || state.imageWidth <= 0 || state.imageHeight <= 0) {
            return null
        }
        val area = area
        val fit = fitScale(state.imageWidth, state.imageHeight, rotation, area)
        return ImageTransform(
            imageWidth = state.imageWidth,
            imageHeight = state.imageHeight,
            rotation = normalizeRotation(rotation),
            center = area.center + pan,
            scale = fit * zoom,
        )
    }

    /** The fit (zoom 1) scale for [rotation]; 0 before the editor has a size. */
    fun fitScaleFor(rotation: Int = state.rotationDegrees): Float {
        if (size.width <= 0 || size.height <= 0) return 0f
        return fitScale(state.imageWidth, state.imageHeight, rotation, area)
    }

    /** Applies one pinch step: zoom by [zoomChange] around [centroid] and pan by [panChange]. */
    fun transformBy(centroid: Offset, zoomChange: Float, panChange: Offset) {
        val newZoom = (zoom * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
        val focus = centroid - area.center
        setZoomAndPan(newZoom, panForZoom(pan, zoom, newZoom, focus) + panChange)
    }

    fun panBy(delta: Offset) = setZoomAndPan(zoom, pan + delta)

    /** Whole image fitted, centred. */
    fun snapHome() {
        zoom = 1f
        pan = Offset.Zero
    }

    /** Animates to [target] zoom keeping the content under [focus] (view px) in place. */
    suspend fun animateZoomTo(target: Float, focus: Offset, spec: AnimationSpec<Float>) {
        val startZoom = zoom
        val startPan = pan
        val clampedTarget = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
        val targetPan = clampedPan(
            panForZoom(startPan, startZoom, clampedTarget, focus - area.center),
            clampedTarget,
        )
        animate(initialValue = 0f, targetValue = 1f, animationSpec = spec) { progress, _ ->
            // Expressive springs overshoot a little: allow it, but never collapse the image.
            zoom = lerp(startZoom, clampedTarget, progress).coerceAtLeast(MIN_ZOOM * 0.8f)
            pan = Offset(lerp(startPan.x, targetPan.x, progress), lerp(startPan.y, targetPan.y, progress))
        }
        zoom = clampedTarget
        pan = targetPan
    }

    /** Runs a viewport animation, replacing any running one. */
    fun launchAnimation(scope: CoroutineScope, block: suspend CropViewport.() -> Unit) {
        animationJob?.cancel()
        animationJob = scope.launch { block() }
    }

    fun cancelAnimation() {
        animationJob?.cancel()
        animationJob = null
    }

    /** Sets the crop rect from the editor (gestures, accessibility actions). */
    fun setCrop(rect: IntRect) {
        state.updateCropRect(rect)
        lastEditorRect = state.cropRect
    }

    private fun setZoomAndPan(newZoom: Float, newPan: Offset) {
        pan = clampedPan(newPan, newZoom)
        zoom = newZoom
    }

    private fun clampedPan(pan: Offset, zoom: Float): Offset {
        val fit = fitScaleFor()
        val rotated = rotatedSize(state.imageWidth, state.imageHeight, state.rotationDegrees)
        return clampPan(pan, rotated.width * fit * zoom, rotated.height * fit * zoom, area)
    }

    companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 6f
        const val DOUBLE_TAP_ZOOM = 2.5f
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float = start + (stop - start) * fraction
