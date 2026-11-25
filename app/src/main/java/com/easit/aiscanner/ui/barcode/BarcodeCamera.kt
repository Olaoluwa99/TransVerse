package com.easit.aiscanner.ui.barcode

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.TorchState
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.interfaces.Detector
import kotlin.math.hypot
import kotlin.math.min

private const val TAG = "BarcodeCamera"

/** Largest reticle edge, so the frame doesn't swallow the whole screen on tablets. */
private val MaxReticleWidth = 340.dp
private val ReticleCornerRadius = 28.dp

/** Creates the camera controller for the scanner; preview + analysis only. */
@Composable
internal fun rememberScannerCameraController(): LifecycleCameraController {
    val context = LocalContext.current
    return remember(context) {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }
}

/**
 * CameraX preview with ML Kit barcode analysis.
 *
 * @param scanning whether frames are analysed; analysis is detached while a result is shown
 * @param useFrontCamera which lens to use (only honoured when the device has both)
 * @param onBarcode first code found inside the reticle
 * @param onCameraReady capabilities once CameraX has initialised
 * @param onFlashUnitChanged whether the current lens has a flash (changes when switching lens)
 * @param onTorchStateChanged the real torch state, which CameraX resets when it rebinds
 * @param onCameraError CameraX could not initialise or there is no usable camera
 */
@Composable
internal fun BarcodeCameraPreview(
    controller: LifecycleCameraController,
    scanning: Boolean,
    useFrontCamera: Boolean,
    onBarcode: (Barcode) -> Unit,
    onCameraReady: (hasFlashUnit: Boolean, canSwitchCamera: Boolean, useFrontCamera: Boolean) -> Unit,
    onFlashUnitChanged: (Boolean) -> Unit,
    onTorchStateChanged: (Boolean) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val maxReticleWidthPx = with(LocalDensity.current) { MaxReticleWidth.toPx() }

    val currentOnBarcode by rememberUpdatedState(onBarcode)
    val currentOnReady by rememberUpdatedState(onCameraReady)
    val currentOnTorch by rememberUpdatedState(onTorchStateChanged)
    val currentOnError by rememberUpdatedState(onCameraError)
    val currentOnFlashUnit by rememberUpdatedState(onFlashUnitChanged)

    val previewView = remember(context) {
        PreviewView(context).apply {
            // TextureView-backed: behaves with Compose transitions and overlays.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            this.controller = controller
        }
    }

    // Play-services barcode scanning; all formats.
    val scanner = remember { BarcodeScanning.getClient() }
    DisposableEffect(scanner) {
        onDispose { scanner.close() }
    }

    val analyzer = remember(scanner, previewView) {
        val detectors: List<Detector<*>> = listOf(scanner)
        MlKitAnalyzer(
            detectors,
            ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
            ContextCompat.getMainExecutor(context),
        ) { result ->
            val barcodes: List<Barcode> = result.getValue(scanner).orEmpty()
            if (barcodes.isNotEmpty()) {
                val reticle = reticleRect(
                    Size(previewView.width.toFloat(), previewView.height.toFloat()),
                    maxReticleWidthPx,
                )
                selectBarcode(barcodes, reticle)?.let { currentOnBarcode(it) }
            }
        }
    }

    var initialized by remember(controller) { mutableStateOf(false) }

    DisposableEffect(controller, lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        val torchObserver = Observer<Int> { state -> currentOnTorch(state == TorchState.ON) }
        controller.torchState.observe(lifecycleOwner, torchObserver)

        var disposed = false
        val init = controller.initializationFuture
        init.addListener(
            {
                if (disposed) return@addListener
                try {
                    init.get()
                    val hasBack = controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val hasFront = controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    if (!hasBack && !hasFront) {
                        currentOnError()
                    } else {
                        val front = !hasBack || (useFrontCamera && hasFront)
                        controller.cameraSelector =
                            if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        initialized = true
                        currentOnReady(
                            controller.cameraInfo?.hasFlashUnit() == true,
                            hasBack && hasFront,
                            front,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Camera initialisation failed", e)
                    currentOnError()
                }
            },
            ContextCompat.getMainExecutor(context),
        )

        onDispose {
            disposed = true
            controller.torchState.removeObserver(torchObserver)
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    // Lens switching after initialisation.
    LaunchedEffect(controller, initialized, useFrontCamera) {
        if (!initialized) return@LaunchedEffect
        val selector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        if (controller.cameraSelector != selector && controller.hasCamera(selector)) {
            controller.cameraSelector = selector
        }
        currentOnFlashUnit(controller.cameraInfo?.hasFlashUnit() == true)
    }

    // Pause analysis while a result is shown; resume on "Scan again".
    DisposableEffect(controller, analyzer, scanning) {
        if (scanning) {
            controller.setImageAnalysisAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
        } else {
            controller.clearImageAnalysisAnalyzer()
        }
        onDispose { }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/**
 * The reticle frame in a container of [size]: a slightly wide rounded rectangle, centred a
 * little above the middle so the hint and system bars don't crowd it.
 */
internal fun reticleRect(size: Size, maxWidthPx: Float): Rect {
    if (size.width <= 0f || size.height <= 0f) return Rect.Zero
    val width = min(min(size.width * 0.78f, size.height * 0.6f), maxWidthPx)
    val height = width * 0.82f
    val cx = size.width / 2f
    val cy = size.height * 0.45f
    return Rect(cx - width / 2f, cy - height / 2f, cx + width / 2f, cy + height / 2f)
}

/** Picks the code whose centre is inside the reticle and closest to its centre. */
private fun selectBarcode(barcodes: List<Barcode>, reticle: Rect): Barcode? {
    if (reticle == Rect.Zero) return barcodes.firstOrNull()
    return barcodes
        .mapNotNull { barcode ->
            val box = barcode.boundingBox ?: return@mapNotNull null
            val center = Offset(box.exactCenterX(), box.exactCenterY())
            if (!reticle.contains(center)) return@mapNotNull null
            barcode to hypot(center.x - reticle.center.x, center.y - reticle.center.y)
        }
        .minByOrNull { it.second }
        ?.first
}

/**
 * Dim scrim with a rounded cut-out and animated corner accents. The frame breathes gently while
 * scanning and settles, slightly enlarged and recoloured, once a code is found.
 */
@Composable
internal fun ScannerOverlay(
    scanning: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val maxWidthPx = with(density) { MaxReticleWidth.toPx() }
    val cornerRadiusPx = with(density) { ReticleCornerRadius.toPx() }
    val strokePx = with(density) { 5.dp.toPx() }
    val accentLengthPx = with(density) { 36.dp.toPx() }

    val breathing = rememberInfiniteTransition(label = "reticleBreathing")
    val breath by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breath",
    )
    val settledScale by animateFloatAsState(
        targetValue = if (scanning) 1f else 1.06f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "settledScale",
    )
    val breathAmount by animateFloatAsState(
        targetValue = if (scanning) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "breathAmount",
    )
    val accent by animateColorAsState(
        targetValue = if (scanning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "accent",
    )
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)

    Canvas(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val frame = reticleRect(size, maxWidthPx)
        if (frame == Rect.Zero) return@Canvas
        val frameScale = settledScale * (1f + (breath - 1f) * breathAmount)

        drawRect(scrim)
        scale(frameScale, pivot = frame.center) {
            drawRoundRect(
                color = Color.Transparent,
                topLeft = frame.topLeft,
                size = frame.size,
                cornerRadius = CornerRadius(cornerRadiusPx),
                blendMode = BlendMode.Clear,
            )
            drawCornerAccents(frame, cornerRadiusPx, accentLengthPx, strokePx, accent)
        }
    }
}

private fun DrawScope.drawCornerAccents(
    frame: Rect,
    radius: Float,
    length: Float,
    strokeWidth: Float,
    color: Color,
) {
    val d = radius * 2f
    val path = Path().apply {
        // Top-left
        moveTo(frame.left, frame.top + length)
        lineTo(frame.left, frame.top + radius)
        arcTo(Rect(frame.left, frame.top, frame.left + d, frame.top + d), 180f, 90f, false)
        lineTo(frame.left + length, frame.top)
        // Top-right
        moveTo(frame.right - length, frame.top)
        lineTo(frame.right - radius, frame.top)
        arcTo(Rect(frame.right - d, frame.top, frame.right, frame.top + d), 270f, 90f, false)
        lineTo(frame.right, frame.top + length)
        // Bottom-right
        moveTo(frame.right, frame.bottom - length)
        lineTo(frame.right, frame.bottom - radius)
        arcTo(Rect(frame.right - d, frame.bottom - d, frame.right, frame.bottom), 0f, 90f, false)
        lineTo(frame.right - length, frame.bottom)
        // Bottom-left
        moveTo(frame.left + length, frame.bottom)
        lineTo(frame.left + radius, frame.bottom)
        arcTo(Rect(frame.left, frame.bottom - d, frame.left + d, frame.bottom), 90f, 90f, false)
        lineTo(frame.left, frame.bottom - length)
    }
    drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
