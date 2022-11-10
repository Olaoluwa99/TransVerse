package com.easit.aiscanner.ui.typeChooser

import android.app.Application
import androidx.lifecycle.*
import com.easit.aiscanner.data.Preference
import com.easit.aiscanner.database.ScanDao
import com.easit.aiscanner.database.ScanDatabase
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList
import javax.inject.Inject

enum class ScanHistoryStatus { LOADING, ERROR, DONE, NO_PROJECT }

@HiltViewModel
class TypeChooserViewModel @Inject constructor(
    application: Application,
    private val preference: Preference
) : AndroidViewModel(application) {
    lateinit var allScans: LiveData<List<Scan>>

    private val repository : ScanRepository

    private val _scanObject = MutableLiveData<Scan>()
    val scanObject: LiveData<Scan> = _scanObject

    private var _scanHistoryList = MutableLiveData<List<Scan>>()
    val scanHistoryList: LiveData<List<Scan>> = _scanHistoryList

    private val _status = MutableLiveData<ScanHistoryStatus>()
    val status: LiveData<ScanHistoryStatus> = _status

    lateinit var currentHistoryItem: LiveData<Scan>


    init {
        _status.value = ScanHistoryStatus.LOADING
        val dao = ScanDatabase.getDatabase(application).getScanDao()
        repository = ScanRepository(dao)
        allScans = repository.allScans.asLiveData()
    }

    var selectedFontSize
        get() = preference.appFontSize
        set(value) {
            preference.appFontSize = value
        }

    var selectedIncognitoMode
        get() = preference.appIncognitoMode
        set(value) {
            preference.appIncognitoMode = value
        }

    fun deleteScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.delete(scan)
    }

    fun updateScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.update(scan)
    }

    fun addScan(scan: Scan) = viewModelScope.launch (Dispatchers.IO) {
        repository.insert(scan)
    }

    fun getSelectedScanObject(id: String) = viewModelScope.launch{
        currentHistoryItem = repository.getSelectedScanById(id)
    }

    fun onScanObjectClicked(scan: Scan) {
        _scanObject.value= scan
    }
}