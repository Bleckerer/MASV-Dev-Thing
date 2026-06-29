package com.cambrian.masv_dev

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.cambrian.masv_dev.utils.NotificationHelper
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FileMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FileMonitorWorker"
    }

    private fun isSupportedFile(filename: String?): Boolean {
        if (filename == null) return false
        val lower = filename.lowercase()
        val prefs = PreferencesHelper(applicationContext)
        val extensions = prefs.getScanExtensionsSet()
        return extensions.any { lower.endsWith(it) }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = PreferencesHelper(applicationContext)
            val notificationHelper = NotificationHelper(applicationContext)
            val monitoredFolderUri = prefs.getMonitoredFolderUri()
            if (monitoredFolderUri == null) {
                Log.e(TAG, "No monitored folder selected")
                return@withContext Result.failure()
            }

            val uri = Uri.parse(monitoredFolderUri)
            val root = DocumentFile.fromTreeUri(applicationContext, uri)
            if (root == null || !root.exists() || !root.isDirectory) {
                Log.e(TAG, "Selected URI is not a directory or is inaccessible")
                return@withContext Result.failure()
            }

            val alreadyUploaded = prefs.getUploadedFiles().toMutableSet()
            val newlyFoundUris = mutableListOf<Uri>()
            findFiles(root, alreadyUploaded, newlyFoundUris)

            if (newlyFoundUris.isNotEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Found ${newlyFoundUris.size} new file(s)")
                }
                notificationHelper.showNewFilesDetected(newlyFoundUris.size)
                if (isNetworkAvailable()) {
                    notificationHelper.dismissWaitingForNetwork()
                    // Always create a fresh batch ID – no persistence across scans
                    val batchId = System.currentTimeMillis().toString()
                    val uriStrings = newlyFoundUris.map { it.toString() }.toSet()
                    prefs.saveBatch(batchId, uriStrings, "", "")  // empty package id / token for now
                    val inputData = workDataOf("batchId" to batchId)
                    val batchWork = OneTimeWorkRequestBuilder<BatchUploadWorker>()
                        .setInputData(inputData)
                        .addTag("batch_upload")
                        .setConstraints(Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build())
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(batchWork)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "BatchUploadWorker enqueued with batchId: $batchId")
                    }
                } else {
                    notificationHelper.showWaitingForNetwork()
                }
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "No new files found")
                }
            }
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error monitoring files", e)
            return@withContext Result.retry()
        }
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

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}