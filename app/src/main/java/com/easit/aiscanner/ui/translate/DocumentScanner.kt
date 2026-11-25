package com.easit.aiscanner.ui.translate

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * ML Kit's document scanner (Google Play services UI): finds the page edges, corrects the
 * perspective and cleans the image up, so a scanned page skips the crop editor.
 */
internal object DocumentScanner {

    private val options: GmsDocumentScannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setPageLimit(1)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build()

    /**
     * The scanner's start intent, to launch with
     * [androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult].
     * Fails when Play services or the scanner module isn't available.
     */
    fun startScanIntent(activity: Activity): Task<IntentSender> =
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)

    /** The scanned page's JPEG, from a successful scanner result; null if there is none. */
    fun scannedImage(data: Intent?): Uri? =
        GmsDocumentScanningResult.fromActivityResultIntent(data)?.pages?.firstOrNull()?.imageUri
}
