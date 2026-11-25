package com.easit.aiscanner.database

import android.content.Context
import android.util.Log
import com.easit.aiscanner.model.Scan
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the image and audio capture files written to `filesDir`, so that removing history
 * also removes the captures behind it. Only names accepted by [CaptureFiles.isCaptureFileName]
 * are ever deleted.
 */
@Singleton
class CaptureFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScanRepository
) {

    /** Deletes the capture files of [scan] that no other saved scan still references. */
    suspend fun deleteFilesFor(scan: Scan): Int = withContext(Dispatchers.IO) {
        val others = repository.getFileReferences().filterNot { it.id == scan.id }
        val target = ScanFileRef(scan.id, scan.imageUrl, scan.audioUrl)
        CaptureFiles.filesOwnedBy(target, others).count { deleteCaptureFile(it) }
    }

    /** Deletes every capture file, referenced or not. */
    suspend fun deleteAllCaptureFiles(): Int = withContext(Dispatchers.IO) {
        listFileNames().filter { CaptureFiles.isCaptureFileName(it) }.count { deleteCaptureFile(it) }
    }

    /**
     * Deletes capture files that no saved scan references (incognito captures, unsaved
     * captures and leftovers from interrupted deletions). Only safe while no capture screen
     * holds an unsaved capture, e.g. at app start.
     */
    suspend fun deleteOrphanedCaptureFiles(): Int = withContext(Dispatchers.IO) {
        val names = listFileNames()
        if (names.none { CaptureFiles.isCaptureFileName(it) }) return@withContext 0
        CaptureFiles.findOrphans(names, repository.getFileReferences()).count { deleteCaptureFile(it) }
    }

    private fun listFileNames(): List<String> = context.filesDir.list()?.toList() ?: emptyList()

    private fun deleteCaptureFile(name: String): Boolean {
        if (!CaptureFiles.isCaptureFileName(name)) return false
        val file = File(context.filesDir, name)
        if (!file.isFile) return false
        return file.delete().also { deleted ->
            if (!deleted) Log.w(TAG, "Could not delete capture file $name")
        }
    }

    private companion object {
        const val TAG = "CaptureFileStore"
    }
}
