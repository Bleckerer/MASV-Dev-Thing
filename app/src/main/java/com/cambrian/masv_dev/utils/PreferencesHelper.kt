package com.cambrian.masv_dev.utils

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import javax.crypto.AEADBadTagException

class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences = createEncryptedPreferences(context)

    private fun createEncryptedPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            EncryptedSharedPreferences.create(
                context,
                "masv_dev_prefs_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            when (e) {
                is AEADBadTagException,
                is KeyPermanentlyInvalidatedException,
                is javax.crypto.BadPaddingException -> {
                    // Clear the corrupt file
                    context.getSharedPreferences("masv_dev_prefs_encrypted", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    // Retry
                    EncryptedSharedPreferences.create(
                        context,
                        "masv_dev_prefs_encrypted",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                }
                else -> throw e
            }
        }
    }

    // -------------------- Folder monitoring --------------------
    fun saveMonitoredFolderUri(uri: String) {
        prefs.edit { putString("monitored_folder_uri", uri) }
    }

    fun getMonitoredFolderUri(): String? = prefs.getString("monitored_folder_uri", null)

    fun saveMonitoredFolderPath(path: String) {
        prefs.edit { putString("monitored_folder_path", path) }
    }

    fun getMonitoredFolderPath(): String? = prefs.getString("monitored_folder_path", null)

    // -------------------- Uploaded files --------------------
    fun saveUploadedFiles(files: Set<String>) {
        prefs.edit { putString("uploaded_files", files.joinToString(",")) }
    }

    fun getUploadedFiles(): Set<String> {
        val str = prefs.getString("uploaded_files", "") ?: ""
        return if (str.isNotEmpty()) str.split(",").toSet() else emptySet()
    }

    fun saveLastUploadTime(timestamp: Long) {
        prefs.edit { putLong("last_upload_time", timestamp) }
    }

    fun getLastUploadTime(): String? {
        val timestamp = prefs.getLong("last_upload_time", 0)
        return if (timestamp > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } else null
    }

    // -------------------- Current batch ID --------------------
    fun saveCurrentBatchId(batchId: String) {
        prefs.edit { putString("current_batch_id", batchId) }
    }

    fun getCurrentBatchId(): String? = prefs.getString("current_batch_id", null)

    fun clearCurrentBatchId() {
        prefs.edit { remove("current_batch_id") }
    }

    // -------------------- Intervals --------------------
    fun saveScanIntervalMinutes(minutes: Int) {
        prefs.edit { putInt("scan_interval_minutes", minutes) }
    }

    fun getScanIntervalMinutes(): Int = prefs.getInt("scan_interval_minutes", 15)

    fun saveUploadRetryIntervalMinutes(minutes: Int) {
        prefs.edit { putInt("upload_retry_interval_minutes", minutes) }
    }

    fun getUploadRetryIntervalMinutes(): Int = prefs.getInt("upload_retry_interval_minutes", 30)

    // -------------------- Batch storage --------------------
    fun saveBatch(batchId: String, uris: Set<String>, packageId: String, accessToken: String) {
        prefs.edit {
            putStringSet("batch_${batchId}_uris", uris)
            putString("batch_${batchId}_package_id", packageId)
            putString("batch_${batchId}_access_token", accessToken)
        }
    }

    fun getBatchUris(batchId: String): Set<String>? = prefs.getStringSet("batch_${batchId}_uris", null)
    fun getBatchPackageId(batchId: String): String? = prefs.getString("batch_${batchId}_package_id", null)
    fun getBatchAccessToken(batchId: String): String? = prefs.getString("batch_${batchId}_access_token", null)

    fun removeBatch(batchId: String) {
        prefs.edit {
            remove("batch_${batchId}_uris")
            remove("batch_${batchId}_package_id")
            remove("batch_${batchId}_access_token")
        }
        // If this is the current batch, clear it
        if (getCurrentBatchId() == batchId) {
            clearCurrentBatchId()
        }
    }

    // -------------------- Session --------------------
    fun saveSessionId(sessionId: String) {
        prefs.edit { putString("session_id", sessionId) }
    }

    fun getSessionId(): String? = prefs.getString("session_id", null)

    fun clearSession() {
        prefs.edit {
            remove("session_id")
            remove("team_id")
        }
    }

    // -------------------- Team ID --------------------
    fun saveTeamId(teamId: String) {
        prefs.edit { putString("team_id", teamId) }
    }

    fun getTeamId(): String? = prefs.getString("team_id", null)

    // -------------------- File extensions --------------------
    fun getScanExtensions(): String {
        return prefs.getString("scan_extensions", ".png") ?: ".png"
    }

    fun getScanExtensionsSet(): MutableSet<String> {
        val str = getScanExtensions()
        return str.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != "." }
            .toMutableSet()
    }

    fun saveScanExtensions(extensions: String) {
        val cleaned = extensions.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && it != "." }
            .joinToString(",")
        prefs.edit { putString("scan_extensions", cleaned) }
    }

    fun addFileExtension(ext: String) {
        val current = getScanExtensionsSet()
        var normalized = ext.trim().lowercase()
        normalized = normalized.replace(Regex("[^a-z0-9.]"), "")
        if (normalized.isEmpty() || normalized == ".") return
        if (!normalized.startsWith(".")) normalized = ".$normalized"
        current.add(normalized)
        saveScanExtensions(current.joinToString(","))
    }

    fun removeFileExtension(ext: String) {
        val current = getScanExtensionsSet()
        var normalized = ext.trim().lowercase()
        normalized = normalized.replace(Regex("[^a-z0-9.]"), "")
        if (normalized.isEmpty() || normalized == ".") return
        if (!normalized.startsWith(".")) normalized = ".$normalized"
        current.remove(normalized)
        saveScanExtensions(current.joinToString(","))
    }

    // -------------------- Batch pending check --------------------
    fun hasPendingBatch(): Boolean {
        val all = prefs.all
        return all.keys.any { key ->
            key.startsWith("batch_") && key.endsWith("_uris") &&
                    (getBatchUris(key.removeSuffix("_uris"))?.isNotEmpty() == true)
        }
    }
}