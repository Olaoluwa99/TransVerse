package com.easit.aiscanner.ui.crop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntRect
import kotlin.math.max
import kotlin.math.min

/** A block of text found by OCR, in ORIGINAL (unrotated) image pixel coordinates. */
@Immutable
data class TextBlock(
    val text: String,
    val bounds: IntRect,
    /** The block's lines with their own bounds, for finer drawing (may be empty). */
    val lines: List<TextLine> = emptyList(),
)

@Immutable
data class TextLine(val text: String, val bounds: IntRect)

/** How the user is selecting: drawing a region, or tapping detected text blocks. */
enum class CropMode { REGION, TEXT }

/**
 * State of a [CropEditor] over an image of [imageWidth] × [imageHeight] pixels.
 *
 * Coordinates: [cropRect] and every [TextBlock.bounds] are in the ORIGINAL image's pixels.
 * [rotationDegrees] is applied to the output after cropping (see [cropBitmap]) and to how the
 * editor displays the image.
 */
@Stable
class CropEditorState(
    val imageWidth: Int,
    val imageHeight: Int,
    initialCrop: IntRect? = null,
) {
    /** The whole image. */
    val fullImage: IntRect = IntRect(0, 0, imageWidth, imageHeight)

    private val initial: IntRect = initialCrop?.let(::clamp) ?: fullImage

    /** The selected region (REGION mode), in original image pixels. Always within the image. */
    var cropRect: IntRect by mutableStateOf(initial)
        private set

    /** Output rotation: 0, 90, 180 or 270, clockwise. */
    var rotationDegrees: Int by mutableIntStateOf(0)
        private set

    var mode: CropMode by mutableStateOf(CropMode.REGION)

    private var textBlocksState: List<TextBlock> by mutableStateOf(emptyList())

    /**
     * Text blocks found by OCR; set when recognition finishes (empty until then). Replacing them
     * clears [selectedBlocks], whose indices would no longer match.
     */
    var textBlocks: List<TextBlock>
        get() = textBlocksState
        set(value) {
            if (value != textBlocksState) selectedBlocks = emptySet()
            textBlocksState = value
        }

    /** Indices into [textBlocks] the user selected in TEXT mode. */
    var selectedBlocks: Set<Int> by mutableStateOf(emptySet())
        private set

    /** Moves/resizes the region, clamped to the image and at least [MIN_SIZE_PX] per side. */
    fun updateCropRect(rect: IntRect) {
        cropRect = clamp(rect)
    }

    fun rotateClockwise() {
        rotationDegrees = (rotationDegrees + 90) % 360
    }

    /** Back to the initial region, no rotation and no selected blocks. */
    fun reset() {
        cropRect = initial
        rotationDegrees = 0
        selectedBlocks = emptySet()
    }

    fun toggleBlock(index: Int) {
        if (index !in textBlocks.indices) return
        selectedBlocks = if (index in selectedBlocks) selectedBlocks - index else selectedBlocks + index
    }

    fun selectAllBlocks() {
        selectedBlocks = textBlocks.indices.toSet()
    }

    fun clearBlockSelection() {
        selectedBlocks = emptySet()
    }

    /**
     * The blocks the current selection covers, in reading order (top to bottom, then left to
     * right): TEXT mode → the tapped blocks; REGION mode → blocks whose centre is in [cropRect].
     */
    val coveredBlocks: List<TextBlock> by derivedStateOf {
        val blocks = when (mode) {
            CropMode.TEXT -> selectedBlocks.sorted().mapNotNull { textBlocks.getOrNull(it) }
            CropMode.REGION -> textBlocks.filter { cropRect.contains(it.bounds.center) }
        }
        blocks.sortedWith(compareBy<TextBlock>({ it.bounds.top / READING_ROW_PX }, { it.bounds.left }))
    }

    /** The text the selection covers, blocks separated by blank lines. */
    val selectedText: String by derivedStateOf {
        coveredBlocks.joinToString("\n\n") { it.text.trim() }.trim()
    }

    /**
     * The image region to output: REGION mode → [cropRect]; TEXT mode → the padded union of the
     * selected blocks (or [cropRect] if none are selected).
     */
    val outputRect: IntRect by derivedStateOf {
        val union = if (mode == CropMode.TEXT) {
            selectedBlocks.mapNotNull { textBlocks.getOrNull(it)?.bounds }.reduceOrNull { a, b ->
                IntRect(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))
            }
        } else {
            null
        }
        if (union != null) {
            clamp(
                IntRect(
                    union.left - BLOCK_PADDING_PX,
                    union.top - BLOCK_PADDING_PX,
                    union.right + BLOCK_PADDING_PX,
                    union.bottom + BLOCK_PADDING_PX,
                )
            )
        } else {
            cropRect
        }
    }

    /** Whether there is something to output (a region, or at least one selected block). */
    val hasSelection: Boolean by derivedStateOf {
        mode == CropMode.REGION || selectedBlocks.any { it in textBlocks.indices }
    }

    private fun clamp(rect: IntRect): IntRect {
        val minSize = min(MIN_SIZE_PX, min(imageWidth, imageHeight))
        var left = rect.left.coerceIn(0, imageWidth - minSize)
        var top = rect.top.coerceIn(0, imageHeight - minSize)
        var right = rect.right.coerceIn(left + minSize, imageWidth)
        var bottom = rect.bottom.coerceIn(top + minSize, imageHeight)
        if (right - left < minSize) left = (right - minSize).coerceAtLeast(0)
        if (bottom - top < minSize) top = (bottom - minSize).coerceAtLeast(0)
        right = max(right, left + minSize).coerceAtMost(imageWidth)
        bottom = max(bottom, top + minSize).coerceAtMost(imageHeight)
        return IntRect(left, top, right, bottom)
    }

    companion object {
        /** Smallest region side, in image pixels. */
        const val MIN_SIZE_PX = 48
        private const val BLOCK_PADDING_PX = 12
        /** Blocks whose tops are within this many pixels count as the same row when ordering. */
        private const val READING_ROW_PX = 24
    }
}

/** Remembers a [CropEditorState] for an image of the given size (new state for a new size). */
@Composable
fun rememberCropEditorState(
    imageWidth: Int,
    imageHeight: Int,
    initialCrop: IntRect? = null,
): CropEditorState = remember(imageWidth, imageHeight) {
    CropEditorState(imageWidth, imageHeight, initialCrop)
}
