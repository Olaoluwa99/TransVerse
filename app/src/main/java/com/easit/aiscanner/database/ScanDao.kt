package com.easit.aiscanner.database

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.room.*
import com.easit.aiscanner.model.Scan

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(scan: Scan)

    @Update()
    suspend fun update(scan: Scan)

    @Delete()
    suspend fun delete(scan: Scan)

    @Query("Select * from scanTable order by id ASC")
    fun getAllScans(): LiveData<List<Scan>>


    @Query("Select * from scanTable WHERE id = :selectedScanId")
    fun getSelectedScanDetails(selectedScanId: String): LiveData<Scan>

}