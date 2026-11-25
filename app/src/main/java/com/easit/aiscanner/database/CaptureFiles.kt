package com.easit.aiscanner.database

import androidx.room.ColumnInfo

/** The columns of a saved scan that can point at a capture file in `filesDir`. */
data class ScanFileRef(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "image_url") val imageUrl: String,
    @ColumnInfo(name = "audio_url") val audioUrl: String
)

/**
 * Pure naming and ownership rules for the capture files the app writes to `filesDir`.
 *
 * Only names produced by the capture flows are ever treated as capture files:
 * - `<id>.jpg` from the image screen (ids are digit-only timestamps),
 * - `SCAN.<millis>.jpg` from the screen-capture service,
 * - `TRANSVERSE_SPEECH_<id>.wav` from the audio screen.
 * Anything else in `filesDir` is never selected for deletion.
 */
object CaptureFiles {
    const val AUDIO_FILE_PREFIX = "TRANSVERSE_SPEECH_"

    private val IMAGE_FILE = Regex("[0-9]+\\.jpg")
    private val SCANNER_IMAGE_FILE = Regex("SCAN\\.[0-9]+\\.jpg")
    private val AUDIO_FILE = Regex("${AUDIO_FILE_PREFIX}[0-9]+\\.wav")

    fun imageFileName(id: String): String = "$id.jpg"

    fun audioFileName(id: String): String = "$AUDIO_FILE_PREFIX$id.wav"

    fun isCaptureFileName(name: String): Boolean =
        IMAGE_FILE.matches(name) || SCANNER_IMAGE_FILE.matches(name) || AUDIO_FILE.matches(name)

    /**
     * True when any saved scan may still need [fileName]. Legacy audio rows stored no audioUrl
     * and named the wav after the scan id, so a name containing a scan id also counts.
     * Errs on the side of keeping files.
     */
    fun isReferenced(fileName: String, references: Collection<ScanFileRef>): Boolean =
        references.any { ref ->
            fileName == ref.imageUrl ||
                fileName == ref.audioUrl ||
                fileName.contains(ref.id)
        }

    /** Capture files among [fileNames] that no saved scan references. */
    fun findOrphans(fileNames: Collection<String>, references: Collection<ScanFileRef>): List<String> =
        fileNames.filter { isCaptureFileName(it) && !isReferenced(it, references) }

    /**
     * Capture files that belong to [scan] (including legacy id-based names) and that none of
     * [otherReferences] still needs.
     */
    fun filesOwnedBy(scan: ScanFileRef, otherReferences: Collection<ScanFileRef>): List<String> =
        listOf(scan.imageUrl, scan.audioUrl, imageFileName(scan.id), audioFileName(scan.id))
            .distinct()
            .filter { isCaptureFileName(it) && !isReferenced(it, otherReferences) }
}
