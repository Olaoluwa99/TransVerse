package com.easit.aiscanner.ui.app

import android.net.Uri

/** Content handed to the Translate screen from outside it (share sheet, overlay, scanner). */
sealed interface IncomingContent {
    /** Text from the share sheet, Android's "Process text" menu, or a scanned barcode. */
    data class Text(val text: String) : IncomingContent

    /** An image shared from another app; read it promptly, the URI grant is temporary. */
    data class SharedImage(val uri: Uri) : IncomingContent

    /**
     * A region captured by the floating screen-capture flow, already saved privately in
     * `filesDir` under [fileName] (`SCAN.<millis>.jpg`).
     */
    data class ScreenCapture(val fileName: String) : IncomingContent

    /** Speech transcribed by the floating voice capture. */
    data class Speech(val text: String) : IncomingContent
}
