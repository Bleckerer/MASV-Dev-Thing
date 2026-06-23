package com.cambrian.masv_dev.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesHelper(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("masv_dev_prefs", Context.MODE_PRIVATE)

    // Folder monitoring
    fun saveMonitoredFolderUri(uri: String) {
        prefs.edit().putString("monitored_folder_uri", uri).apply()
    }

    fun getMonitoredFolderUri(): String? = prefs.getString("monitored_folder_uri", null)

    fun saveMonitoredFolderPath(path: String) {
        prefs.edit().putString("monitored_folder_path", path).apply()
    }

    fun getMonitoredFolderPath(): String? = prefs.getString("monitored_folder_path", null)

    // Uploaded files
    fun saveUploadedFiles(files: Set<String>) {
        prefs.edit().putString("uploaded_files", files.joinToString(",")).apply()
    }

    fun getUploadedFiles(): Set<String> {
        val str = prefs.getString("uploaded_files", "") ?: ""
        return if (str.isNotEmpty()) str.split(",").toSet() else emptySet()
    }

    fun saveLastUploadTime(timestamp: Long) {
        prefs.edit().putLong("last_upload_time", timestamp).apply()
    }

    fun getLastUploadTime(): String? {
        val timestamp = prefs.getLong("last_upload_time", 0)
        return if (timestamp > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } else null
    }

    // Intervals
    fun saveScanIntervalMinutes(minutes: Int) {
        prefs.edit().putInt("scan_interval_minutes", minutes).apply()
    }

    fun getScanIntervalMinutes(): Int = prefs.getInt("scan_interval_minutes", 15)

    fun saveUploadRetryIntervalMinutes(minutes: Int) {
        prefs.edit().putInt("upload_retry_interval_minutes", minutes).apply()
    }

    fun getUploadRetryIntervalMinutes(): Int = prefs.getInt("upload_retry_interval_minutes", 30)

    // Batch storage
    fun saveBatch(batchId: String, uris: Set<String>, packageId: String, accessToken: String) {
        with(prefs.edit()) {
            putStringSet("batch_${batchId}_uris", uris)
            putString("batch_${batchId}_package_id", packageId)
            putString("batch_${batchId}_access_token", accessToken)
            apply()
        }
    }

    fun getBatchUris(batchId: String): Set<String>? = prefs.getStringSet("batch_${batchId}_uris", null)
    fun getBatchPackageId(batchId: String): String? = prefs.getString("batch_${batchId}_package_id", null)
    fun getBatchAccessToken(batchId: String): String? = prefs.getString("batch_${batchId}_access_token", null)

    fun removeBatch(batchId: String) {
        with(prefs.edit()) {
            remove("batch_${batchId}_uris")
            remove("batch_${batchId}_package_id")
            remove("batch_${batchId}_access_token")
            apply()
        }
    }

    // Session
    fun saveSessionId(sessionId: String) {
        prefs.edit().putString("session_id", sessionId).apply()
    }

    fun getSessionId(): String? = prefs.getString("session_id", null)

    fun clearSession() {
        prefs.edit().remove("session_id").remove("team_id").apply()
    }

    // Team ID (new for direct MASV API)
    fun saveTeamId(teamId: String) {
        prefs.edit().putString("team_id", teamId).apply()
    }

    fun getTeamId(): String? = prefs.getString("team_id", null)

    // Proxy URL (kept for compatibility, but no longer used)
    fun saveProxyUrl(url: String) {
        prefs.edit().putString("proxy_url", url).apply()
    }

    fun getProxyUrl(): String? = prefs.getString("proxy_url", null)

    // File extensions
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
        prefs.edit().putString("scan_extensions", cleaned).apply()
    }

    fun addFileExtension(ext: String) {
        val current = getScanExtensionsSet()
        var normalized = ext.trim().lowercase()
        if (!normalized.startsWith(".")) normalized = ".$normalized"
        current.add(normalized)
        saveScanExtensions(current.joinToString(","))
    }

    fun removeFileExtension(ext: String) {
        val current = getScanExtensionsSet()
        var normalized = ext.trim().lowercase()
        if (!normalized.startsWith(".")) normalized = ".$normalized"
        current.remove(normalized)
        saveScanExtensions(current.joinToString(","))
    }

    fun hasPendingBatch(): Boolean {
        val all = prefs.all
        return all.keys.any { key ->
            key.startsWith("batch_") && key.endsWith("_uris") &&
                    (getBatchUris(key.removeSuffix("_uris"))?.isNotEmpty() == true)
        }
    }
}