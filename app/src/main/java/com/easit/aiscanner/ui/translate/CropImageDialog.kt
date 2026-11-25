package com.easit.aiscanner.ui.translate

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.crop.CropEditor
import com.easit.aiscanner.ui.crop.CropEditorControls
import com.easit.aiscanner.ui.crop.CropEditorState
import com.easit.aiscanner.ui.crop.CropMode
import com.easit.aiscanner.ui.crop.TextBlock
import com.easit.aiscanner.ui.theme.TransVerseTheme

/**
 * The crop step for camera photos, gallery picks and shared images: a full-screen dialog with
 * the crop editor, so it covers the app's bottom navigation bar too.
 *
 * The editor's selection (region, rotation, mode, tapped blocks) is saved here, in the screen's
 * composition rather than inside the dialog's own, so it survives rotation; the image and its
 * text blocks come from the ViewModel, which survives it anyway.
 *
 * @param onDone the region to output (original image pixels), the rotation to apply after
 *   cropping, and the OCR text the selection covers (may be blank)
 */
@Composable
internal fun CropImageDialog(
    session: CropSession,
    onCancel: () -> Unit,
    onDone: (outputRect: IntRect, rotationDegrees: Int, selectedText: String) -> Unit,
) {
    val bitmap = session.bitmap
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    val blocks = session.textBlocks
    val editorState = if (bitmap != null) {
        rememberSessionCropState(session.id, bitmap.width, bitmap.height, blocks)
    } else {
        null
    }
    if (editorState != null && blocks != null) {
        LaunchedEffect(editorState, blocks) {
            if (editorState.textBlocks != blocks) editorState.textBlocks = blocks
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        CropImageContent(
            image = image,
            editorState = editorState,
            findingText = session.findingText,
            onClose = onCancel,
            onDone = {
                if (editorState != null) {
                    onDone(editorState.outputRect, editorState.rotationDegrees, editorState.selectedText)
                }
            },
        )
    }
}

/**
 * A [CropEditorState] for crop session [sessionId], saved across configuration changes and
 * process death. A saved state from another session or image size is ignored.
 */
@Composable
private fun rememberSessionCropState(
    sessionId: Long,
    imageWidth: Int,
    imageHeight: Int,
    textBlocks: List<TextBlock>?,
): CropEditorState {
    val saver = remember(sessionId, imageWidth, imageHeight, textBlocks) {
        cropStateSaver(sessionId, imageWidth, imageHeight, textBlocks)
    }
    return rememberSaveable(sessionId, imageWidth, imageHeight, saver = saver) {
        CropEditorState(imageWidth, imageHeight)
    }
}

/**
 * Saves the user's choices as a LongArray: session id, image size, crop rect, rotation, mode,
 * then the selected block indices. Restoring replays them through the state's public API, so
 * [CropEditorState.reset] still returns to the whole image.
 */
private fun cropStateSaver(
    sessionId: Long,
    imageWidth: Int,
    imageHeight: Int,
    knownBlocks: List<TextBlock>?,
): Saver<CropEditorState, LongArray> = Saver(
    save = { state ->
        val rect = state.cropRect
        longArrayOf(
            sessionId,
            state.imageWidth.toLong(),
            state.imageHeight.toLong(),
            rect.left.toLong(),
            rect.top.toLong(),
            rect.right.toLong(),
            rect.bottom.toLong(),
            state.rotationDegrees.toLong(),
            state.mode.ordinal.toLong(),
        ) + state.selectedBlocks.map { it.toLong() }.toLongArray()
    },
    restore = { saved ->
        if (saved.size < SAVED_HEADER_SIZE ||
            saved[0] != sessionId ||
            saved[1] != imageWidth.toLong() ||
            saved[2] != imageHeight.toLong()
        ) {
            null
        } else {
            CropEditorState(imageWidth, imageHeight).apply {
                updateCropRect(IntRect(saved[3].toInt(), saved[4].toInt(), saved[5].toInt(), saved[6].toInt()))
                repeat((saved[7].toInt() / 90) % 4) { rotateClockwise() }
                mode = CropMode.entries.getOrElse(saved[8].toInt()) { CropMode.REGION }
                if (knownBlocks != null) {
                    textBlocks = knownBlocks
                    for (i in SAVED_HEADER_SIZE until saved.size) toggleBlock(saved[i].toInt())
                }
            }
        }
    },
)

private const val SAVED_HEADER_SIZE = 9

/** Stateless crop step: top bar (Close, title, Done), the editor, its controls and a status hint. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CropImageContent(
    image: ImageBitmap?,
    editorState: CropEditorState?,
    findingText: Boolean,
    onClose: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerLowest
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.translate_crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.translate_crop_close))
                    }
                },
                actions = {
                    Button(
                        onClick = onDone,
                        enabled = editorState?.hasSelection == true,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.translate_crop_done))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (image != null && editorState != null) {
                CropEditor(
                    image = image,
                    state = editorState,
                    modifier = Modifier.fillMaxSize(),
                    // Room above for the hint and below for the floating controls.
                    contentPadding = PaddingValues(start = 24.dp, top = 56.dp, end = 24.dp, bottom = 104.dp),
                )
                CropEditorControls(
                    state = editorState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            } else {
                val loading = stringResource(R.string.translate_crop_loading)
                ContainedLoadingIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .semantics { contentDescription = loading },
                )
            }
            AnimatedVisibility(
                visible = findingText,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            ) {
                FindingTextHint()
            }
        }
    }
}

/** "Finding text…" pill. Not a Surface, so it never swallows touches meant for the editor. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FindingTextHint(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LoadingIndicator(
            modifier = Modifier.size(32.dp),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = stringResource(R.string.translate_crop_finding_text),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

// region Previews

private fun previewImage(): ImageBitmap =
    Bitmap.createBitmap(PREVIEW_WIDTH, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(android.graphics.Color.LTGRAY) }
        .asImageBitmap()

private const val PREVIEW_WIDTH = 900
private const val PREVIEW_HEIGHT = 1200

@Preview(name = "Crop: finding text")
@Composable
private fun CropFindingTextPreview() {
    TransVerseTheme {
        CropImageContent(
            image = remember { previewImage() },
            editorState = remember { CropEditorState(PREVIEW_WIDTH, PREVIEW_HEIGHT) },
            findingText = true,
            onClose = {},
            onDone = {},
        )
    }
}

@Preview(name = "Crop: text blocks")
@Composable
private fun CropTextBlocksPreview() {
    TransVerseTheme {
        CropImageContent(
            image = remember { previewImage() },
            editorState = remember {
                CropEditorState(PREVIEW_WIDTH, PREVIEW_HEIGHT).apply {
                    textBlocks = listOf(
                        TextBlock("Bienvenue à Paris", IntRect(120, 200, 780, 300)),
                        TextBlock("Sortie", IntRect(120, 420, 420, 500)),
                    )
                    mode = CropMode.TEXT
                    toggleBlock(0)
                }
            },
            findingText = false,
            onClose = {},
            onDone = {},
        )
    }
}

@Preview(name = "Crop: opening image")
@Composable
private fun CropLoadingPreview() {
    TransVerseTheme {
        CropImageContent(image = null, editorState = null, findingText = false, onClose = {}, onDone = {})
    }
}

// endregion
