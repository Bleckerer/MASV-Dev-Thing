package com.cambrian.masv_dev

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.cambrian.masv_dev.utils.NotificationHelper
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class BatchUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "BatchUploadWorker"
        private const val DEFAULT_API_URL = "https://api.massive.app/v1"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    private fun getApiBaseUrl(prefs: PreferencesHelper): String {
        val prefProxy = prefs.getProxyUrl()
        val buildConfigProxy = BuildConfig.MASV_PROXY_URL
        return when {
            !prefProxy.isNullOrEmpty() -> prefProxy
            !buildConfigProxy.isNullOrEmpty() -> buildConfigProxy
            else -> DEFAULT_API_URL
        }
    }

    private fun getSessionId(prefs: PreferencesHelper): String? = prefs.getSessionId()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = PreferencesHelper(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        val apiBaseUrl = getApiBaseUrl(prefs)
        Log.d(TAG, "Using API base URL: $apiBaseUrl")

        val sessionId = getSessionId(prefs)
        if (sessionId.isNullOrEmpty()) {
            Log.e(TAG, "No session ID – user not logged in")
            notificationHelper.showUploadFailure("Not logged in. Please restart the app and log in.")
            return@withContext Result.failure()
        }

        try {
            val batchId = inputData.getString("batchId") ?: run {
                Log.e(TAG, "No batchId provided")
                return@withContext Result.failure()
            }

            val pendingUris = prefs.getBatchUris(batchId)?.toMutableSet() ?: run {
                Log.e(TAG, "No batch found for id $batchId")
                return@withContext Result.failure()
            }

            if (pendingUris.isEmpty()) {
                Log.d(TAG, "Batch already completed")
                prefs.removeBatch(batchId)
                return@withContext Result.success()
            }

            var packageId = prefs.getBatchPackageId(batchId)
            var accessToken = prefs.getBatchAccessToken(batchId)

            if (packageId.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
                val packageInfo = createMasvPackage(apiBaseUrl, sessionId) ?: run {
                    Log.e(TAG, "Failed to create package for batch $batchId")
                    notificationHelper.showUploadFailure("Failed to create upload package")
                    return@withContext Result.failure()
                }
                packageId = packageInfo.first
                accessToken = packageInfo.second
                prefs.saveBatch(batchId, pendingUris, packageId, accessToken)
            }

            notificationHelper.showUploadStarted("Batch upload")

            val totalFiles = pendingUris.size
            var currentFileIndex = 0
            val succeededUris = mutableSetOf<String>()
            var allSuccess = true

            setProgressAsync(workDataOf("currentFile" to 0, "totalFiles" to totalFiles, "currentFileName" to ""))

            for (uriString in pendingUris) {
                currentFileIndex++
                val uri = Uri.parse(uriString)
                val fileName = getFileNameFromUri(uri) ?: "unknown"

                setProgressAsync(workDataOf(
                    "currentFile" to currentFileIndex,
                    "totalFiles" to totalFiles,
                    "currentFileName" to fileName
                ))

                val tempFile = copyUriToTempFile(uri)
                if (tempFile == null) {
                    Log.e(TAG, "Failed to copy $fileName")
                    notificationHelper.cancelUploadStarted()
                    notificationHelper.showUploadFailure("Failed to access $fileName")
                    allSuccess = false
                    break
                }

                val success = uploadFileMultipart(apiBaseUrl, packageId, accessToken, tempFile, fileName, sessionId)
                tempFile.delete()

                if (success) {
                    succeededUris.add(uriString)
                    val uploaded = prefs.getUploadedFiles().toMutableSet()
                    uploaded.add(uriString)
                    prefs.saveUploadedFiles(uploaded)
                    Log.d(TAG, "Uploaded: $fileName")
                } else {
                    allSuccess = false
                    Log.e(TAG, "Failed to upload: $fileName")
                    notificationHelper.cancelUploadStarted()
                    notificationHelper.showUploadFailure("Failed to upload $fileName")
                    break
                }
            }

            notificationHelper.cancelUploadStarted()

            val remainingUris = pendingUris - succeededUris
            if (remainingUris.isEmpty() && allSuccess) {
                closeMasvPackage(apiBaseUrl, packageId, accessToken, sessionId)
                prefs.removeBatch(batchId)
                notificationHelper.showUploadSuccess(pendingUris.size)
                prefs.saveLastUploadTime(System.currentTimeMillis())
                setProgressAsync(workDataOf(
                    "currentFile" to totalFiles,
                    "totalFiles" to totalFiles,
                    "currentFileName" to ""
                ))
                return@withContext Result.success()
            } else {
                prefs.removeBatch(batchId)
                if (!allSuccess) {
                    // Notification already shown
                } else {
                    notificationHelper.showUploadFailure("Some files failed, will not retry automatically")
                }
                return@withContext Result.failure()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Batch upload error", e)
            if (e is java.util.concurrent.CancellationException) {
                Log.w(TAG, "Worker cancelled (system). Will retry without discarding batch.")
                notificationHelper.showUploadFailure("Upload paused, will retry automatically")
                return@withContext Result.retry()
            }
            notificationHelper.cancelUploadStarted()
            notificationHelper.showUploadFailure(e.message ?: "Unknown error")
            return@withContext Result.failure()
        }
    }

    // ------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream = applicationContext.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(applicationContext.cacheDir, "temp_upload_${System.currentTimeMillis()}")
            FileOutputStream(tempFile).use { output -> inputStream.copyTo(output) }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying URI", e)
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return androidx.documentfile.provider.DocumentFile.fromSingleUri(applicationContext, uri)?.name
            ?: uri.path?.substringAfterLast('/')
    }

    private fun createMasvPackage(baseUrl: String, sessionId: String): Pair<String, String>? {
        try {
            val json = JSONObject().apply {
                put("name", "MASV_Dev_Batch_${System.currentTimeMillis()}")
                put("description", "Batch upload from MASV-Dev")
                put("auto_start", true)
                put("recipients", JSONArray().put("jude.bryant@cambriancollege.ca"))
            }
            val request = Request.Builder()
                .url("$baseUrl/packages")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Session-ID", sessionId)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val jsonResponse = JSONObject(body)
                    val id = jsonResponse.getString("id")
                    val token = jsonResponse.getString("access_token")
                    Log.d(TAG, "Package created: $id")
                    return Pair(id, token)
                } else {
                    Log.e(TAG, "Create package failed: ${response.code} - $body")
                    if (response.code == 401) handleSessionExpired()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating package", e)
        }
        return null
    }

    private suspend fun uploadFileMultipart(
        baseUrl: String,
        packageId: String,
        accessToken: String,
        file: File,
        originalFileName: String,
        sessionId: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val blueprint = requestUploadBlueprint(baseUrl, packageId, accessToken, file, originalFileName, sessionId)
                ?: return@withContext false
            val createBlueprint = blueprint.getJSONObject("create_blueprint")
            val blueprintUrl = createBlueprint.getString("url")
            val blueprintHeaders = createBlueprint.getJSONObject("headers")

            val uploadId = initiateMultipartUpload(blueprintUrl, blueprintHeaders) ?: return@withContext false

            val fileId = blueprint.getJSONObject("file").getString("id")
            val partUrls = requestPartUrls(baseUrl, packageId, accessToken, fileId, uploadId, start = 0, count = 1, sessionId)
            if (partUrls.isEmpty()) return@withContext false
            val part = partUrls[0]
            val partUrl = part.getString("url")
            val partMethod = part.getString("method")
            val partHeaders = part.optJSONObject("headers") ?: JSONObject()

            val eTag = uploadPart(partUrl, partMethod, partHeaders, file) ?: return@withContext false

            finalizeFile(baseUrl, packageId, accessToken, fileId, uploadId, file.length(), eTag, sessionId)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Multipart upload error for $originalFileName", e)
            return@withContext false
        }
    }

    private fun requestUploadBlueprint(
        baseUrl: String,
        packageId: String,
        accessToken: String,
        file: File,
        desiredFileName: String,
        sessionId: String
    ): JSONObject? {
        try {
            val json = JSONObject().apply {
                put("name", desiredFileName)
                put("size", file.length())
            }
            val request = Request.Builder()
                .url("$baseUrl/packages/$packageId/files")
                .addHeader("X-Package-Token", accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Session-ID", sessionId)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    Log.d(TAG, "Blueprint received for $desiredFileName")
                    return JSONObject(body)
                } else {
                    Log.e(TAG, "Blueprint request failed for $desiredFileName: ${response.code} - $body")
                    if (response.code == 401) handleSessionExpired()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting blueprint for $desiredFileName", e)
        }
        return null
    }

    private fun initiateMultipartUpload(blueprintUrl: String, blueprintHeaders: JSONObject): String? {
        try {
            val requestBuilder = Request.Builder().url(blueprintUrl)
            blueprintHeaders.keys().forEach { key ->
                requestBuilder.addHeader(key, blueprintHeaders.getString(key))
            }
            val request = requestBuilder.post("".toRequestBody(null)).build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val uploadId = extractUploadIdFromXml(body)
                    if (uploadId != null) {
                        Log.d(TAG, "Multipart upload initiated, UploadId: $uploadId")
                        return uploadId
                    } else {
                        Log.e(TAG, "Failed to extract UploadId from XML: $body")
                    }
                } else {
                    Log.e(TAG, "Initiate upload failed: ${response.code} - $body")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating multipart upload", e)
        }
        return null
    }

    private fun extractUploadIdFromXml(xml: String): String? {
        val regex = Regex("<UploadId>(.*?)</UploadId>")
        return regex.find(xml)?.groupValues?.get(1)
    }

    private fun requestPartUrls(
        baseUrl: String,
        packageId: String,
        accessToken: String,
        fileId: String,
        uploadId: String,
        start: Int,
        count: Int,
        sessionId: String
    ): List<JSONObject> {
        try {
            val url = "$baseUrl/packages/$packageId/files/$fileId?start=$start&count=$count"
            val json = JSONObject().apply { put("upload_id", uploadId) }
            val request = Request.Builder()
                .url(url)
                .addHeader("X-Package-Token", accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Session-ID", sessionId)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val array = JSONArray(body)
                    val result = mutableListOf<JSONObject>()
                    for (i in 0 until array.length()) {
                        result.add(array.getJSONObject(i))
                    }
                    Log.d(TAG, "Received ${result.size} part URLs")
                    return result
                } else {
                    Log.e(TAG, "Part URLs request failed: ${response.code} - $body")
                    if (response.code == 401) handleSessionExpired()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting part URLs", e)
        }
        return emptyList()
    }

    private fun uploadPart(partUrl: String, method: String, headers: JSONObject, file: File): String? {
        try {
            val requestBuilder = Request.Builder().url(partUrl)
            headers.keys().forEach { key ->
                requestBuilder.addHeader(key, headers.getString(key))
            }
            val body = file.asRequestBody("application/octet-stream".toMediaType())
            val request = when (method.uppercase()) {
                "PUT" -> requestBuilder.put(body).build()
                "POST" -> requestBuilder.post(body).build()
                else -> {
                    Log.e(TAG, "Unsupported upload method: $method")
                    return null
                }
            }
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val eTag = response.header("ETag")?.trim('"')
                    Log.d(TAG, "Part uploaded, ETag: $eTag")
                    return eTag
                } else {
                    Log.e(TAG, "Part upload failed: ${response.code} - ${response.body?.string()}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading part", e)
        }
        return null
    }

    private fun finalizeFile(
        baseUrl: String,
        packageId: String,
        accessToken: String,
        fileId: String,
        uploadId: String,
        fileSize: Long,
        eTag: String,
        sessionId: String
    ) {
        try {
            val json = JSONObject().apply {
                put("size", fileSize)
                put("file_extras", JSONObject().apply { put("upload_id", uploadId) })
                put("chunk_extras", JSONArray().apply {
                    put(JSONObject().apply {
                        put("part_number", "1")
                        put("etag", eTag)
                    })
                })
            }
            val request = Request.Builder()
                .url("$baseUrl/packages/$packageId/files/$fileId/finalize")
                .addHeader("X-Package-Token", accessToken)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Session-ID", sessionId)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "File finalize response: ${response.code}")
                if (response.code == 401) handleSessionExpired()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finalizing file", e)
        }
    }

    private fun closeMasvPackage(baseUrl: String, packageId: String, accessToken: String, sessionId: String) {
        val endpoints = listOf(
            "$baseUrl/packages/$packageId/finalize",
            "$baseUrl/packages/$packageId/close",
            "$baseUrl/packages/$packageId"
        )
        for (endpoint in endpoints) {
            try {
                val request = if (endpoint == "$baseUrl/packages/$packageId") {
                    val json = JSONObject().apply { put("state", "closed") }
                    Request.Builder()
                        .url(endpoint)
                        .addHeader("X-Package-Token", accessToken)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-Session-ID", sessionId)
                        .patch(json.toString().toRequestBody("application/json".toMediaType()))
                        .build()
                } else {
                    Request.Builder()
                        .url(endpoint)
                        .addHeader("X-Package-Token", accessToken)
                        .addHeader("X-Session-ID", sessionId)
                        .post("".toRequestBody(null))
                        .build()
                }
                client.newCall(request).execute().use { response ->
                    Log.d(TAG, "Close attempt on $endpoint: ${response.code}")
                    if (response.isSuccessful) {
                        Log.d(TAG, "Package closed successfully via $endpoint")
                        return
                    }
                    if (response.code == 401) handleSessionExpired()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Close attempt on $endpoint failed", e)
            }
        }
        Log.w(TAG, "Could not close package, but files were uploaded. You may need to close manually in MASV portal.")
    }

    private fun handleSessionExpired() {
        val prefs = PreferencesHelper(applicationContext)
        prefs.clearSession()
        Log.e(TAG, "Session expired. User must log in again.")
    }
}