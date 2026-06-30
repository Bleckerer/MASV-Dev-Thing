package com.cambrian.masv_dev.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Status of an individual file upload.
 */
enum class UploadStatus {
    PENDING,      // Queued for upload, not started
    UPLOADING,    // Currently uploading
    COMPLETED,    // Successfully uploaded
    FAILED        // Upload failed
}

/**
 * Room entity representing a single file upload record.
 */
@Entity(tableName = "uploads")
data class UploadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long, // in bytes

    @ColumnInfo(name = "uri")
    val uri: String, // content URI string

    @ColumnInfo(name = "batch_id")
    val batchId: String, // group identifier for the batch

    @ColumnInfo(name = "status")
    val status: UploadStatus,

    @ColumnInfo(name = "progress_percent")
    val progressPercent: Int = 0, // 0-100

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "package_id")
    val packageId: String? = null, // MASV package ID, if known

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null // if failed, store the error
)