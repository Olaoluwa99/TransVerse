package com.easit.aiscanner.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.easit.aiscanner.model.Scan
import kotlinx.coroutines.flow.Flow

class ScanRepository(private val scanDao: ScanDao) {

    val allScans: Flow<List<Scan>> = scanDao.reGetAllScans()

    suspend fun insert(scan: Scan){
        scanDao.insert(scan)
    }

    suspend fun update(scan: Scan){
        scanDao.update(scan)
    }

    suspend fun delete(scan: Scan){
        scanDao.delete(scan)
    }

    fun getSelectedScanById(selectedScanId: String): LiveData<Scan>{
        return scanDao.getSelectedScanDetails(selectedScanId)
    }

    suspend fun deleteAll(){
        scanDao.deleteAllScanHistory()
    }

}