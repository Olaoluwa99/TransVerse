package com.easit.aiscanner.ui.translate

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.database.CaptureFiles
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@Immutable
sealed interface SavedScanUiState {
    data object Loading : SavedScanUiState
    data object NotFound : SavedScanUiState
    data class Loaded(
        val scan: Scan,
        /** The captured image, when its file still exists. */
        val imageFile: File?,
        val playback: PlaybackState,
    ) : SavedScanUiState
}

/** Loads one saved scan, read-only, and plays its speech file when there is one. */
@HiltViewModel
class SavedScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ScanRepository,
) : ViewModel() {

    private data class LoadedScan(val scan: Scan?, val imageFile: File?)

    private val scanId = MutableStateFlow<String?>(null)
    private val playback = SpeechPlayback(context, viewModelScope)
    private var audioFileName: String? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loaded: StateFlow<LoadedScan?> = scanId
        .filterNotNull()
        .flatMapLatest { id ->
            // Room's LiveData emits null when the row does not (or no longer) exist.
            @Suppress("UNCHECKED_CAST")
            (repository.getSelectedScanById(id) as LiveData<Scan?>).asFlow()
        }
        .map { scan -> LoadedScan(scan, scan?.let { imageFileOf(it) }) }
        .onEach { it.scan?.let { scan -> prepareAudio(scan) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<SavedScanUiState> =
        combine(loaded, playback.state) { current, speech ->
            when {
                current == null -> SavedScanUiState.Loading
                current.scan == null -> SavedScanUiState.NotFound
                else -> SavedScanUiState.Loaded(current.scan, current.imageFile, speech)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedScanUiState.Loading)

    fun load(id: String) {
        scanId.value = id
    }

    fun togglePlay(): Boolean = playback.togglePlay()

    fun seekTo(positionMs: Int) = playback.seekTo(positionMs)

    /** The scan's speech file, if it exists. */
    fun speechFile(): File? {
        val name = audioFileName ?: return null
        if (!playback.state.value.available) return null
        return File(context.filesDir, name)
    }

    private suspend fun prepareAudio(scan: Scan) {
        // Older audio rows stored no audioUrl; their file was named after the scan id.
        val name = scan.audioUrl.ifBlank { CaptureFiles.audioFileName(scan.id) }
        if (name == audioFileName) return
        audioFileName = name
        playback.load(File(context.filesDir, name))
    }

    private suspend fun imageFileOf(scan: Scan): File? = withContext(Dispatchers.IO) {
        scan.imageUrl.takeIf { it.isNotBlank() }
            ?.let { File(context.filesDir, it) }
            ?.takeIf { it.isFile && it.canRead() }
    }

    override fun onCleared() {
        playback.release()
        super.onCleared()
    }
}
