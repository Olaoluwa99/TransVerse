package com.easit.aiscanner.ui.history

import androidx.compose.runtime.Immutable
import com.easit.aiscanner.model.Scan
import java.io.File
import java.time.LocalDate

internal const val SCAN_TYPE_TEXT = "text"
internal const val SCAN_TYPE_IMAGE = "image"
internal const val SCAN_TYPE_AUDIO = "audio"
internal const val SCAN_TYPE_BARCODE = "barcode"

/** Everything the History screen shows. */
@Immutable
data class HistoryUiState(
    val isLoading: Boolean = true,
    /** Saved scans in total, ignoring search and filter. */
    val totalCount: Int = 0,
    val filter: HistoryFilter = HistoryFilter.ALL,
    /** True when a search or a filter other than All is narrowing the list. */
    val isFiltering: Boolean = false,
    val sections: List<HistorySection> = emptyList(),
) {
    val hasNoHistory: Boolean get() = !isLoading && totalCount == 0
    val hasNoResults: Boolean get() = !isLoading && totalCount > 0 && sections.isEmpty()
}

/** Single-select filter chips above the list. */
enum class HistoryFilter {
    ALL, FAVOURITES, TEXT, IMAGE, VOICE;

    fun matches(scan: Scan): Boolean = when (this) {
        ALL -> true
        FAVOURITES -> scan.isFavorite
        TEXT -> scan.scanType == SCAN_TYPE_TEXT
        IMAGE -> scan.scanType == SCAN_TYPE_IMAGE
        VOICE -> scan.scanType == SCAN_TYPE_AUDIO
    }
}

/** A saved scan plus its on-disk image thumbnail, if any. */
@Immutable
data class HistoryItem(
    val scan: Scan,
    val thumbnail: File? = null,
)

@Immutable
data class HistorySection(
    val header: HistorySectionHeader,
    val items: List<HistoryItem>,
) {
    /** Stable LazyColumn key for the header. */
    val key: String get() = "header-" + header.key
}

sealed interface HistorySectionHeader {
    val key: String

    data object Favourites : HistorySectionHeader {
        override val key: String get() = "favourites"
    }

    data object Today : HistorySectionHeader {
        override val key: String get() = "today"
    }

    data object Yesterday : HistorySectionHeader {
        override val key: String get() = "yesterday"
    }

    data class Day(val date: LocalDate) : HistorySectionHeader {
        override val key: String get() = date.toString()
    }
}

/** The kind of capture a scan came from, for its icon and description. */
internal enum class ScanKind { TEXT, IMAGE, VOICE, BARCODE, OTHER }

internal val Scan.kind: ScanKind
    get() = when (scanType) {
        SCAN_TYPE_TEXT -> ScanKind.TEXT
        SCAN_TYPE_IMAGE -> ScanKind.IMAGE
        SCAN_TYPE_AUDIO -> ScanKind.VOICE
        SCAN_TYPE_BARCODE -> ScanKind.BARCODE
        else -> ScanKind.OTHER
    }
