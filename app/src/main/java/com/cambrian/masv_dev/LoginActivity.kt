package com.cambrian.masv_dev

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var preferencesHelper: PreferencesHelper

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        preferencesHelper = PreferencesHelper(this)

        val sessionId = preferencesHelper.getSessionId()
        if (sessionId != null) {
            validateSession(sessionId)
            return
        }

        setupLoginUI()
    }

    private fun validateSession(sessionId: String) {
        val proxyUrl = BuildConfig.MASV_PROXY_URL
        if (proxyUrl.isNullOrEmpty()) {
            startLoginActivity()
            return
        }
        val json = JSONObject().apply { put("sessionId", sessionId) }
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$proxyUrl/../validate")
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        startMainActivity()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        preferencesHelper.clearSession()
                        startLoginActivity()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    preferencesHelper.clearSession()
                    startLoginActivity()
                }
            }
        }
    }

    private fun startLoginActivity() {
        setupLoginUI()
    }

    private fun setupLoginUI() {
        setContentView(R.layout.activity_login)
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

        val proxyUrl = BuildConfig.MASV_PROXY_URL
        if (proxyUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Proxy URL not configured", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val json = JSONObject().apply {
            put("email", email)
            put("password", password)
        }
        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$proxyUrl/../auth")
            .post(requestBody)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && body != null) {
                        val jsonResponse = JSONObject(body)
                        val sessionId = jsonResponse.getString("sessionId")
                        preferencesHelper.saveSessionId(sessionId)
                        startMainActivity()
                    } else {
                        val msg = if (body != null) JSONObject(body).optString("message", "Authentication failed")
                        else "Authentication failed"
                        Toast.makeText(this@LoginActivity, msg, Toast.LENGTH_LONG).show()
                        loginButton.isEnabled = true
                        progressBar.visibility = ProgressBar.GONE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                    loginButton.isEnabled = true
                    progressBar.visibility = ProgressBar.GONE
                }
            }
        }
    }

    private fun startMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}