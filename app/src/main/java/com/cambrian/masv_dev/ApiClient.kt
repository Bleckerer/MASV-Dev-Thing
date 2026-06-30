package com.cambrian.masv_dev

import android.util.Log
import com.cambrian.masv_dev.models.MasvPackage
import com.cambrian.masv_dev.models.PackageFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "ApiClient"
    private const val MASV_API_BASE = "https://api.massive.app/v1"

    private val certificatePinner = CertificatePinner.Builder()
        .add("api.massive.app", "sha256/31pbZjJp98u4VpeO2e+6vNK707ftVWZuOwsDQwYJj2U=")
        .build()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .certificatePinner(certificatePinner)
            .build()
    }

    suspend fun getPackages(apiKey: String, teamId: String): List<MasvPackage> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$MASV_API_BASE/teams/$teamId/packages")
                .addHeader("X-API-KEY", apiKey)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d(TAG, "Response code: ${response.code}")
                Log.d(TAG, "Response body: $body")

                if (!response.isSuccessful) {
                    throw Exception("HTTP error: ${response.code} - $body")
                }
                if (body == null) {
                    throw Exception("Empty response body")
                }

                try {
                    val jsonArray = JSONArray(body)
                    val packages = mutableListOf<MasvPackage>()

                    if (jsonArray.length() > 0) {
                        Log.d(TAG, "First package JSON: ${jsonArray.getJSONObject(0).toString()}")
                    }

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        packages.add(
                            MasvPackage(
                                id = obj.getString("id"),
                                name = obj.getString("name"),
                                state = obj.getString("state"),
                                created_at = obj.getString("created_at"),
                                total_files = obj.optInt("total_files", 0),
                                size = obj.optLong("size", 0L),
                                access_token = obj.optString("access_token").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                    packages
                } catch (e: JSONException) {
                    try {
                        val jsonObject = JSONObject(body)
                        val dataArray = jsonObject.optJSONArray("data")
                        if (dataArray != null) {
                            val packages = mutableListOf<MasvPackage>()
                            if (dataArray.length() > 0) {
                                Log.d(TAG, "First package JSON (data): ${dataArray.getJSONObject(0).toString()}")
                            }
                            for (i in 0 until dataArray.length()) {
                                val obj = dataArray.getJSONObject(i)
                                packages.add(
                                    MasvPackage(
                                        id = obj.getString("id"),
                                        name = obj.getString("name"),
                                        state = obj.getString("state"),
                                        created_at = obj.getString("created_at"),
                                        total_files = obj.optInt("total_files", 0),
                                        size = obj.optLong("size", 0L),
                                        access_token = obj.optString("access_token").takeIf { it.isNotEmpty() }
                                    )
                                )
                            }
                            packages
                        } else {
                            throw Exception("Unexpected response format: $body")
                        }
                    } catch (e2: JSONException) {
                        throw Exception("Failed to parse JSON: ${e2.message} - body: $body")
                    }
                }
            }
        }

    suspend fun getPackageFiles(packageAccessToken: String, packageId: String): List<PackageFile> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$MASV_API_BASE/packages/$packageId/files")
                .addHeader("X-Package-Token", packageAccessToken)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                Log.d(TAG, "Package files response code: ${response.code}")
                Log.d(TAG, "Package files response body: $body")

                if (!response.isSuccessful) {
                    throw Exception("HTTP error: ${response.code} - $body")
                }
                if (body == null) {
                    throw Exception("Empty response body")
                }

                val jsonArray = JSONArray(body)
                val files = mutableListOf<PackageFile>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val completed = obj.optBoolean("completed", false)
                    val state = if (completed) "completed" else "pending"
                    files.add(
                        PackageFile(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            size = obj.optLong("size", 0L),
                            state = state
                        )
                    )
                }
                files
            }
        }
}