package com.easit.aiscanner.floatingWindow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process coordination between the floating bubble ([Floater]), the consent activity
 * ([ImageTransparentActivity]) and the capture service ([ScreenCropping]). They all run in the
 * app's process, so plain state flows are enough. Touch from the main thread.
 */
object CaptureCoordinator {

    private val _bubbleRunning = MutableStateFlow(false)

    /** Whether the floating bubble service is running (capture sessions only live alongside it). */
    val bubbleRunning: StateFlow<Boolean> = _bubbleRunning.asStateFlow()

    private val _captureInProgress = MutableStateFlow(false)

    /**
     * True from the moment a capture is requested until its editor closes (or it fails). The
     * bubble hides meanwhile so it isn't in the screenshot.
     */
    val captureInProgress: StateFlow<Boolean> = _captureInProgress.asStateFlow()

    private val _sessionActive = MutableStateFlow(false)

    /** A MediaProjection is being kept open between captures (capture session mode). */
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private val _consentVisible = MutableStateFlow(false)

    /** The consent activity is on screen; the first frame must wait until it has gone. */
    val consentVisible: StateFlow<Boolean> = _consentVisible.asStateFlow()

    fun setBubbleRunning(running: Boolean) {
        _bubbleRunning.value = running
    }

    /** Claims the capture slot. @return false if a capture is already in progress. */
    fun beginCapture(): Boolean = _captureInProgress.compareAndSet(expect = false, update = true)

    /** Releases the capture slot; the bubble shows again. */
    fun endCapture() {
        _captureInProgress.value = false
    }

    fun setSessionActive(active: Boolean) {
        _sessionActive.value = active
    }

    fun setConsentVisible(visible: Boolean) {
        _consentVisible.value = visible
    }
}
