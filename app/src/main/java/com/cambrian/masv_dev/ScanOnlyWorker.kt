package com.cambrian.masv_dev

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cambrian.masv_dev.utils.NotificationHelper
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ScanOnlyWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ScanOnlyWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesHelper(applicationContext)
            val monitoredFolderUri = prefs.getMonitoredFolderUri()
            if (monitoredFolderUri == null) {
                Log.e(TAG, "No monitored folder selected")
                return@withContext Result.failure()
            }

            val uri = Uri.parse(monitoredFolderUri)
            val root = DocumentFile.fromTreeUri(applicationContext, uri)
            if (root == null || !root.exists()) {
                Log.e(TAG, "Cannot access monitored folder")
                return@withContext Result.failure()
            }

            val alreadyUploaded = prefs.getUploadedFiles().toMutableSet()
            val newlyFoundUris = mutableListOf<Uri>()
            findFiles(root, alreadyUploaded, newlyFoundUris)

            if (newlyFoundUris.isNotEmpty()) {
                Log.d(TAG, "Found ${newlyFoundUris.size} new file(s)")
                // No upload – just notify
                NotificationHelper(applicationContext).showNewFilesDetected(newlyFoundUris.size)
            } else {
                Log.d(TAG, "No new files found")
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning files", e)
            return@withContext Result.retry()
        }
    }

    private fun isSupportedFile(filename: String?): Boolean {
        if (filename == null) return false
        val lower = filename.lowercase()
        val prefs = PreferencesHelper(applicationContext)
        val extensions = prefs.getScanExtensionsSet()
        return extensions.any { lower.endsWith(it) }
    }

    private fun findFiles(dir: DocumentFile, alreadyUploaded: MutableSet<String>, newlyFound: MutableList<Uri>) {
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) {
                findFiles(doc, alreadyUploaded, newlyFound)
            } else if (isSupportedFile(doc.name)) {
                val uriString = doc.uri.toString()
                if (!alreadyUploaded.contains(uriString)) {
                    newlyFound.add(doc.uri)
                }
            }
        }
    }
}