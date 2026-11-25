package com.easit.aiscanner.scannerAI

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.InstallStatusListener
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate.InstallState
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A Play services module install that failed or was cancelled. */
class ModuleInstallException(val errorCode: Int) :
    Exception("ML Kit module install failed (error $errorCode)")

/**
 * Makes sure the ML Kit features delivered by Google Play services (language identification,
 * text recognition, smart reply, barcode scanning) are installed before they are used.
 *
 * Play services downloads these modules on first use, and until the download finishes ML Kit
 * calls fail with "Waiting for the … optional module to be downloaded". Instead of surfacing
 * that as an error, callers wait here and get real download progress.
 */
@Singleton
class MlKitModules @Inject constructor(@ApplicationContext private val context: Context) {

    private val client = ModuleInstall.getClient(context)

    /**
     * Starts downloading every module the app uses, so they are usually ready before the first
     * translation. Best effort: failures are only logged, and [ensureInstalled] retries later.
     */
    fun prefetch() {
        val clients = listOf(
            LanguageIdentification.getClient(),
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            SmartReply.getClient(),
            BarcodeScanning.getClient(),
        )
        val request = ModuleInstallRequest.newBuilder().apply { clients.forEach(::addApi) }.build()
        client.installModules(request)
            .addOnFailureListener { Log.w(TAG, "Could not prefetch ML Kit modules", it) }
            .addOnCompleteListener { clients.forEach { api -> runCatching { api.close() } } }
    }

    /**
     * Suspends until [apis] are installed, downloading them now if needed.
     *
     * @param onDownloading called on the main thread while a download is needed, with progress
     *   from 0 to 1, or null while the size isn't known yet. Not called when already installed.
     * @throws ModuleInstallException if Play services can't install the modules
     */
    suspend fun ensureInstalled(apis: List<OptionalModuleApi>, onDownloading: (Float?) -> Unit = {}) {
        val alreadyAvailable = try {
            client.areModulesAvailable(*apis.toTypedArray()).await().areModulesAvailable()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not check ML Kit module availability", e)
            false
        }
        if (alreadyAvailable) return

        onDownloading(null)
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: InstallStatusListener
            listener = InstallStatusListener { update ->
                update.progressInfo?.let { progress ->
                    val total = progress.totalBytesToDownload
                    if (total > 0) onDownloading((progress.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f))
                }
                when (update.installState) {
                    InstallState.STATE_COMPLETED -> {
                        client.unregisterListener(listener)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    InstallState.STATE_FAILED, InstallState.STATE_CANCELED -> {
                        client.unregisterListener(listener)
                        if (continuation.isActive) {
                            continuation.resumeWithException(ModuleInstallException(update.errorCode))
                        }
                    }
                }
            }
            continuation.invokeOnCancellation { client.unregisterListener(listener) }

            val request = ModuleInstallRequest.newBuilder()
                .apply { apis.forEach(::addApi) }
                .setListener(listener, ContextCompat.getMainExecutor(context))
                .build()
            client.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) {
                        client.unregisterListener(listener)
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                }
                .addOnFailureListener { error ->
                    client.unregisterListener(listener)
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
        }
    }

    companion object {
        private const val TAG = "MlKitModules"
        private const val RETRY_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 1_500L

        /**
         * Runs [block], retrying briefly while ML Kit reports its module as still unavailable.
         * Play services can take a moment to load a module even right after it reports the
         * install complete.
         */
        suspend fun <T> retryWhileUnavailable(block: suspend () -> T): T {
            repeat(RETRY_ATTEMPTS - 1) {
                try {
                    return block()
                } catch (e: MlKitException) {
                    if (e.errorCode != MlKitException.UNAVAILABLE) throw e
                    Log.i(TAG, "ML Kit module not ready yet; retrying", e)
                    delay(RETRY_DELAY_MS)
                }
            }
            return block()
        }
    }
}
