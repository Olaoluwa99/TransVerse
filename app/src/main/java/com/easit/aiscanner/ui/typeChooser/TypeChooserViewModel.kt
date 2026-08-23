package com.easit.aiscanner.ui.typeChooser

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

@HiltViewModel
class TypeChooserViewModel @Inject constructor(
    private val preference: Preference,
    private val repository: ScanRepository
) : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    val allScans: LiveData<List<Scan>> = searchQuery
        .flatMapLatest(repository::searchScans)
        .asLiveData()

    var selectedFontSize
        get() = preference.appFontSize
        set(value) {
            preference.appFontSize = value
        }

    fun deleteScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.delete(scan)
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query.trim()
    }

    fun toggleFavorite(scan: Scan) = viewModelScope.launch(Dispatchers.IO) {
        repository.setFavorite(scan.id, !scan.isFavorite)
    }

    fun restoreScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.insert(scan)
    }
}
