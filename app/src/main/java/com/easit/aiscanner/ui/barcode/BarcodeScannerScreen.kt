package com.easit.aiscanner.ui.barcode

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.easit.aiscanner.R
import com.easit.aiscanner.ui.theme.TransVerseTheme
import kotlinx.coroutines.launch

/** Live barcode/QR scanner. [onTranslateText] sends a scanned value to the Translate screen. */
@Composable
fun BarcodeScannerScreen(
    onBack: () -> Unit,
    onTranslateText: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BarcodeScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val activity = context.findActivity()
        val canAskAgain = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
        viewModel.onPermissionResult(granted, canAskAgain)
    }

    // Re-check on every resume: the user may come back from app settings.
    LifecycleResumeEffect(Unit) {
        viewModel.onPermissionChecked(context.hasCameraPermission())
        onPauseOrDispose { }
    }

    // The user opened the scanner on purpose, so ask straight away (once), like before.
    LaunchedEffect(state.permission) {
        if (state.permission == CameraPermission.Denied && !viewModel.hasAutoRequestedPermission) {
            viewModel.onAutoPermissionRequest()
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when {
        state.permission == CameraPermission.Granted && !state.cameraError -> {
            val controller = rememberScannerCameraController()
            ScannerCameraContent(
                state = state,
                onBack = onBack,
                onToggleTorch = { controller.enableTorch(!state.torchOn) },
                onSwitchCamera = viewModel::switchCamera,
                modifier = modifier,
                preview = { previewModifier ->
                    BarcodeCameraPreview(
                        controller = controller,
                        scanning = state.isScanning,
                        useFrontCamera = state.useFrontCamera,
                        onBarcode = { barcode ->
                            val code = barcode.toScannedCode()
                            if (code != null && viewModel.onCodeDetected(code)) {
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            }
                        },
                        onCameraReady = viewModel::onCameraReady,
                        onFlashUnitChanged = viewModel::onFlashUnitChanged,
                        onTorchStateChanged = viewModel::onTorchStateChanged,
                        onCameraError = viewModel::onCameraError,
                        modifier = previewModifier,
                    )
                },
            )
            state.result?.let { code ->
                BarcodeResultSheet(
                    code = code,
                    onDismiss = viewModel::scanAgain,
                    onTranslateText = onTranslateText,
                )
            }
        }
        state.permission == CameraPermission.Unknown -> {
            // First frame only; avoid flashing the rationale before the check runs.
            Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        }
        else -> ScannerMessageContent(
            kind = when {
                state.cameraError -> ScannerMessage.CameraError
                state.permission == CameraPermission.PermanentlyDenied -> ScannerMessage.PermissionBlocked
                else -> ScannerMessage.PermissionNeeded
            },
            onBack = onBack,
            onAllowCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { openAppSettings(context) },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodeResultSheet(
    code: ScannedCode,
    onDismiss: () -> Unit,
    onTranslateText: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { after ->
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) after()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BarcodeResultContent(
            code = code,
            onPrimaryAction = { runPrimaryAction(context, code) },
            onCopy = { text, sensitive -> copyToClipboard(context, text, sensitive) },
            onShare = { shareText(context, code.copyText) },
            onTranslate = code.translatableText?.let { text -> { onTranslateText(text) } },
            onScanAgain = { hideThen(onDismiss) },
        )
    }
}

/**
 * Full-screen camera with the reticle overlay and translucent controls. The camera itself is
 * passed in as [preview] so this layout can be previewed without CameraX.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScannerCameraContent(
    state: BarcodeScannerUiState,
    onBack: () -> Unit,
    onToggleTorch: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable (Modifier) -> Unit,
) {
    KeepScreenOnWithLightSystemBars()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim),
    ) {
        preview(Modifier.fillMaxSize())
        ScannerOverlay(scanning = state.isScanning, modifier = Modifier.fillMaxSize())

        val chromeColors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack, colors = chromeColors) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(visible = state.canSwitchCamera, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                FilledTonalIconButton(onClick = onSwitchCamera, colors = chromeColors) {
                    Icon(Icons.Rounded.Cameraswitch, contentDescription = stringResource(R.string.barcode_switch_camera))
                }
            }
            AnimatedVisibility(visible = state.hasFlashUnit, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                FilledTonalIconToggleButton(
                    checked = state.torchOn,
                    onCheckedChange = { onToggleTorch() },
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = if (state.torchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = stringResource(R.string.barcode_flashlight),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = state.isScanning,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
            ) {
                Text(
                    text = stringResource(R.string.barcode_hint),
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/** Keeps the display awake while scanning and uses light status/nav icons over the camera. */
@Composable
private fun KeepScreenOnWithLightSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            view.keepScreenOn = false
            if (controller != null) {
                lightStatus?.let { controller.isAppearanceLightStatusBars = it }
                lightNav?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}

private enum class ScannerMessage(
    val icon: ImageVector,
    val title: Int,
    val body: Int,
) {
    PermissionNeeded(Icons.Rounded.PhotoCamera, R.string.barcode_permission_title, R.string.barcode_permission_body),
    PermissionBlocked(Icons.Rounded.PhotoCamera, R.string.barcode_permission_title, R.string.barcode_permission_denied_body),
    CameraError(Icons.Rounded.NoPhotography, R.string.barcode_camera_error_title, R.string.barcode_camera_error_body),
}

/** Permission rationale / blocked / error state: shape-framed icon, headline, body, action. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScannerMessageContent(
    kind: ScannerMessage,
    onBack: () -> Unit,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.barcode_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(
                        if (kind == ScannerMessage.CameraError) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = kind.icon,
                    contentDescription = null,
                    tint = if (kind == ScannerMessage.CameraError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.size(32.dp))
            Text(
                text = stringResource(kind.title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = stringResource(kind.body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Spacer(Modifier.size(32.dp))
            when (kind) {
                ScannerMessage.PermissionNeeded -> MessageButton(
                    icon = Icons.Rounded.PhotoCamera,
                    label = stringResource(R.string.barcode_action_allow_camera),
                    onClick = onAllowCamera,
                )
                ScannerMessage.PermissionBlocked -> MessageButton(
                    icon = Icons.Rounded.Settings,
                    label = stringResource(R.string.barcode_action_open_settings),
                    onClick = onOpenSettings,
                )
                ScannerMessage.CameraError -> Unit
            }
        }
    }
}

@Composable
private fun MessageButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val height = ButtonDefaults.MediumContainerHeight
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = Modifier.heightIn(min = height),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
        Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
        Text(label, style = ButtonDefaults.textStyleFor(height))
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// region Previews

@Preview(showBackground = true, name = "Permission needed")
@Composable
private fun PermissionNeededPreview() {
    TransVerseTheme {
        ScannerMessageContent(ScannerMessage.PermissionNeeded, onBack = {}, onAllowCamera = {}, onOpenSettings = {})
    }
}

@Preview(showBackground = true, name = "Permission blocked")
@Composable
private fun PermissionBlockedPreview() {
    TransVerseTheme {
        ScannerMessageContent(ScannerMessage.PermissionBlocked, onBack = {}, onAllowCamera = {}, onOpenSettings = {})
    }
}

@Preview(showBackground = true, name = "Camera error")
@Composable
private fun CameraErrorPreview() {
    TransVerseTheme {
        ScannerMessageContent(ScannerMessage.CameraError, onBack = {}, onAllowCamera = {}, onOpenSettings = {})
    }
}

@Preview(name = "Scanning", widthDp = 360, heightDp = 720)
@Composable
private fun ScanningPreview() {
    TransVerseTheme {
        ScannerCameraContent(
            state = BarcodeScannerUiState(
                permission = CameraPermission.Granted,
                hasFlashUnit = true,
                canSwitchCamera = true,
            ),
            onBack = {},
            onToggleTorch = {},
            onSwitchCamera = {},
            preview = { previewModifier ->
                Box(previewModifier.background(MaterialTheme.colorScheme.surfaceVariant))
            },
        )
    }
}

// endregion
