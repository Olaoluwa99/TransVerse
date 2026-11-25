package com.easit.aiscanner

import androidx.compose.ui.unit.IntRect
import com.easit.aiscanner.ui.crop.CropEditorState
import com.easit.aiscanner.ui.crop.CropMode
import com.easit.aiscanner.ui.crop.TextBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CropEditorStateTest {

    private fun state() = CropEditorState(imageWidth = 1000, imageHeight = 2000)

    private val blocks = listOf(
        TextBlock("second line", IntRect(100, 400, 600, 460)),
        TextBlock("first line", IntRect(100, 100, 600, 160)),
        TextBlock("far away", IntRect(700, 1800, 900, 1900)),
    )

    @Test
    fun cropRect_isClampedToImage_andKeepsMinimumSize() {
        val s = state()
        s.updateCropRect(IntRect(-50, -50, 5000, 5000))
        assertEquals(IntRect(0, 0, 1000, 2000), s.cropRect)

        s.updateCropRect(IntRect(500, 500, 501, 501))
        assertTrue(s.cropRect.width >= CropEditorState.MIN_SIZE_PX)
        assertTrue(s.cropRect.height >= CropEditorState.MIN_SIZE_PX)
    }

    @Test
    fun textMode_outputRect_isPaddedUnionOfSelectedBlocks() {
        val s = state()
        s.textBlocks = blocks
        s.mode = CropMode.TEXT
        s.toggleBlock(0)
        s.toggleBlock(1)
        // Union (100,100)-(600,460), padded by 12px.
        assertEquals(IntRect(88, 88, 612, 472), s.outputRect)
    }

    @Test
    fun selectedText_followsReadingOrder() {
        val s = state()
        s.textBlocks = blocks
        s.mode = CropMode.TEXT
        s.toggleBlock(0)
        s.toggleBlock(1)
        assertEquals("first line\n\nsecond line", s.selectedText)
    }

    @Test
    fun regionMode_coversBlocksWhoseCentreIsInside() {
        val s = state()
        s.textBlocks = blocks
        s.updateCropRect(IntRect(0, 0, 1000, 1000))
        assertEquals("first line\n\nsecond line", s.selectedText)
    }

    @Test
    fun replacingBlocks_clearsStaleSelection_withoutCrashing() {
        val s = state()
        s.textBlocks = blocks
        s.mode = CropMode.TEXT
        s.toggleBlock(2)
        s.textBlocks = blocks.take(1)
        assertTrue(s.selectedBlocks.isEmpty())
        assertFalse(s.hasSelection)
        assertEquals(s.cropRect, s.outputRect)
    }

    @Test
    fun reset_restoresInitialRegion_rotation_andSelection() {
        val s = state()
        s.textBlocks = blocks
        s.updateCropRect(IntRect(10, 10, 300, 300))
        s.rotateClockwise()
        s.toggleBlock(0)
        s.reset()
        assertEquals(IntRect(0, 0, 1000, 2000), s.cropRect)
        assertEquals(0, s.rotationDegrees)
        assertTrue(s.selectedBlocks.isEmpty())
    }

    @Test
    fun rotation_cyclesThroughQuarterTurns() {
        val s = state()
        repeat(4) { s.rotateClockwise() }
        assertEquals(0, s.rotationDegrees)
    }
}
