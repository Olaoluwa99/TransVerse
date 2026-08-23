package com.easit.aiscanner.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.easit.aiscanner.model.Scan
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: Scan)

    @Delete()
    suspend fun delete(scan: Scan)

    @Query("""
        SELECT * FROM scanTable
        WHERE transcript_text LIKE '%' || :query || '%'
           OR translated_text LIKE '%' || :query || '%'
           OR source_language LIKE '%' || :query || '%'
           OR translated_language LIKE '%' || :query || '%'
        ORDER BY is_favorite DESC, timeMilliseconds DESC
    """)
    fun searchScans(query: String): Flow<List<Scan>>


    @Query("Select * from scanTable WHERE id = :selectedScanId")
    fun getSelectedScanDetails(selectedScanId: String): LiveData<Scan>

    @Query("Delete from scanTable")
    suspend fun deleteAllScanHistory()

    @Query("UPDATE scanTable SET is_favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

}
