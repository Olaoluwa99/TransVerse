package com.easit.aiscanner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.easit.aiscanner.model.Scan

@Database(entities = arrayOf(Scan::class), version = 1, exportSchema = false)
abstract class ScanDatabase: RoomDatabase() {
    abstract fun getScanDao(): ScanDao

    companion object{
        @Volatile
        private var INSTANCE: ScanDatabase? = null

        fun getDatabase(context: Context): ScanDatabase{
            return INSTANCE ?: synchronized(this){
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScanDatabase::class.java,
                    "scan_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}