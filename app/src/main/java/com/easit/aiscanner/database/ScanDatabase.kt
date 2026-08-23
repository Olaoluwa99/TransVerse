package com.easit.aiscanner.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.easit.aiscanner.model.Scan

@Database(entities = [Scan::class], version = 2, exportSchema = true)
@TypeConverters(MyTypeConverter::class)
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
                ).addMigrations(MIGRATION_1_2).build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE scanTable ADD COLUMN is_favorite INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
