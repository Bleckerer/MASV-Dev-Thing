package com.cambrian.masv_dev.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UploadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UploadDatabase : RoomDatabase() {

    abstract fun uploadDao(): UploadDao

    companion object {
        @Volatile
        private var INSTANCE: UploadDatabase? = null

        fun getInstance(context: Context): UploadDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UploadDatabase::class.java,
                    "upload_database"
                )
                    .fallbackToDestructiveMigration(true)  // Updated
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}