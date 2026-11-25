package com.easit.aiscanner.scannerAI

import android.app.DownloadManager
import android.content.Context
import android.util.Log

/** Why Android has paused a model download. */
enum class DownloadWaitReason { NETWORK, WIFI, RETRYING, OTHER }

/**
 * Progress of the language-model download(s) in flight. ML Kit downloads translation models
 * through Android's DownloadManager under this app's identity, so they are visible here.
 *
 * @property totalBytes total size, or a value ≤ 0 while unknown
 * @property waitingFor set while Android has paused the download
 */
data class ModelDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val waitingFor: DownloadWaitReason? = null,
) {
    /** 0–1, or null while the size is unknown. */
    val fraction: Float?
        get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}

/** Reads this app's active DownloadManager downloads (ML Kit model downloads). */
class ModelDownloadMonitor(context: Context) {

    private val downloadManager = context.getSystemService(DownloadManager::class.java)

    /**
     * The combined progress of this app's pending, running and paused downloads, or null when
     * there are none (or DownloadManager isn't available, as on some customised Android builds).
     */
    fun snapshot(): ModelDownloadProgress? {
        val manager = downloadManager ?: return null
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PAUSED
        )
        return try {
            manager.query(query)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val bytesColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val statusColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val reasonColumn = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                var downloaded = 0L
                var total = 0L
                var sizeUnknown = false
                var running = false
                var waiting: DownloadWaitReason? = null
                do {
                    downloaded += cursor.getLong(bytesColumn).coerceAtLeast(0)
                    val size = cursor.getLong(totalColumn)
                    if (size > 0) total += size else sizeUnknown = true
                    when (cursor.getInt(statusColumn)) {
                        DownloadManager.STATUS_RUNNING -> running = true
                        DownloadManager.STATUS_PAUSED -> waiting = waiting ?: when (cursor.getInt(reasonColumn)) {
                            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> DownloadWaitReason.NETWORK
                            DownloadManager.PAUSED_QUEUED_FOR_WIFI -> DownloadWaitReason.WIFI
                            DownloadManager.PAUSED_WAITING_TO_RETRY -> DownloadWaitReason.RETRYING
                            else -> DownloadWaitReason.OTHER
                        }
                    }
                } while (cursor.moveToNext())
                ModelDownloadProgress(
                    bytesDownloaded = downloaded,
                    totalBytes = if (sizeUnknown) -1 else total,
                    // Only report waiting when nothing is actively downloading.
                    waitingFor = if (running) null else waiting,
                )
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read model download progress", e)
            null
        }
    }

    private companion object {
        const val TAG = "ModelDownloadMonitor"
    }
}
