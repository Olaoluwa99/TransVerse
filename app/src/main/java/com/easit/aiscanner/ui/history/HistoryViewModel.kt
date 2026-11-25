package com.easit.aiscanner.ui.history

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.database.CaptureFileStore
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Saved translations: search (debounced, over text and languages), filter by type or favourite,
 * favourite toggle, and delete with undo. Capture files are only removed once undo is no longer
 * possible ([finalizeDeletion]).
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScanRepository,
    private val captureFileStore: CaptureFileStore,
) : ViewModel() {

    /** Raw search text, held as Compose state so the text field never lags behind typing. */
    var query by mutableStateOf("")
        private set

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    private val events = Channel<HistoryEvent>(Channel.BUFFERED)

    /** One-off events for the UI, such as showing the undo snackbar. */
    val eventFlow: Flow<HistoryEvent> = events.receiveAsFlow()

    private val searchQuery: Flow<String> = snapshotFlow { query.trim() }
        .distinctUntilChanged()
        .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    private val searchResults: Flow<Pair<String, List<Scan>>> = searchQuery
        .flatMapLatest { q -> repository.searchScans(q).map { q to it } }

    private val totalCount: Flow<Int> = repository.searchScans("")
        .map { it.size }
        .distinctUntilChanged()

    val uiState: StateFlow<HistoryUiState> =
        combine(searchResults, totalCount, filter) { (activeQuery, scans), total, selected ->
            buildState(activeQuery, scans, total, selected)
        }
            .flowOn(Dispatchers.IO)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = HistoryUiState(),
            )

    fun onQueryChange(value: String) {
        query = value
    }

    fun onFilterChange(value: HistoryFilter) {
        filter.value = value
    }

    fun clearFilters() {
        query = ""
        filter.value = HistoryFilter.ALL
    }

    fun toggleFavorite(scan: Scan) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setFavorite(scan.id, !scan.isFavorite)
        }
    }

    /** Removes the history row only; its capture files stay until the undo window closes. */
    fun delete(scan: Scan) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(scan)
            events.send(HistoryEvent.Deleted(scan))
        }
    }

    /** Puts a deleted row back (Undo). */
    fun restore(scan: Scan) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(scan)
        }
    }

    /** Call once a deletion can no longer be undone: removes the scan's capture files. */
    fun finalizeDeletion(scan: Scan) {
        viewModelScope.launch(Dispatchers.IO) {
            captureFileStore.deleteFilesFor(scan)
        }
    }

    private fun buildState(
        activeQuery: String,
        scans: List<Scan>,
        total: Int,
        filter: HistoryFilter,
    ): HistoryUiState {
        val items = scans
            .filter { filter.matches(it) }
            .map { HistoryItem(scan = it, thumbnail = thumbnailFor(it)) }
        return HistoryUiState(
            isLoading = false,
            totalCount = total,
            filter = filter,
            isFiltering = activeQuery.isNotEmpty() || filter != HistoryFilter.ALL,
            sections = groupIntoSections(items, filter, LocalDate.now(), ZoneId.systemDefault()),
        )
    }

    /** The captured image for image scans, when it is still on disk. Runs on the IO dispatcher. */
    private fun thumbnailFor(scan: Scan): File? {
        if (scan.scanType != SCAN_TYPE_IMAGE || scan.imageUrl.isBlank()) return null
        // imageUrl is a bare file name inside filesDir; reject anything that looks like a path.
        if (scan.imageUrl.contains('/') || scan.imageUrl.contains('\\')) return null
        return File(context.filesDir, scan.imageUrl).takeIf { it.isFile }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** One-off events from [HistoryViewModel]. */
sealed interface HistoryEvent {
    /** [scan] was removed from history and can still be restored. */
    data class Deleted(val scan: Scan) : HistoryEvent
}

/**
 * Splits [items] (already ordered favourites first, then newest first) into sections.
 * Outside the Favourites filter, favourites get their own group on top so the DAO's
 * favourites-first ordering stays visible; everything else is grouped by day.
 */
internal fun groupIntoSections(
    items: List<HistoryItem>,
    filter: HistoryFilter,
    today: LocalDate,
    zone: ZoneId,
): List<HistorySection> {
    if (items.isEmpty()) return emptyList()
    val sections = mutableListOf<HistorySection>()
    val dated = if (filter == HistoryFilter.FAVOURITES) {
        items
    } else {
        val favourites = items.filter { it.scan.isFavorite }
        if (favourites.isNotEmpty()) {
            sections += HistorySection(HistorySectionHeader.Favourites, favourites)
        }
        items.filterNot { it.scan.isFavorite }
    }
    dated
        .sortedByDescending { it.scan.timeMilliseconds }
        .groupBy { Instant.ofEpochMilli(it.scan.timeMilliseconds).atZone(zone).toLocalDate() }
        .forEach { (date, dayItems) ->
            val header = when (date) {
                today -> HistorySectionHeader.Today
                today.minusDays(1) -> HistorySectionHeader.Yesterday
                else -> HistorySectionHeader.Day(date)
            }
            sections += HistorySection(header, dayItems)
        }
    return sections
}
