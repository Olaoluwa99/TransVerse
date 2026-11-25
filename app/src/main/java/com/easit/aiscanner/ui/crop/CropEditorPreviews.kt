package com.easit.aiscanner.ui.crop

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.easit.aiscanner.ui.theme.TransVerseTheme

private const val PREVIEW_WIDTH = 1080
private const val PREVIEW_HEIGHT = 1500

private val PreviewBlocks = listOf(
    TextBlock("Bienvenue à Paris", IntRect(90, 210, 830, 300)),
    TextBlock("Le musée ouvre à 9h", IntRect(90, 430, 900, 500)),
    TextBlock("Billets en vente ici", IntRect(90, 610, 860, 680)),
    TextBlock("Sortie", IntRect(690, 1180, 980, 1270)),
)

private val PreviewTranslations = listOf(
    "Welcome to Paris",
    "The museum opens at 9 am",
    "Tickets on sale here",
    "Exit",
)

/** A generated "photo": a gradient with the preview blocks' text painted where they are. */
private fun previewBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, PREVIEW_WIDTH.toFloat(), PREVIEW_HEIGHT.toFloat(),
        0xFF27476E.toInt(), 0xFF8C5E7A.toInt(), Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, PREVIEW_WIDTH.toFloat(), PREVIEW_HEIGHT.toFloat(), paint)
    paint.shader = null
    paint.color = 0xFFF5F1E8.toInt()
    PreviewBlocks.forEach { block ->
        val b = block.bounds
        paint.textSize = b.height * 0.8f
        canvas.drawText(block.text, b.left.toFloat(), b.bottom - b.height * 0.18f, paint)
    }
    return bitmap
}

@Composable
private fun PreviewFrame(content: @Composable BoxScope.() -> Unit) {
    TransVerseTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            content()
        }
    }
}

private val PreviewPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 104.dp)

@Preview(name = "Crop editor – region", widthDp = 360, heightDp = 640)
@Composable
private fun CropEditorRegionPreview() {
    val image = remember { previewBitmap().asImageBitmap() }
    val state = remember {
        CropEditorState(PREVIEW_WIDTH, PREVIEW_HEIGHT, initialCrop = IntRect(60, 160, 960, 740))
    }
    PreviewFrame {
        CropEditor(image, state, Modifier.fillMaxSize(), contentPadding = PreviewPadding)
        CropEditorControls(
            state,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Preview(name = "Crop editor – text blocks", widthDp = 360, heightDp = 640)
@Composable
private fun CropEditorTextPreview() {
    val image = remember { previewBitmap().asImageBitmap() }
    val state = remember {
        CropEditorState(PREVIEW_WIDTH, PREVIEW_HEIGHT).apply {
            mode = CropMode.TEXT
            textBlocks = PreviewBlocks
            toggleBlock(0)
            toggleBlock(2)
        }
    }
    PreviewFrame {
        CropEditor(image, state, Modifier.fillMaxSize(), contentPadding = PreviewPadding)
        CropEditorControls(
            state,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

@Preview(name = "Crop editor – translate on image", widthDp = 360, heightDp = 640)
@Composable
private fun CropEditorTranslatedPreview() {
    val image = remember { previewBitmap().asImageBitmap() }
    val state = remember {
        CropEditorState(PREVIEW_WIDTH, PREVIEW_HEIGHT).apply {
            mode = CropMode.TEXT
            textBlocks = PreviewBlocks
            selectAllBlocks()
        }
    }
    PreviewFrame {
        CropEditor(
            image = image,
            state = state,
            modifier = Modifier.fillMaxSize(),
            blockTranslations = PreviewTranslations,
            contentPadding = PreviewPadding,
        )
    }
}
