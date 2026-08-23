package com.easit.aiscanner

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.easit.aiscanner.database.ScanDao
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRepositoryTest {
    private val dao = FakeScanDao()
    private val repository = ScanRepository(dao)

    @Test
    fun search_matchesOriginalAndTranslatedText_caseInsensitively() = runTest {
        repository.insert(scan(id = "1", original = "Bonjour Lagos", translated = "Hello Lagos"))
        repository.insert(scan(id = "2", original = "Guten Tag", translated = "Good day"))

        assertEquals(listOf("1"), repository.searchScans("bonjour").first().map { it.id })
        assertEquals(listOf("2"), repository.searchScans("GOOD").first().map { it.id })
    }

    @Test
    fun favoriteState_isPersistedById() = runTest {
        repository.insert(scan(id = "1", original = "Ẹ káàárọ̀", translated = "Good morning"))
        repository.setFavorite("1", true)

        assertTrue(repository.searchScans("").first().single().isFavorite)
    }

    private fun scan(id: String, original: String, translated: String) = Scan(
        id = id,
        dateTime = "12:00 01/01/2026",
        timeMilliseconds = id.toLong(),
        scanType = "text",
        transcriptText = original,
        translatedText = translated,
        sourceLanguage = "French",
        translatedLanguage = "English",
        entities = emptyList(),
        smartReplies = emptyList(),
        imageUrl = "",
        audioUrl = "",
        barcodeScan = ""
    )
}

private class FakeScanDao : ScanDao {
    private val scans = MutableStateFlow<List<Scan>>(emptyList())

    override suspend fun insert(scan: Scan) {
        scans.value = scans.value.filterNot { it.id == scan.id } + scan
    }

    override suspend fun delete(scan: Scan) {
        scans.value = scans.value.filterNot { it.id == scan.id }
    }

    override fun searchScans(query: String): Flow<List<Scan>> = scans.map { items ->
        items.filter { scan ->
            listOf(
                scan.transcriptText,
                scan.translatedText,
                scan.sourceLanguage,
                scan.translatedLanguage
            ).any { it.contains(query, ignoreCase = true) }
        }.sortedWith(compareByDescending<Scan> { it.isFavorite }.thenByDescending { it.timeMilliseconds })
    }

    override fun getSelectedScanDetails(selectedScanId: String): LiveData<Scan> =
        MutableLiveData(scans.value.first { it.id == selectedScanId })

    override suspend fun deleteAllScanHistory() {
        scans.value = emptyList()
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        scans.value = scans.value.map { if (it.id == id) it.copy(isFavorite = favorite) else it }
    }
}
