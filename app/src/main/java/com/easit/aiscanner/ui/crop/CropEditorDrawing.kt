package com.easit.aiscanner.ui.crop

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Dimming outside the selected region. */
internal const val REGION_SCRIM_ALPHA = 0.55f

/** Light dimming of the whole image in TEXT mode, so the highlighted blocks stand out. */
internal const val TEXT_DIM_ALPHA = 0.28f

/** Loupe magnification relative to what is on screen. */
internal const val LOUPE_ZOOM = 2f

/** The selection outline and handles are white (with a soft dark edge) to read on any image. */
private val HandleColor = Color.White
private val HandleShadowColor = Color.Black
private const val HANDLE_SHADOW_ALPHA = 0.3f
private const val GRID_ALPHA = 0.55f

/**
 * Reusable paths, strokes and sizes for drawing the editor; created once per density so the
 * draw pass doesn't allocate them every frame.
 */
internal class CropDrawTools(density: Density) {
    private val px: (Dp) -> Float = { with(density) { it.toPx() } }

    val scrimPath = Path()
    val handlePath = Path()
    val activeHandlePath = Path()
    val loupeClip = Path()

    val outlineWidth = px(2.dp)
    val handleWidth = px(4.dp)
    val activeHandleWidth = px(6.dp)
    val gridWidth = px(1.dp)
    val cornerArm = px(22.dp)
    val edgePill = px(24.dp)
    val selectionRadius = px(10.dp)
    val blockRadius = px(6.dp)
    val blockInflate = px(3.dp)
    val badgeRadius = px(10.dp)
    val translationRadius = px(4.dp)
    val loupeRadius = px(56.dp)
    val loupeGap = px(40.dp)
    val loupeMargin = px(8.dp)
    val loupeDot = px(2.dp)
    val loupeShadow = px(3.dp)
    private val shadowExtra = px(1.5.dp)

    val outlineStroke = Stroke(width = outlineWidth)
    val outlineShadowStroke = Stroke(width = outlineWidth + shadowExtra)
    val handleStroke = Stroke(width = handleWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val handleShadowStroke = Stroke(width = handleWidth + shadowExtra, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val activeHandleStroke = Stroke(width = activeHandleWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val activeHandleShadowStroke =
        Stroke(width = activeHandleWidth + shadowExtra, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val blockStroke = Stroke(width = px(1.dp))
    val blockSelectedStroke = Stroke(width = px(2.dp))
    val loupeRing = Stroke(width = px(3.dp))
}

/**
 * Draws [image] with transform [t]. The image is drawn at the state's logical size, so a host
 * may pass a downscaled bitmap for a large original.
 */
internal fun DrawScope.drawImageWith(image: ImageBitmap, t: ImageTransform, alpha: Float = 1f) {
    val w = t.imageWidth * t.scale
    val h = t.imageHeight * t.scale
    withTransform({
        translate(t.center.x, t.center.y)
        if (t.rotation != 0) rotate(t.rotation.toFloat(), Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((-w / 2f).roundToInt(), (-h / 2f).roundToInt()),
            dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * REGION mode: scrim outside the selection (rounded corners), rule-of-thirds grid
 * ([gridAlpha] > 0 while dragging), outline, and corner/edge handles ([activeSides] drawn bolder).
 */
internal fun DrawScope.drawRegion(
    t: ImageTransform,
    crop: IntRect,
    tools: CropDrawTools,
    scrim: Color,
    alpha: Float,
    gridAlpha: Float,
    activeSides: Int,
    withHandles: Boolean,
    radiusScale: Float = 1f,
) {
    if (alpha <= 0f) return
    val bounds = t.imageBounds()
    val r = t.toView(crop)
    val radius = min(tools.selectionRadius * radiusScale, min(r.width, r.height) / 4f)
    val corner = CornerRadius(radius)

    val scrimPath = tools.scrimPath
    scrimPath.rewind()
    scrimPath.fillType = PathFillType.EvenOdd
    scrimPath.addRect(bounds)
    scrimPath.addRoundRect(RoundRect(r, corner))
    drawPath(scrimPath, scrim, alpha = REGION_SCRIM_ALPHA * alpha)

    if (gridAlpha > 0f) {
        val a = GRID_ALPHA * gridAlpha * alpha
        for (i in 1..2) {
            val x = r.left + r.width * i / 3f
            val y = r.top + r.height * i / 3f
            drawLine(HandleColor, Offset(x, r.top), Offset(x, r.bottom), tools.gridWidth, alpha = a)
            drawLine(HandleColor, Offset(r.left, y), Offset(r.right, y), tools.gridWidth, alpha = a)
        }
    }

    drawRoundRect(HandleShadowColor, r.topLeft, r.size, corner, style = tools.outlineShadowStroke, alpha = 0.25f * alpha)
    drawRoundRect(HandleColor, r.topLeft, r.size, corner, style = tools.outlineStroke, alpha = alpha)

    if (withHandles) drawHandles(r, radius, tools, activeSides, alpha)
}

private fun DrawScope.drawHandles(r: Rect, radius: Float, tools: CropDrawTools, activeSides: Int, alpha: Float) {
    // Handles hug the outline from outside: their inner edge lines up with the outline's.
    val o = (tools.handleWidth - tools.outlineWidth) / 2f
    val rad = max(radius + o, 0.5f)
    val arm = max(rad + 1f, min(tools.cornerArm, min(r.width, r.height) / 2f))
    val l = r.left - o
    val t = r.top - o
    val rt = r.right + o
    val b = r.bottom + o

    val normal = tools.handlePath
    val active = tools.activeHandlePath
    normal.rewind()
    active.rewind()
    fun pathFor(sides: Int) = if (sides == activeSides) active else normal

    // Top-left
    pathFor(Sides.LEFT or Sides.TOP).apply {
        moveTo(l, t + arm)
        lineTo(l, t + rad)
        arcTo(Rect(l, t, l + 2 * rad, t + 2 * rad), 180f, 90f, false)
        lineTo(l + arm, t)
    }
    // Top-right
    pathFor(Sides.RIGHT or Sides.TOP).apply {
        moveTo(rt - arm, t)
        lineTo(rt - rad, t)
        arcTo(Rect(rt - 2 * rad, t, rt, t + 2 * rad), 270f, 90f, false)
        lineTo(rt, t + arm)
    }
    // Bottom-right
    pathFor(Sides.RIGHT or Sides.BOTTOM).apply {
        moveTo(rt, b - arm)
        lineTo(rt, b - rad)
        arcTo(Rect(rt - 2 * rad, b - 2 * rad, rt, b), 0f, 90f, false)
        lineTo(rt - arm, b)
    }
    // Bottom-left
    pathFor(Sides.LEFT or Sides.BOTTOM).apply {
        moveTo(l + arm, b)
        lineTo(l + rad, b)
        arcTo(Rect(l, b - 2 * rad, l + 2 * rad, b), 90f, 90f, false)
        lineTo(l, b - arm)
    }

    drawPath(normal, HandleShadowColor, alpha = HANDLE_SHADOW_ALPHA * alpha, style = tools.handleShadowStroke)
    drawPath(normal, HandleColor, alpha = alpha, style = tools.handleStroke)
    if (Sides.isHandle(activeSides) && activeSides.countOneBits() == 2) {
        drawPath(active, HandleShadowColor, alpha = HANDLE_SHADOW_ALPHA * alpha, style = tools.activeHandleShadowStroke)
        drawPath(active, HandleColor, alpha = alpha, style = tools.activeHandleStroke)
    }

    // Edge pills, only when the side is long enough to keep them clear of the corners.
    val half = tools.edgePill / 2f
    val roomX = r.width > 2 * arm + tools.edgePill
    val roomY = r.height > 2 * arm + tools.edgePill
    val cx = r.center.x
    val cy = r.center.y
    if (roomX) {
        drawEdge(Offset(cx - half, t), Offset(cx + half, t), activeSides == Sides.TOP, tools, alpha)
        drawEdge(Offset(cx - half, b), Offset(cx + half, b), activeSides == Sides.BOTTOM, tools, alpha)
    }
    if (roomY) {
        drawEdge(Offset(l, cy - half), Offset(l, cy + half), activeSides == Sides.LEFT, tools, alpha)
        drawEdge(Offset(rt, cy - half), Offset(rt, cy + half), activeSides == Sides.RIGHT, tools, alpha)
    }
}

private fun DrawScope.drawEdge(start: Offset, end: Offset, active: Boolean, tools: CropDrawTools, alpha: Float) {
    val width = if (active) tools.activeHandleWidth else tools.handleWidth
    val shadow = if (active) tools.activeHandleShadowStroke.width else tools.handleShadowStroke.width
    drawLine(HandleShadowColor, start, end, shadow, StrokeCap.Round, alpha = HANDLE_SHADOW_ALPHA * alpha)
    drawLine(HandleColor, start, end, width, StrokeCap.Round, alpha = alpha)
}

/**
 * TEXT mode: a rounded highlight per block; selected blocks ([selection] 0…1, animated) get a
 * stronger fill, an outline and a check badge. With [translated], unselected blocks get no fill
 * so the translations stay readable.
 */
internal fun DrawScope.drawBlocks(
    t: ImageTransform,
    blocks: List<TextBlock>,
    selection: (Int) -> Float,
    tools: CropDrawTools,
    primary: Color,
    onPrimary: Color,
    check: Painter,
    checkTint: ColorFilter,
    alpha: Float,
    translated: Boolean,
) {
    if (alpha <= 0f) return
    val corner = CornerRadius(tools.blockRadius)
    val inflate = tools.blockInflate
    blocks.forEachIndexed { index, block ->
        val p = selection(index)
        val shown = p.coerceIn(0f, 1f)
        val v = t.toView(block.bounds)
        val topLeft = Offset(v.left - inflate, v.top - inflate)
        val size = Size(v.width + 2 * inflate, v.height + 2 * inflate)
        val baseFill = if (translated) 0f else 0.16f
        val fill = baseFill + (0.34f - baseFill) * shown
        if (fill > 0f) drawRoundRect(primary, topLeft, size, corner, alpha = fill * alpha)
        if (!translated) {
            drawRoundRect(primary, topLeft, size, corner, style = tools.blockStroke, alpha = 0.6f * alpha * (1f - shown))
        }
        if (shown > 0.01f) {
            drawRoundRect(primary, topLeft, size, corner, style = tools.blockSelectedStroke, alpha = shown * alpha)
            // Check badge on the top-right corner, popping in with the (overshooting) spring.
            val badge = tools.badgeRadius * p.coerceIn(0f, 1.3f)
            val c = Offset(topLeft.x + size.width, topLeft.y)
            drawCircle(onPrimary, badge + tools.blockSelectedStroke.width, c, alpha = shown * alpha)
            drawCircle(primary, badge, c, alpha = shown * alpha)
            val icon = badge * 1.5f
            translate(c.x - icon / 2f, c.y - icon / 2f) {
                with(check) { draw(Size(icon, icon), alpha = shown * alpha, colorFilter = checkTint) }
            }
        }
    }
}

/** A translation laid out to cover its block, in image px (see [layoutTranslations]). */
internal class TranslationLayout(
    /** The box that hides the original text. */
    val box: Rect,
    val text: TextLayoutResult,
    val textTopLeft: Offset,
)

/** "Translate on image": paints each translation over its block, in image space. */
internal fun DrawScope.drawTranslations(
    t: ImageTransform,
    layouts: List<TranslationLayout?>,
    tools: CropDrawTools,
    surface: Color,
    onSurface: Color,
    alpha: Float,
) {
    if (alpha <= 0f) return
    val radius = tools.translationRadius / t.scale
    withTransform({
        translate(t.center.x, t.center.y)
        if (t.rotation != 0) rotate(t.rotation.toFloat(), Offset.Zero)
        scale(t.scale, t.scale, Offset.Zero)
        translate(-t.imageWidth / 2f, -t.imageHeight / 2f)
    }) {
        layouts.forEach { layout ->
            if (layout == null) return@forEach
            val box = layout.box
            drawRoundRect(
                surface,
                box.topLeft,
                box.size,
                CornerRadius(min(radius, box.height / 4f)),
                alpha = alpha,
            )
            drawText(layout.text, color = onSurface, topLeft = layout.textTopLeft, alpha = alpha)
        }
    }
}

/**
 * The magnifier loupe shown above [finger] while a handle is dragged: the image and selection
 * edge around the dragged handle, magnified [LOUPE_ZOOM]×, in a circle.
 */
internal fun DrawScope.drawLoupe(
    image: ImageBitmap,
    t: ImageTransform,
    crop: IntRect,
    finger: Offset,
    sides: Int,
    tools: CropDrawTools,
    scrim: Color,
    amount: Float,
) {
    val alpha = amount.coerceIn(0f, 1f)
    if (alpha <= 0f) return
    val r = t.toView(crop)
    val anchor = Offset(
        x = when {
            Sides.has(sides, Sides.LEFT) -> r.left
            Sides.has(sides, Sides.RIGHT) -> r.right
            else -> finger.x.coerceIn(r.left, r.right)
        },
        y = when {
            Sides.has(sides, Sides.TOP) -> r.top
            Sides.has(sides, Sides.BOTTOM) -> r.bottom
            else -> finger.y.coerceIn(r.top, r.bottom)
        },
    )
    val full = tools.loupeRadius
    val radius = full * (0.6f + 0.4f * amount)
    var cy = finger.y - tools.loupeGap - full
    if (cy - full < tools.loupeMargin) cy = finger.y + tools.loupeGap + full
    val minX = full + tools.loupeMargin
    val maxX = size.width - full - tools.loupeMargin
    val cx = if (minX <= maxX) finger.x.coerceIn(minX, maxX) else size.width / 2f
    val center = Offset(cx, cy)
    val zoomed = t.magnified(anchor, center, LOUPE_ZOOM)

    drawCircle(HandleShadowColor, radius + tools.loupeShadow, center, alpha = 0.2f * alpha)
    val clip = tools.loupeClip
    clip.rewind()
    clip.addOval(Rect(center, radius))
    clipPath(clip) {
        drawRect(HandleShadowColor, Offset(center.x - radius, center.y - radius), Size(2 * radius, 2 * radius), alpha = alpha)
        drawImageWith(image, zoomed, alpha)
        drawRegion(
            t = zoomed,
            crop = crop,
            tools = tools,
            scrim = scrim,
            alpha = alpha,
            gridAlpha = 0f,
            activeSides = sides,
            withHandles = false,
            radiusScale = LOUPE_ZOOM,
        )
        drawCircle(HandleColor, tools.loupeDot, center, alpha = alpha)
    }
    drawCircle(HandleColor, radius, center, style = tools.loupeRing, alpha = alpha)
}
