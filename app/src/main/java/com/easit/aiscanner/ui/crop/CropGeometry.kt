package com.easit.aiscanner.ui.crop

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Pure geometry for the crop editor: no Compose runtime, so it is easy to unit test.
//
// Coordinate spaces:
//  - image px: the ORIGINAL (unrotated) image, as in CropEditorState.
//  - view px: the editor's own layout coordinates.
// The image is rotated clockwise by `rotation` around its centre, scaled by `scale`, and its
// centre placed at `center` (view px).

/** Rectangle sides as bit flags, in clockwise order (so rotating = shifting bits). */
internal object Sides {
    const val NONE = 0
    const val LEFT = 1
    const val TOP = 2
    const val RIGHT = 4
    const val BOTTOM = 8
    /** All four sides: moving the whole rectangle. */
    const val ALL = LEFT or TOP or RIGHT or BOTTOM

    fun has(sides: Int, side: Int): Boolean = (sides and side) != 0

    /** A corner or edge handle (as opposed to nothing, or the whole rectangle). */
    fun isHandle(sides: Int): Boolean = sides != NONE && sides != ALL
}

/** Insets of the editor's content area, in view px (absolute left/right, not start/end). */
@Immutable
internal data class ViewInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
)

/** Normalises any angle to 0, 90, 180 or 270. */
internal fun normalizeRotation(degrees: Int): Int = ((degrees / 90) % 4 + 4) % 4 * 90

/** Size of the image once rotated by [rotation]. */
internal fun rotatedSize(imageWidth: Int, imageHeight: Int, rotation: Int): IntSize =
    if (normalizeRotation(rotation) % 180 == 0) IntSize(imageWidth, imageHeight)
    else IntSize(imageHeight, imageWidth)

/** The area inside [insets] of a view of [size]; the whole view if the insets leave nothing. */
internal fun contentArea(size: IntSize, insets: ViewInsets): Rect {
    val area = Rect(insets.left, insets.top, size.width - insets.right, size.height - insets.bottom)
    return if (area.width > 1f && area.height > 1f) area
    else Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
}

/** Scale that fits (contains) the rotated image in [area]. */
internal fun fitScale(imageWidth: Int, imageHeight: Int, rotation: Int, area: Rect): Float {
    val rotated = rotatedSize(imageWidth, imageHeight, rotation)
    if (rotated.width <= 0 || rotated.height <= 0) return 1f
    return min(area.width / rotated.width, area.height / rotated.height)
}

/**
 * Keeps the user's pan (offset of the image centre from the [area] centre, view px) such that
 * the image can't be lost: a side smaller than the area stays centred, a larger side can move
 * only until its edge reaches the area's edge.
 */
internal fun clampPan(pan: Offset, displayedWidth: Float, displayedHeight: Float, area: Rect): Offset {
    val maxX = max(0f, (displayedWidth - area.width) / 2f)
    val maxY = max(0f, (displayedHeight - area.height) / 2f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}

/**
 * The pan that keeps the content under [focus] (relative to the area centre) fixed while the
 * zoom changes from [zoom] to [newZoom].
 */
internal fun panForZoom(pan: Offset, zoom: Float, newZoom: Float, focus: Offset): Offset =
    focus - (focus - pan) * (newZoom / zoom)

/** Maps between image px and view px for one frame. */
@Immutable
internal data class ImageTransform(
    val imageWidth: Int,
    val imageHeight: Int,
    /** 0, 90, 180 or 270, clockwise. */
    val rotation: Int,
    /** Where the image centre is drawn, view px. */
    val center: Offset,
    /** View px per image px. */
    val scale: Float,
) {
    val displayedWidth: Float
        get() = rotatedSize(imageWidth, imageHeight, rotation).width * scale
    val displayedHeight: Float
        get() = rotatedSize(imageWidth, imageHeight, rotation).height * scale

    /** Image px → view px. */
    fun toView(x: Float, y: Float): Offset {
        val dx = x - imageWidth / 2f
        val dy = y - imageHeight / 2f
        return center + rotateVector(dx, dy, rotation) * scale
    }

    fun toView(point: Offset): Offset = toView(point.x, point.y)

    /** View px → image px (may be outside the image). */
    fun toImage(view: Offset): Offset {
        val r = (view - center) / scale
        val d = unrotateVector(r.x, r.y, rotation)
        return Offset(d.x + imageWidth / 2f, d.y + imageHeight / 2f)
    }

    /** A movement in view px → the same movement in image px. */
    fun viewDeltaToImage(delta: Offset): Offset {
        val r = delta / scale
        return unrotateVector(r.x, r.y, rotation)
    }

    /** An image rect → its (axis-aligned) rect on screen. */
    fun toView(rect: IntRect): Rect = toView(
        rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()
    )

    fun toView(left: Float, top: Float, right: Float, bottom: Float): Rect {
        val a = toView(left, top)
        val b = toView(right, bottom)
        return Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    }

    /** The whole image on screen. */
    fun imageBounds(): Rect = toView(0f, 0f, imageWidth.toFloat(), imageHeight.toFloat())

    /**
     * A transform zoomed by [factor] whose view point [anchor] (in this transform) is drawn at
     * [target] instead: used for the magnifier loupe.
     */
    fun magnified(anchor: Offset, target: Offset, factor: Float): ImageTransform =
        copy(center = target - (anchor - center) * factor, scale = scale * factor)
}

/** Rotates (dx, dy) clockwise by [rotation] degrees in y-down screen space. */
internal fun rotateVector(dx: Float, dy: Float, rotation: Int): Offset = when (normalizeRotation(rotation)) {
    90 -> Offset(-dy, dx)
    180 -> Offset(-dx, -dy)
    270 -> Offset(dy, -dx)
    else -> Offset(dx, dy)
}

/** Inverse of [rotateVector]. */
internal fun unrotateVector(rx: Float, ry: Float, rotation: Int): Offset = when (normalizeRotation(rotation)) {
    90 -> Offset(ry, -rx)
    180 -> Offset(-rx, -ry)
    270 -> Offset(-ry, rx)
    else -> Offset(rx, ry)
}

/**
 * Which image sides the given on-screen [viewSides] show when the image is displayed rotated
 * clockwise by [rotation]. E.g. at 90° the image's top edge is on the screen's right.
 */
internal fun viewSidesToImageSides(viewSides: Int, rotation: Int): Int {
    val steps = normalizeRotation(rotation) / 90
    if (steps == 0 || viewSides == Sides.ALL || viewSides == Sides.NONE) return viewSides
    // Bits are clockwise (L, T, R, B): image side index = view side index - steps (mod 4).
    val shifted = (viewSides shr steps) or (viewSides shl (4 - steps))
    return shifted and Sides.ALL
}

/**
 * Moves the image [sides] of [start] by [delta] (image px), keeping it inside the image and
 * at least [minSize] px per side. Opposite sides never cross.
 */
internal fun resizeRect(
    start: IntRect,
    sides: Int,
    delta: Offset,
    imageWidth: Int,
    imageHeight: Int,
    minSize: Int,
): IntRect {
    val minW = min(minSize, imageWidth)
    val minH = min(minSize, imageHeight)
    var left = start.left
    var top = start.top
    var right = start.right
    var bottom = start.bottom
    if (Sides.has(sides, Sides.LEFT)) {
        left = (start.left + delta.x.roundToInt()).coerceIn(0, max(0, right - minW))
    }
    if (Sides.has(sides, Sides.RIGHT)) {
        right = (start.right + delta.x.roundToInt()).coerceIn(min(imageWidth, left + minW), imageWidth)
    }
    if (Sides.has(sides, Sides.TOP)) {
        top = (start.top + delta.y.roundToInt()).coerceIn(0, max(0, bottom - minH))
    }
    if (Sides.has(sides, Sides.BOTTOM)) {
        bottom = (start.bottom + delta.y.roundToInt()).coerceIn(min(imageHeight, top + minH), imageHeight)
    }
    return IntRect(left, top, right, bottom)
}

/** Moves [start] by [delta] (image px) without changing its size, staying inside the image. */
internal fun moveRect(start: IntRect, delta: Offset, imageWidth: Int, imageHeight: Int): IntRect {
    val dx = delta.x.roundToInt().coerceIn(-start.left, max(-start.left, imageWidth - start.right))
    val dy = delta.y.roundToInt().coerceIn(-start.top, max(-start.top, imageHeight - start.bottom))
    return IntRect(start.left + dx, start.top + dy, start.right + dx, start.bottom + dy)
}

/** Grows (positive) or shrinks (negative) every side of [rect] by [dx]/[dy] image px. */
internal fun inflateRect(
    rect: IntRect,
    dx: Int,
    dy: Int,
    imageWidth: Int,
    imageHeight: Int,
    minSize: Int,
): IntRect {
    val minW = min(minSize, imageWidth)
    val minH = min(minSize, imageHeight)
    val w = rect.width + 2 * dx
    val h = rect.height + 2 * dy
    // Never shrink below the minimum: keep the centre and stop at minSize.
    val sx = if (w < minW) (minW - rect.width) / 2 else dx
    val sy = if (h < minH) (minH - rect.height) / 2 else dy
    return IntRect(
        (rect.left - sx).coerceAtLeast(0),
        (rect.top - sy).coerceAtLeast(0),
        (rect.right + sx).coerceAtMost(imageWidth),
        (rect.bottom + sy).coerceAtMost(imageHeight),
    )
}

/**
 * Which part of the on-screen selection [rect] a touch at [point] grabs: a corner (two sides),
 * an edge (one side), the inside ([Sides.ALL], to move it) or nothing ([Sides.NONE]).
 *
 * Handle zones reach [radius] outside the rect and up to a third of its size inside, so a small
 * selection still has a movable middle.
 */
internal fun hitTestSelection(rect: Rect, point: Offset, radius: Float): Int {
    val x = point.x
    val y = point.y
    if (x < rect.left - radius || x > rect.right + radius) return Sides.NONE
    if (y < rect.top - radius || y > rect.bottom + radius) return Sides.NONE
    val insideX = min(radius, rect.width / 3f)
    val insideY = min(radius, rect.height / 3f)
    var sides = Sides.NONE
    if (x <= rect.left + insideX) sides = sides or Sides.LEFT
    else if (x >= rect.right - insideX) sides = sides or Sides.RIGHT
    if (y <= rect.top + insideY) sides = sides or Sides.TOP
    else if (y >= rect.bottom - insideY) sides = sides or Sides.BOTTOM
    if (sides != Sides.NONE) return sides
    return if (rect.contains(point)) Sides.ALL else Sides.NONE
}

/**
 * Index of the text block under [imagePoint] (image px), preferring the smallest block when they
 * overlap; [slack] image px of tolerance around each block. -1 if none.
 */
internal fun hitTestBlocks(blocks: List<TextBlock>, imagePoint: Offset, slack: Float): Int {
    var best = -1
    var bestArea = Long.MAX_VALUE
    blocks.forEachIndexed { index, block ->
        val b = block.bounds
        if (imagePoint.x >= b.left - slack && imagePoint.x <= b.right + slack &&
            imagePoint.y >= b.top - slack && imagePoint.y <= b.bottom + slack
        ) {
            val area = b.width.toLong() * b.height.toLong()
            if (area < bestArea) {
                bestArea = area
                best = index
            }
        }
    }
    return best
}
