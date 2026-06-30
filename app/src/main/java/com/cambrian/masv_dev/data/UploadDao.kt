package com.cambrian.masv_dev.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(upload: UploadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(uploads: List<UploadEntity>)

    @Update
    suspend fun update(upload: UploadEntity)

    // Use correct column names: progress_percent, batch_id
    @Query("UPDATE uploads SET progress_percent = :progress, status = :status WHERE uri = :uri AND batch_id = :batchId")
    suspend fun updateProgressAndStatus(
        uri: String,
        batchId: String,
        progress: Int,
        status: UploadStatus
    )

    // Use error_message
    @Query("UPDATE uploads SET status = :status, error_message = :errorMessage WHERE uri = :uri AND batch_id = :batchId")
    suspend fun updateStatusAndError(
        uri: String,
        batchId: String,
        status: UploadStatus,
        errorMessage: String? = null
    )

    // Use batch_id
    @Query("SELECT * FROM uploads WHERE batch_id = :batchId ORDER BY timestamp ASC")
    suspend fun getUploadsForBatch(batchId: String): List<UploadEntity>

    @Query("SELECT * FROM uploads WHERE batch_id = :batchId ORDER BY timestamp ASC")
    fun getUploadsForBatchFlow(batchId: String): Flow<List<UploadEntity>>

    @Query("SELECT * FROM uploads ORDER BY timestamp DESC")
    suspend fun getAllUploads(): List<UploadEntity>

    @Query("SELECT * FROM uploads ORDER BY timestamp DESC")
    fun getAllUploadsFlow(): Flow<List<UploadEntity>>

    @Query("SELECT * FROM uploads WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getUploadsByStatus(status: UploadStatus): List<UploadEntity>

    @Query("DELETE FROM uploads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM uploads WHERE batch_id = :batchId")
    suspend fun deleteBatch(batchId: String)

    @Query("DELETE FROM uploads")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM uploads WHERE batch_id = :batchId")
    suspend fun getCountForBatch(batchId: String): Int

    @Query("SELECT COUNT(*) FROM uploads WHERE status = :status")
    suspend fun getCountByStatus(status: UploadStatus): Int

    @Query("SELECT DISTINCT batch_id FROM uploads ORDER BY timestamp DESC")
    suspend fun getAllBatchIds(): List<String>

    @Query("DELETE FROM uploads WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}