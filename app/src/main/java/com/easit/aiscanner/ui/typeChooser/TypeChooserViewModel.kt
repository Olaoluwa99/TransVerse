package com.easit.aiscanner.ui.typeChooser

import android.app.Application
import androidx.lifecycle.*
import com.easit.aiscanner.database.ScanDatabase
import com.easit.aiscanner.database.ScanRepository
import com.easit.aiscanner.model.Scan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList

enum class ScanHistoryStatus { LOADING, ERROR, DONE, NO_PROJECT }

class TypeChooserViewModel(application: Application) : AndroidViewModel(application) {
    // TODO: Implement the ViewModel
    lateinit var allScans: LiveData<List<Scan>>
    private val repository : ScanRepository

    private val _scanObject = MutableLiveData<Scan>()
    val scanObject: LiveData<Scan> = _scanObject

    private var _scanHistoryList = MutableLiveData<List<Scan>>()
    val scanHistoryList: LiveData<List<Scan>> = _scanHistoryList

    private val _status = MutableLiveData<ScanHistoryStatus>()
    val status: LiveData<ScanHistoryStatus> = _status

    init {
        _status.value = ScanHistoryStatus.LOADING
        val dao = ScanDatabase.getDatabase(application).getScanDao()
        repository = ScanRepository(dao)
        //createTestHistory()
    }

    fun getAllScansHistory() = viewModelScope.launch {
        allScans = repository.allScans
        if (allScans.value.isNullOrEmpty()){
            _status.value = ScanHistoryStatus.NO_PROJECT
        }else{
            _status.value = ScanHistoryStatus.DONE
        }
    }

    private fun createTestHistory(){
        val list1 = ArrayList<String>()
        list1.add("Ola")
        list1.add("Fikayo")
        val list2 = ArrayList<String>()
        list2.add("Ife")
        list2.add("Mum")
        val testScan = Scan("12345", "15092022", "9:45", "audio", "I am olaoluwa",
            "Selected Translated text for here", "entities", "smart_replies",
            "", "", "barcode_scan")
        addScan(testScan)

        val testScan2 = Scan("54321", "15092022", "9:45", "audio", "I am not olaoluwa",
            "Unknown: Selected Translated text for here. Why", "entities", "smart_replies",
            "", "", "barcode_scan")
        addScan(testScan2)
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

    fun onScanObjectClicked(scan: Scan) {
        _scanObject.value= scan
    }
}