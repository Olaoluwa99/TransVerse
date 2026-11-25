package com.easit.aiscanner.ui.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easit.aiscanner.R
import com.easit.aiscanner.scannerAI.MlKitModules
import com.easit.aiscanner.scannerAI.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The crop editor: shows [image] (rotated by [CropEditorState.rotationDegrees]) fitted inside
 * [contentPadding], with pinch-zoom/pan, and either an adjustable region (REGION mode: corner and
 * edge handles, drag to move, magnifier while dragging a handle) or tappable highlighted text
 * blocks (TEXT mode). The image is dimmed outside the selection.
 *
 * The editor fills the space it is given and clips to it; [image] is drawn at the state's
 * logical size, so a host may pass a downscaled bitmap of a large original.
 *
 * @param blockTranslations when non-null, parallel to [CropEditorState.textBlocks]: draws each
 *   translation over its block on the image ("translate on image"). Empty strings are skipped.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CropEditor(
    image: ImageBitmap,
    state: CropEditorState,
    modifier: Modifier = Modifier,
    blockTranslations: List<String>? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val viewport = remember(state) { CropViewport(state) }
    val tools = remember(density) { CropDrawTools(density) }

    val insets = with(density) {
        ViewInsets(
            left = contentPadding.calculateLeftPadding(layoutDirection).toPx(),
            top = contentPadding.calculateTopPadding().toPx(),
            right = contentPadding.calculateRightPadding(layoutDirection).toPx(),
            bottom = contentPadding.calculateBottomPadding().toPx(),
        )
    }
    SideEffect { viewport.insets = insets }

    // Rotation: the layout snaps to the new angle and a graphics layer turns from the old angle
    // (and scale) to it, so hit testing never deals with in-between angles.
    val turn = remember(state) { Animatable(0f) }
    val turnScale = remember(state) { Animatable(1f) }
    val shownRotation = remember(state) { intArrayOf(state.rotationDegrees) }
    LaunchedEffect(state, state.rotationDegrees) {
        val old = shownRotation[0]
        val new = state.rotationDegrees
        if (old == new) return@LaunchedEffect
        shownRotation[0] = new
        val oldScale = viewport.fitScaleFor(old) * viewport.zoom
        viewport.cancelAnimation()
        viewport.snapHome()
        val newScale = viewport.fitScaleFor(new)
        val delta = normalizeRotation(new - old)
        turn.snapTo(if (delta <= 180) -delta.toFloat() else (360 - delta).toFloat())
        turnScale.snapTo(if (oldScale > 0f && newScale > 0f) oldScale / newScale else 1f)
        launch { turn.animateTo(0f, motion.defaultSpatialSpec()) }
        launch { turnScale.animateTo(1f, motion.defaultSpatialSpec()) }
    }

    // The host changed the region (e.g. Reset): show the whole image again.
    LaunchedEffect(state, viewport) {
        snapshotFlow { state.cropRect }.drop(1).collect { rect ->
            if (rect != viewport.lastEditorRect && viewport.activeSides == Sides.NONE && viewport.zoom > 1f) {
                viewport.launchAnimation(scope) {
                    animateZoomTo(CropViewport.MIN_ZOOM, area.center, motion.defaultSpatialSpec())
                }
            }
        }
    }

    val regionAmount by animateFloatAsState(
        targetValue = if (state.mode == CropMode.REGION) 1f else 0f,
        animationSpec = motion.defaultEffectsSpec(),
        label = "cropMode",
    )
    val gridAmount by animateFloatAsState(
        targetValue = if (viewport.activeSides != Sides.NONE) 1f else 0f,
        animationSpec = motion.fastEffectsSpec(),
        label = "cropGrid",
    )
    val loupeAmount by animateFloatAsState(
        targetValue = if (Sides.isHandle(viewport.activeSides)) 1f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "cropLoupe",
    )

    // Per-block selection progress, for the highlight and badge animation.
    val blocks = state.textBlocks
    val selection = state.selectedBlocks
    val blockAmounts = remember(blocks) {
        List(blocks.size) { Animatable(if (it in state.selectedBlocks) 1f else 0f) }
    }
    LaunchedEffect(blockAmounts, selection) {
        blockAmounts.forEachIndexed { index, amount ->
            val target = if (index in selection) 1f else 0f
            if (amount.targetValue != target) {
                // Outer scope: a later selection change must not cancel this block's animation.
                scope.launch { amount.animateTo(target, motion.fastSpatialSpec()) }
            }
        }
    }

    // Translate on image: text laid out once in image pixels, independent of zoom.
    val textMeasurer = rememberTextMeasurer()
    val translationStyle = MaterialTheme.typography.bodyLarge
    val fit = viewport.fitScaleFor(state.rotationDegrees)
    // Rounded so small layout changes (e.g. insets animating) don't re-lay out every frame.
    val minFontPx = if (fit > 0f) {
        (with(density) { MIN_TRANSLATION_SP.sp.toPx() } / fit * 2f).roundToInt().coerceAtLeast(1) / 2f
    } else {
        0f
    }
    val translationLayouts = remember(blocks, blockTranslations, translationStyle, minFontPx, layoutDirection) {
        if (blockTranslations == null || minFontPx <= 0f) {
            null
        } else {
            layoutTranslations(textMeasurer, blocks, blockTranslations, translationStyle, minFontPx, layoutDirection)
        }
    }
    val inPreview = LocalInspectionMode.current
    val translationAmount = remember { Animatable(if (inPreview) 1f else 0f) }
    val showTranslations = translationLayouts != null
    LaunchedEffect(showTranslations) {
        if (showTranslations) {
            translationAmount.animateTo(1f, motion.defaultEffectsSpec())
        } else {
            translationAmount.snapTo(0f)
        }
    }

    val checkPainter = rememberVectorPainter(Icons.Rounded.Check)
    val checkTint = remember(colors.onPrimary) { ColorFilter.tint(colors.onPrimary) }
    val scrim = colors.scrim
    val primary = colors.primary
    val onPrimary = colors.onPrimary
    val surface = colors.surfaceContainerHighest
    val onSurface = colors.onSurface

    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { viewport.size = it }
            .cropGestures(state, viewport, scope) { motion.defaultSpatialSpec() }
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val size = viewport.size
                    if (size.width > 0 && size.height > 0) {
                        val pivot = viewport.area.center
                        transformOrigin = TransformOrigin(pivot.x / size.width, pivot.y / size.height)
                    }
                    rotationZ = turn.value
                    scaleX = turnScale.value
                    scaleY = turnScale.value
                }
                .drawBehind {
                    val t = viewport.transform ?: return@drawBehind
                    drawImageWith(image, t)
                    val region = regionAmount.coerceIn(0f, 1f)
                    val text = 1f - region
                    if (text > 0f && translationLayouts == null) {
                        val bounds = t.imageBounds()
                        drawRect(scrim, bounds.topLeft, bounds.size, alpha = TEXT_DIM_ALPHA * text)
                    }
                    if (translationLayouts != null) {
                        drawTranslations(t, translationLayouts, tools, surface, onSurface, translationAmount.value)
                    }
                    drawRegion(
                        t = t,
                        crop = state.cropRect,
                        tools = tools,
                        scrim = scrim,
                        alpha = region,
                        gridAlpha = gridAmount.coerceIn(0f, 1f),
                        activeSides = viewport.activeSides,
                        withHandles = true,
                    )
                    if (text > 0f) {
                        drawBlocks(
                            t = t,
                            blocks = state.textBlocks,
                            selection = { index -> blockAmounts.getOrNull(index)?.value ?: 0f },
                            tools = tools,
                            primary = primary,
                            onPrimary = onPrimary,
                            check = checkPainter,
                            checkTint = checkTint,
                            alpha = text,
                            translated = translationLayouts != null,
                        )
                    }
                    if (region > 0f && loupeAmount > 0.001f && Sides.isHandle(viewport.loupeSides)) {
                        drawLoupe(
                            image = image,
                            t = t,
                            crop = state.cropRect,
                            finger = viewport.fingerPosition,
                            sides = viewport.loupeSides,
                            tools = tools,
                            scrim = scrim,
                            amount = loupeAmount * region,
                        )
                    }
                }
        )
        when (state.mode) {
            CropMode.REGION -> RegionSemantics(state, viewport)
            CropMode.TEXT -> BlockSemantics(state, viewport, blockTranslations)
        }
    }
}

/** Translations never shrink below this on screen (at fit zoom) before they are ellipsized. */
private const val MIN_TRANSLATION_SP = 8f

/**
 * Editor controls as a Material 3 Expressive floating toolbar: Region / Text mode, rotate,
 * reset, and select-all/clear in TEXT mode. Hosts put their own primary action next to it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CropEditorControls(
    state: CropEditorState,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val isText = state.mode == CropMode.TEXT
    HorizontalFloatingToolbar(expanded = true, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeButton(
                checked = !isText,
                onClick = { state.mode = CropMode.REGION },
                icon = Icons.Rounded.Crop,
                label = stringResource(R.string.crop_mode_region),
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            )
            ModeButton(
                checked = isText,
                onClick = { state.mode = CropMode.TEXT },
                icon = Icons.Rounded.TextFields,
                label = stringResource(R.string.crop_mode_text),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            )
        }
        Spacer(Modifier.width(8.dp))
        AnimatedVisibility(
            visible = isText,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandHorizontally(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkHorizontally(motion.defaultSpatialSpec()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = state::selectAllBlocks,
                    enabled = state.textBlocks.isNotEmpty() && state.selectedBlocks.size < state.textBlocks.size,
                ) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.crop_select_all))
                }
                IconButton(
                    onClick = state::clearBlockSelection,
                    enabled = state.selectedBlocks.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.Deselect, contentDescription = stringResource(R.string.crop_clear_selection))
                }
            }
        }
        IconButton(onClick = state::rotateClockwise) {
            Icon(RotateClockwiseIcon, contentDescription = stringResource(R.string.crop_rotate))
        }
        IconButton(onClick = state::reset) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.crop_reset))
        }
    }
}

/** Not the auto-mirrored icon: the rotation is always clockwise, also in RTL layouts. */
@Suppress("DEPRECATION")
private val RotateClockwiseIcon: ImageVector
    get() = Icons.Rounded.RotateRight

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeButton(
    checked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    shapes: ToggleButtonShapes,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = shapes,
        modifier = Modifier.semantics { role = Role.RadioButton },
    ) {
        Icon(icon, contentDescription = label)
    }
}

/**
 * Runs ML Kit text recognition on [bitmap] (waiting for the Play services module via [modules]
 * on first use) and returns its text blocks in [bitmap]'s pixel coordinates. Call off the main
 * thread is not required; ML Kit works asynchronously.
 */
suspend fun recognizeTextBlocks(
    bitmap: Bitmap,
    recognizer: TextRecognizer,
    modules: MlKitModules,
): List<TextBlock> {
    modules.ensureInstalled(listOf(recognizer))
    val text = MlKitModules.retryWhileUnavailable {
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
    }
    val width = bitmap.width
    val height = bitmap.height
    return text.textBlocks.mapNotNull { block ->
        val bounds = block.boundingBox?.toClampedIntRect(width, height) ?: return@mapNotNull null
        val lines = block.lines.mapNotNull { line ->
            line.boundingBox?.toClampedIntRect(width, height)?.let { TextLine(line.text, it) }
        }
        TextBlock(text = block.text, bounds = bounds, lines = lines)
    }
}

/** This rect clamped to a [width] × [height] bitmap; null if nothing of it is inside. */
private fun android.graphics.Rect.toClampedIntRect(width: Int, height: Int): IntRect? {
    val l = left.coerceIn(0, width)
    val t = top.coerceIn(0, height)
    val r = right.coerceIn(0, width)
    val b = bottom.coerceIn(0, height)
    return if (r > l && b > t) IntRect(l, t, r, b) else null
}

/** Crops [source] to [rect] (original image pixels), then rotates by [rotationDegrees]. */
fun cropBitmap(source: Bitmap, rect: IntRect, rotationDegrees: Int): Bitmap {
    val left = rect.left.coerceIn(0, source.width - 1)
    val top = rect.top.coerceIn(0, source.height - 1)
    val right = rect.right.coerceIn(left + 1, source.width)
    val bottom = rect.bottom.coerceIn(top + 1, source.height)
    val degrees = normalizeRotation(rotationDegrees)
    val matrix = if (degrees != 0) Matrix().apply { postRotate(degrees.toFloat()) } else null
    val result = Bitmap.createBitmap(source, left, top, right - left, bottom - top, matrix, true)
    // createBitmap may hand back the source itself (whole, unrotated, immutable): never share it.
    return if (result === source) source.copy(source.config ?: Bitmap.Config.ARGB_8888, false) else result
}
