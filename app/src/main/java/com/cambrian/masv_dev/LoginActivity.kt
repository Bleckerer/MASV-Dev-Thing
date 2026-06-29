package com.cambrian.masv_dev

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.cambrian.masv_dev.api.ApiClient
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import android.widget.ImageButton

class LoginActivity : BaseActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var preferencesHelper: PreferencesHelper

    companion object {
        private const val TAG = "LoginActivity"
        private const val MASV_API_BASE = "https://api.massive.app/v1"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        findViewById<ImageButton>(R.id.themeToggleButton).setOnClickListener {
            switchThemeWithFade()
        }

        preferencesHelper = PreferencesHelper(this)

        val sessionId = preferencesHelper.getSessionId()
        if (sessionId != null) {
            startMainActivity()
            return
        }

        setupLoginUI()
    }

    private fun getExpiryDate(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.add(Calendar.HOUR, 24)
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .format(calendar.time)
    }

    private fun setupLoginUI() {
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        progressBar = findViewById(R.id.progressBar)

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performLogin(email, password)
        }
    }

    private fun performLogin(email: String, password: String) {
        loginButton.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authJson = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }
                val authRequest = Request.Builder()
                    .url("$MASV_API_BASE/auth")
                    .addHeader("Content-Type", "application/json")
                    .post(authJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val authResponse = ApiClient.client.newCall(authRequest).execute()
                val authBody = authResponse.body?.string()
                Log.d(TAG, "Auth response code: ${authResponse.code}")

                if (!authResponse.isSuccessful || authBody == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Authentication failed: ${authResponse.code}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI()
                    }
                    return@launch
                }

                val authJsonResponse = JSONObject(authBody)
                val jwt = authJsonResponse.getString("token")
                val teamsArray = authJsonResponse.getJSONArray("teams")
                if (teamsArray.length() == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LoginActivity,
                            "No teams found for this account",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI()
                    }
                    return@launch
                }
                val teamId = teamsArray.getJSONObject(0).getString("id")
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Team ID: $teamId")
                }
                preferencesHelper.saveTeamId(teamId)

                val expiryDate = getExpiryDate()
                Log.d(TAG, "Expiry date: $expiryDate")

                val apiKeyJson = JSONObject().apply {
                    put("name", "Android_${System.currentTimeMillis()}")
                    put("expiry", expiryDate)
                    put("state", "active")
                }
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "API key request: $apiKeyJson")
                }

                val apiKeyRequest = Request.Builder()
                    .url("$MASV_API_BASE/teams/$teamId/api_keys")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-User-Token", jwt)
                    .post(apiKeyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val apiKeyResponse = ApiClient.client.newCall(apiKeyRequest).execute()
                val apiKeyBody = apiKeyResponse.body?.string()
                Log.d(TAG, "API key response code: ${apiKeyResponse.code}")
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "API key response body: $apiKeyBody")
                }

                if (!apiKeyResponse.isSuccessful || apiKeyBody == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LoginActivity,
                            "Failed to create API key: ${apiKeyResponse.code}",
                            Toast.LENGTH_LONG
                        ).show()
                        resetUI()
                    }
                    return@launch
                }

                val apiKeyJsonResponse = JSONObject(apiKeyBody)
                val apiKey = apiKeyJsonResponse.getString("key")
                val returnedExpiry = apiKeyJsonResponse.optString("expiry")
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "API key created: ${apiKey.take(20)}...")
                }
                Log.d(TAG, "Returned expiry from API: $returnedExpiry")

                preferencesHelper.saveSessionId(apiKey)

                withContext(Dispatchers.Main) {
                    startMainActivity()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LoginActivity,
                        "Network error: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    resetUI()
                }
            }
        }
    }

    private fun resetUI() {
        loginButton.isEnabled = true
        progressBar.visibility = ProgressBar.GONE
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}