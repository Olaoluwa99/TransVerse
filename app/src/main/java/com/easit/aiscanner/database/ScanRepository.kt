package com.easit.aiscanner.database

import androidx.lifecycle.LiveData
import com.easit.aiscanner.model.Scan
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanRepository @Inject constructor(private val scanDao: ScanDao) {

    suspend fun insert(scan: Scan){
        scanDao.insert(scan)
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

    fun searchScans(query: String): Flow<List<Scan>> = scanDao.searchScans(query)

    suspend fun setFavorite(id: String, favorite: Boolean) = scanDao.setFavorite(id, favorite)

}
