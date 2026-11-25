package com.easit.aiscanner

import com.easit.aiscanner.database.CaptureFiles
import com.easit.aiscanner.database.ScanFileRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureFilesTest {

    @Test
    fun fileReferencedByImageUrl_isKept() {
        val refs = listOf(ScanFileRef(id = "20260101120000000", imageUrl = "SCAN.1767268800000.jpg", audioUrl = ""))

        val orphans = CaptureFiles.findOrphans(listOf("SCAN.1767268800000.jpg"), refs)

        assertEquals(emptyList<String>(), orphans)
    }

    @Test
    fun fileReferencedByAudioUrl_isKept() {
        val audio = "TRANSVERSE_SPEECH_20260101120000000.wav"
        val refs = listOf(ScanFileRef(id = "20260101120000000", imageUrl = "", audioUrl = audio))

        assertEquals(emptyList<String>(), CaptureFiles.findOrphans(listOf(audio), refs))
    }

    @Test
    fun legacyAudioRow_keepsWavNamedAfterItsId() {
        // Older audio rows stored audioUrl = "" and a bogus "<id>.jpg" imageUrl.
        val id = "20250101120000000"
        val refs = listOf(ScanFileRef(id = id, imageUrl = "$id.jpg", audioUrl = ""))

        val orphans = CaptureFiles.findOrphans(listOf("TRANSVERSE_SPEECH_$id.wav"), refs)

        assertEquals(emptyList<String>(), orphans)
    }

    @Test
    fun unreferencedCaptureFiles_areOrphans() {
        val refs = listOf(ScanFileRef(id = "20260101120000000", imageUrl = "20260101120000000.jpg", audioUrl = ""))
        val files = listOf(
            "20260101120000000.jpg",
            "20260202120000000.jpg",
            "SCAN.1767268800000.jpg",
            "TRANSVERSE_SPEECH_20260202120000000.wav"
        )

        val orphans = CaptureFiles.findOrphans(files, refs)

        assertEquals(
            listOf(
                "20260202120000000.jpg",
                "SCAN.1767268800000.jpg",
                "TRANSVERSE_SPEECH_20260202120000000.wav"
            ),
            orphans
        )
    }

    @Test
    fun nonCaptureFiles_areNeverSelected() {
        val files = listOf(
            "datastore",
            "notes.txt",
            "profile.jpg",
            "photo_2026.jpg",
            "recording.wav",
            "OTHER_123.wav",
            "SCAN.abc.jpg",
            "123.jpeg",
            "123.jpg.bak",
            "../123.jpg"
        )

        assertEquals(emptyList<String>(), CaptureFiles.findOrphans(files, emptyList()))
        files.forEach { assertFalse(it, CaptureFiles.isCaptureFileName(it)) }
    }

    @Test
    fun noSavedScans_makesEveryCaptureFileAnOrphan() {
        val files = listOf("20260101120000000.jpg", "TRANSVERSE_SPEECH_20260101120000000.wav")

        assertEquals(files, CaptureFiles.findOrphans(files, emptyList()))
    }

    @Test
    fun captureNames_matchWhatTheFlowsWrite() {
        val id = "20260101120000000"
        assertTrue(CaptureFiles.isCaptureFileName(CaptureFiles.imageFileName(id)))
        assertTrue(CaptureFiles.isCaptureFileName(CaptureFiles.audioFileName(id)))
        assertTrue(CaptureFiles.isCaptureFileName("SCAN.1767268800000.jpg"))
    }

    @Test
    fun filesOwnedBy_includesLegacyNamesButSkipsFilesOtherScansUse() {
        val id = "20260101120000000"
        val scan = ScanFileRef(id = id, imageUrl = "SCAN.1767268800000.jpg", audioUrl = "")
        val other = ScanFileRef(id = "20260303120000000", imageUrl = "SCAN.1767268800000.jpg", audioUrl = "")

        assertEquals(
            listOf("SCAN.1767268800000.jpg", "$id.jpg", "TRANSVERSE_SPEECH_$id.wav"),
            CaptureFiles.filesOwnedBy(scan, emptyList())
        )
        assertEquals(
            listOf("$id.jpg", "TRANSVERSE_SPEECH_$id.wav"),
            CaptureFiles.filesOwnedBy(scan, listOf(other))
        )
    }
}
