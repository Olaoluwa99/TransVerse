package com.easit.aiscanner.scannerAI

import android.content.Context
import android.os.SystemClock
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

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

    /** Progress of translation-model downloads, which ML Kit runs through DownloadManager. */
    val modelDownloads = ModelDownloadMonitor(context)

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
     * @throws ModuleInstallException if Play services can't install the modules, or the download
     *   makes no progress for [STALL_TIMEOUT_MS]
     */
    suspend fun ensureInstalled(apis: List<OptionalModuleApi>, onDownloading: (Float?) -> Unit = {}) {
        if (areAvailable(apis)) return

        onDownloading(null)
        val done = CompletableDeferred<Unit>()
        val lastActivity = AtomicLong(SystemClock.elapsedRealtime())
        val listener = InstallStatusListener { update ->
            lastActivity.set(SystemClock.elapsedRealtime())
            update.progressInfo?.let { progress ->
                val total = progress.totalBytesToDownload
                if (total > 0) onDownloading((progress.bytesDownloaded.toFloat() / total).coerceIn(0f, 1f))
            }
            when (update.installState) {
                InstallState.STATE_COMPLETED -> done.complete(Unit)
                InstallState.STATE_FAILED, InstallState.STATE_CANCELED ->
                    done.completeExceptionally(ModuleInstallException(update.errorCode))
            }
        }
        try {
            val request = ModuleInstallRequest.newBuilder()
                .apply { apis.forEach(::addApi) }
                .setListener(listener, ContextCompat.getMainExecutor(context))
                .build()
            client.installModules(request)
                .addOnSuccessListener { response ->
                    if (response.areModulesAlreadyInstalled()) done.complete(Unit)
                }
                .addOnFailureListener { error -> done.completeExceptionally(error) }

            // Status updates can be missed (e.g. another app finished the same install), so also
            // poll availability, and give up if nothing moves rather than waiting forever.
            while (!done.isCompleted) {
                if (withTimeoutOrNull(POLL_INTERVAL_MS) { done.await() } != null) break
                if (areAvailable(apis)) return
                if (SystemClock.elapsedRealtime() - lastActivity.get() > STALL_TIMEOUT_MS) {
                    Log.w(TAG, "ML Kit module install made no progress; giving up")
                    throw ModuleInstallException(ERROR_STALLED)
                }
            }
            done.await() // rethrows a failure reported by the listener
        } finally {
            client.unregisterListener(listener)
        }
    }

    private suspend fun areAvailable(apis: List<OptionalModuleApi>): Boolean = try {
        client.areModulesAvailable(*apis.toTypedArray()).await().areModulesAvailable()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Could not check ML Kit module availability", e)
        false
    }

    companion object {
        private const val TAG = "MlKitModules"
        private const val POLL_INTERVAL_MS = 2_000L
        /** Give up on a module install with no status update for this long. */
        private const val STALL_TIMEOUT_MS = 90_000L
        /** [ModuleInstallException.errorCode] when an install stopped making progress. */
        const val ERROR_STALLED = -1
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
