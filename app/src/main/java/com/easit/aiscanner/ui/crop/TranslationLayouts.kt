package com.easit.aiscanner.ui.crop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** sp == px, so text can be laid out directly in image pixels. */
private val ImagePixelDensity = Density(density = 1f, fontScale = 1f)

private const val LINE_HEIGHT_FACTOR = 1.18f
private const val SEARCH_STEPS = 7

/**
 * Lays out each translation to cover its block, in IMAGE pixels (so the result doesn't depend on
 * zoom and is drawn under the image transform). The font starts at the original text's line
 * height and shrinks until the text fits the block, down to [minFontPx]; below that it is
 * ellipsized. Blocks with no (or a blank) translation get null.
 */
internal fun layoutTranslations(
    measurer: TextMeasurer,
    blocks: List<TextBlock>,
    translations: List<String>,
    style: TextStyle,
    minFontPx: Float,
    layoutDirection: LayoutDirection,
): List<TranslationLayout?> = blocks.mapIndexed { index, block ->
    val text = translations.getOrNull(index)?.trim().orEmpty()
    if (text.isEmpty()) return@mapIndexed null
    layoutOne(measurer, block, text, style, minFontPx, layoutDirection)
}

private fun layoutOne(
    measurer: TextMeasurer,
    block: TextBlock,
    text: String,
    style: TextStyle,
    minFontPx: Float,
    layoutDirection: LayoutDirection,
): TranslationLayout {
    val b = block.bounds
    val lineHeight = typicalLineHeight(block)
    val pad = max(2f, lineHeight * 0.12f)
    val innerWidth = (b.width + pad).roundToInt().coerceAtLeast(1)
    val innerHeight = b.height + pad
    val minFont = max(1f, min(minFontPx, lineHeight))
    val maxFont = max(minFont, lineHeight * 0.82f)

    fun measure(fontPx: Float, maxLines: Int = Int.MAX_VALUE, overflow: TextOverflow = TextOverflow.Clip) =
        measurer.measure(
            text = text,
            style = style.copy(
                fontSize = fontPx.sp,
                lineHeight = (fontPx * LINE_HEIGHT_FACTOR).sp,
                letterSpacing = 0.sp,
            ),
            overflow = overflow,
            maxLines = maxLines,
            constraints = Constraints(maxWidth = innerWidth),
            layoutDirection = layoutDirection,
            density = ImagePixelDensity,
            skipCache = true,
        )

    fun fits(result: TextLayoutResult) = result.size.height <= innerHeight && !result.didOverflowWidth

    var best: TextLayoutResult = measure(maxFont)
    if (!fits(best)) {
        val atMin = measure(minFont)
        if (fits(atMin)) {
            // Binary search for the largest size that fits.
            best = atMin
            var low = minFont
            var high = maxFont
            repeat(SEARCH_STEPS) {
                val mid = (low + high) / 2f
                val result = measure(mid)
                if (fits(result)) {
                    best = result
                    low = mid
                } else {
                    high = mid
                }
            }
        } else {
            val lines = max(1, floor(innerHeight / (minFont * LINE_HEIGHT_FACTOR)).toInt())
            best = measure(minFont, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
    }

    val textHeight = best.size.height.toFloat()
    val textTop = b.top + (b.height - textHeight) / 2f
    val textLeft = b.left - pad / 2f
    val box = Rect(
        left = b.left - pad,
        top = min(b.top - pad, textTop - pad / 2f),
        right = b.right + pad,
        bottom = max(b.bottom + pad, textTop + textHeight + pad / 2f),
    )
    return TranslationLayout(box = box, text = best, textTopLeft = Offset(textLeft, textTop))
}

/** The block's median line height, or its height when it has no lines. */
private fun typicalLineHeight(block: TextBlock): Float {
    val heights = block.lines.map { it.bounds.height }.filter { it > 0 }.sorted()
    return if (heights.isEmpty()) block.bounds.height.toFloat().coerceAtLeast(1f)
    else heights[heights.size / 2].toFloat()
}
