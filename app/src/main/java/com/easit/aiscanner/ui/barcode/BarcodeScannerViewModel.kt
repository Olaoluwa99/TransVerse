package com.easit.aiscanner.ui.barcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Camera permission as far as the scanner knows it. */
enum class CameraPermission {
    /** Not checked yet (first frame). */
    Unknown,

    /** Not granted; asking again can still show the system dialog. */
    Denied,

    /** Denied with "don't ask again" (or by policy); only app settings can change it. */
    PermanentlyDenied,
    Granted,
}

@Immutable
data class BarcodeScannerUiState(
    val permission: CameraPermission = CameraPermission.Unknown,
    /** The code being shown; while non-null, analysis is paused. */
    val result: ScannedCode? = null,
    val cameraError: Boolean = false,
    val torchOn: Boolean = false,
    val hasFlashUnit: Boolean = false,
    val canSwitchCamera: Boolean = false,
    val useFrontCamera: Boolean = false,
) {
    val isScanning: Boolean
        get() = permission == CameraPermission.Granted && result == null && !cameraError
}

/**
 * State for the live scanner. Scans are deliberately not written to history: a code is only
 * kept in memory until the user scans again or leaves.
 */
@HiltViewModel
class BarcodeScannerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(BarcodeScannerUiState())
    val uiState: StateFlow<BarcodeScannerUiState> = _uiState.asStateFlow()

    /** True once the system permission dialog has been launched automatically on entry. */
    var hasAutoRequestedPermission: Boolean = false
        private set

    fun onAutoPermissionRequest() {
        hasAutoRequestedPermission = true
    }

    /** Re-check on entry and on resume (the user may have changed it in Settings). */
    fun onPermissionChecked(granted: Boolean) {
        _uiState.update { state ->
            when {
                granted -> state.copy(permission = CameraPermission.Granted)
                // Keep a known denial; otherwise the rationale screen asks again.
                state.permission == CameraPermission.PermanentlyDenied -> state
                else -> state.copy(permission = CameraPermission.Denied)
            }
        }
    }

    fun onPermissionResult(granted: Boolean, canAskAgain: Boolean) {
        _uiState.update {
            it.copy(
                permission = when {
                    granted -> CameraPermission.Granted
                    canAskAgain -> CameraPermission.Denied
                    else -> CameraPermission.PermanentlyDenied
                },
            )
        }
    }

    /** Accepts the first code of a scanning pass. Returns true if [code] is now shown. */
    fun onCodeDetected(code: ScannedCode): Boolean {
        var accepted = false
        _uiState.update { state ->
            accepted = state.isScanning
            if (accepted) state.copy(result = code) else state
        }
        return accepted
    }

    fun scanAgain() {
        _uiState.update { it.copy(result = null) }
    }

    fun onTorchStateChanged(on: Boolean) {
        _uiState.update { it.copy(torchOn = on) }
    }

    fun onCameraReady(hasFlashUnit: Boolean, canSwitchCamera: Boolean, useFrontCamera: Boolean) {
        _uiState.update {
            it.copy(
                hasFlashUnit = hasFlashUnit,
                canSwitchCamera = canSwitchCamera,
                useFrontCamera = useFrontCamera,
                cameraError = false,
            )
        }
    }

    fun onFlashUnitChanged(hasFlashUnit: Boolean) {
        _uiState.update { it.copy(hasFlashUnit = hasFlashUnit, torchOn = it.torchOn && hasFlashUnit) }
    }

    fun switchCamera() {
        _uiState.update { it.copy(useFrontCamera = !it.useFrontCamera, torchOn = false) }
    }

    fun onCameraError() {
        _uiState.update { it.copy(cameraError = true) }
    }
}
