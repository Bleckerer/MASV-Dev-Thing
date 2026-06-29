package com.cambrian.masv_dev

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.cambrian.masv_dev.utils.PreferencesHelper
import java.io.File
import java.util.concurrent.TimeUnit
import android.widget.ImageButton

class MainActivity : BaseActivity() {

    companion object {
        private const val TAG = "MASVDevMain"
    }

    private lateinit var statusTextView: TextView
    private lateinit var detectedCountTextView: TextView
    private lateinit var pendingCountTextView: TextView
    private lateinit var uploadStatusTextView: TextView
    private lateinit var selectFolderButton: Button
    private lateinit var startMonitoringButton: Button
    private lateinit var scanNowButton: Button
    private lateinit var manualUploadButton: Button
    private lateinit var resetHistoryButton: Button
    private lateinit var progressOverlay: CardView
    private lateinit var progressTitle: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressPercent: TextView
    private lateinit var progressDetails: TextView
    private lateinit var preferencesHelper: PreferencesHelper

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                preferencesHelper.saveMonitoredFolderUri(uri.toString())
                preferencesHelper.saveMonitoredFolderPath(getFolderPathFromUri(uri))
                updateStatus()
                refreshDetectedFilesCount()
                updateButtonsEnabledState()
                Toast.makeText(this, "Folder selected: ${getFolderPathFromUri(uri)}", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Selected folder URI: $uri")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<ImageButton>(R.id.themeToggleButton).setOnClickListener {
            switchThemeWithFade()
        }

        findViewById<ImageButton>(R.id.settingsButton).setOnClickListener {
            showSettingsDialog()
        }

        preferencesHelper = PreferencesHelper(this)

        checkSessionAndRedirect()

        statusTextView = findViewById(R.id.statusTextView)
        detectedCountTextView = findViewById(R.id.detectedCountTextView)
        pendingCountTextView = findViewById(R.id.pendingCountTextView)
        uploadStatusTextView = findViewById(R.id.uploadStatusTextView)
        selectFolderButton = findViewById(R.id.selectFolderButton)
        startMonitoringButton = findViewById(R.id.startMonitoringButton)
        scanNowButton = findViewById(R.id.scanNowButton)
        manualUploadButton = findViewById(R.id.manualUploadButton)
        resetHistoryButton = findViewById(R.id.resetHistoryButton)
        progressOverlay = findViewById(R.id.progressOverlay)
        progressTitle = findViewById(R.id.progressTitle)
        progressBar = findViewById(R.id.progressBar)
        progressPercent = findViewById(R.id.progressPercent)
        progressDetails = findViewById(R.id.progressDetails)

        selectFolderButton.setOnClickListener {
            if (checkStoragePermission()) {
                openFolderPicker()
            }
        }

        startMonitoringButton.setOnClickListener {
            if (preferencesHelper.getMonitoredFolderUri() != null) {
                startMonitoring()
            } else {
                Toast.makeText(this, "Please select a folder first", Toast.LENGTH_SHORT).show()
            }
        }

        scanNowButton.setOnClickListener {
            Toast.makeText(this, "Scanning for new files... (no upload)", Toast.LENGTH_SHORT).show()
            val scanWork = OneTimeWorkRequestBuilder<ScanOnlyWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
                .build()
            WorkManager.getInstance(this).enqueue(scanWork)
            scanNowButton.postDelayed({
                refreshDetectedFilesCount()
                refreshPendingUploadsCount()
            }, 3000)
        }

        manualUploadButton.setOnClickListener { triggerManualUpload() }

        resetHistoryButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reset Upload History")
                .setMessage("This will allow previously uploaded files to be uploaded again. Continue?")
                .setPositiveButton("Reset") { _, _ ->
                    preferencesHelper.saveUploadedFiles(emptySet())
                    refreshDetectedFilesCount()
                    refreshPendingUploadsCount()
                    Toast.makeText(this, "Upload history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        updateButtonsEnabledState()
        requestNotificationPermission()
        updateStatus()
        refreshDetectedFilesCount()
        refreshPendingUploadsCount()

        WorkManager.getInstance(this).getWorkInfosByTagLiveData("batch_upload").observe(this) { workInfos ->
            val activeWork = workInfos?.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            if (activeWork != null) {
                val progress = activeWork.progress
                val currentFile = progress.getInt("currentFile", 0)
                val totalFiles = progress.getInt("totalFiles", 0)
                val fileName = progress.getString("currentFileName") ?: ""
                val bytesTransferred = progress.getLong("bytesTransferred", 0L)
                val totalBytes = progress.getLong("totalBytes", 0L)

                progressOverlay.visibility = View.VISIBLE

                if (totalFiles > 0) {
                    progressTitle.text = "Uploading file $currentFile of $totalFiles"

                    val detailsText = if (fileName.isNotEmpty() && totalBytes > 0) {
                        val percent = (bytesTransferred * 100 / totalBytes).toInt()
                        val transferredMB = bytesTransferred / (1024 * 1024)
                        val totalMB = totalBytes / (1024 * 1024)
                        "$fileName · $percent% · $transferredMB MB / $totalMB MB"
                    } else if (fileName.isNotEmpty()) {
                        "File: $fileName"
                    } else {
                        "Preparing..."
                    }
                    progressDetails.text = detailsText

                    if (totalBytes > 0) {
                        val percent = (bytesTransferred * 100 / totalBytes).toInt()
                        progressBar.isIndeterminate = false
                        progressBar.max = 100
                        progressBar.progress = percent
                        progressPercent.visibility = View.VISIBLE
                        progressPercent.text = "$percent%"
                    } else {
                        progressBar.isIndeterminate = true
                        progressPercent.visibility = View.GONE
                    }
                } else {
                    progressTitle.text = "Preparing upload..."
                    progressDetails.text = ""
                    progressBar.isIndeterminate = true
                    progressPercent.visibility = View.GONE
                }
            } else {
                // No active worker – show a brief "Finalising" state before hiding
                if (preferencesHelper.hasPendingBatch()) {
                    progressOverlay.visibility = View.VISIBLE
                    progressTitle.text = "Upload paused – retrying"
                    progressDetails.text = "Waiting for next attempt"
                    progressBar.isIndeterminate = true
                    progressPercent.visibility = View.GONE
                } else {
                    // If the overlay is visible and the worker just finished, show "Finalising" briefly
                    if (progressOverlay.visibility == View.VISIBLE) {
                        progressTitle.text = "Finalising package..."
                        progressDetails.text = ""
                        progressBar.isIndeterminate = true
                        progressPercent.visibility = View.GONE
                        android.os.Handler(mainLooper).postDelayed({
                            progressOverlay.visibility = View.GONE
                        }, 1500)
                    } else {
                        progressOverlay.visibility = View.GONE
                    }
                    refreshDetectedFilesCount()
                    refreshPendingUploadsCount()
                }
            }
        }
    }

    private fun checkSessionAndRedirect() {
        if (preferencesHelper.getSessionId() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    101
                )
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                return true
            } else {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs access to manage all files to monitor the folder.\n\nTap 'Open Settings' and enable 'All files access' for this app.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                return false
            }
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun openFolderPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        folderPickerLauncher.launch(intent)
    }

    private fun getFolderPathFromUri(uri: Uri): String = uri.path?.split(":")?.lastOrNull() ?: "Unknown path"

    private fun updateStatus() {
        val folderPath = preferencesHelper.getMonitoredFolderPath()
        statusTextView.text = if (folderPath != null) "Monitoring: $folderPath" else "No folder selected."
    }

    private fun updateButtonsEnabledState() {
        val folderSelected = preferencesHelper.getMonitoredFolderUri() != null
        startMonitoringButton.isEnabled = folderSelected
        scanNowButton.isEnabled = folderSelected
        manualUploadButton.isEnabled = folderSelected
    }

    private fun refreshDetectedFilesCount() {
        val folderUriString = preferencesHelper.getMonitoredFolderUri()
        if (folderUriString == null) {
            detectedCountTextView.text = "Detected files: (no folder selected)"
            return
        }
        val folderUri = Uri.parse(folderUriString)
        Thread {
            try {
                val root = DocumentFile.fromTreeUri(this, folderUri)
                if (root == null) {
                    runOnUiThread { detectedCountTextView.text = "Detected files: (unable to access folder)" }
                    return@Thread
                }
                val count = countScanFilesRecursively(root)
                runOnUiThread { detectedCountTextView.text = "Detected files in folder: $count" }
            } catch (e: Exception) {
                Log.e(TAG, "Error counting files", e)
                runOnUiThread { detectedCountTextView.text = "Detected files: error" }
            }
        }.start()
    }

    private fun countScanFilesRecursively(dir: DocumentFile): Int {
        var count = 0
        dir.listFiles().forEach { doc ->
            if (doc.isDirectory) {
                count += countScanFilesRecursively(doc)
            } else if (isScanFile(doc.name)) {
                count++
            }
        }
        return count
    }

    private fun refreshPendingUploadsCount() {
        val scanDir = File(getExternalFilesDir(null), "scans")
        val pendingCount = if (scanDir.exists()) {
            scanDir.listFiles()?.filter { it.isFile }?.size ?: 0
        } else 0
        pendingCountTextView.text = "Pending uploads: $pendingCount"
        val lastUpload = preferencesHelper.getLastUploadTime()
        uploadStatusTextView.text = "Last upload: ${lastUpload ?: "Never"}"
    }

    private fun isScanFile(filename: String?): Boolean {
        if (filename == null) return false
        val lower = filename.lowercase()
        val extensions = preferencesHelper.getScanExtensionsSet()
        return extensions.any { lower.endsWith(it) }
    }

    private fun startMonitoring() {
        val intervalMinutes = preferencesHelper.getScanIntervalMinutes()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val monitorRequest = PeriodicWorkRequestBuilder<FileMonitorWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "file_monitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            monitorRequest
        )
        Toast.makeText(this, "Monitoring started! Will check for new files every $intervalMinutes minutes.", Toast.LENGTH_SHORT).show()
        val immediateRequest = OneTimeWorkRequestBuilder<FileMonitorWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueue(immediateRequest)
        startMonitoringButton.isEnabled = false
        startMonitoringButton.text = "Monitoring Active"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.add("Logout").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> {
                if (item.title == "Logout") {
                    logout()
                    true
                } else {
                    super.onOptionsItemSelected(item)
                }
            }
        }
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                preferencesHelper.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addExtensionChip(container: LinearLayout, ext: String, onRemove: (String) -> Unit) {
        val chip = TextView(this).apply {
            text = ext
            setPadding(32, 16, 32, 16)
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.chip_background)
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.white))
            compoundDrawablePadding = 32
            val removeDrawable = ContextCompat.getDrawable(this@MainActivity, android.R.drawable.ic_menu_close_clear_cancel)
            removeDrawable?.setBounds(0, 0, removeDrawable.intrinsicWidth, removeDrawable.intrinsicHeight)
            setCompoundDrawablesWithIntrinsicBounds(null, null, removeDrawable, null)
            setOnClickListener {
                onRemove(ext)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 8, 8, 8)
            }
        }
        container.addView(chip)
    }

    // ******************* CORRECTED showSettingsDialog *******************
    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val scanInput = dialogView.findViewById<EditText>(R.id.scanIntervalEditText)
        val uploadInput = dialogView.findViewById<EditText>(R.id.uploadIntervalEditText)
        val extensionsContainer = dialogView.findViewById<LinearLayout>(R.id.extensionsContainer)
        val newExtensionEditText = dialogView.findViewById<EditText>(R.id.newExtensionEditText)
        val addButton = dialogView.findViewById<Button>(R.id.addExtensionButton)
        val logoutButton = dialogView.findViewById<Button>(R.id.logoutButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)
        val saveButton = dialogView.findViewById<Button>(R.id.saveButton)

        logoutButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    PreferencesHelper(this).clearSession()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton("No", null)
                .show()
        }

        fun refreshExtensionsList() {
            extensionsContainer.removeAllViews()
            val extensions = preferencesHelper.getScanExtensionsSet()
            for (ext in extensions) {
                addExtensionChip(extensionsContainer, ext) { removedExt ->
                    preferencesHelper.removeFileExtension(removedExt)
                    refreshExtensionsList()
                }
            }
        }

        refreshExtensionsList()

        addButton.setOnClickListener {
            val ext = newExtensionEditText.text.toString().trim()
            if (ext.isNotEmpty()) {
                preferencesHelper.addFileExtension(ext)
                refreshExtensionsList()
                newExtensionEditText.text.clear()
            }
        }

        scanInput.setText(preferencesHelper.getScanIntervalMinutes().toString())
        uploadInput.setText(preferencesHelper.getUploadRetryIntervalMinutes().toString())

        // Build the dialog WITHOUT the framework's buttons
        val builder = AlertDialog.Builder(this)
        builder.setTitle(null)
        builder.setView(dialogView)

        val dialog = builder.show()

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        saveButton.setOnClickListener {
            val newScan = scanInput.text.toString().toIntOrNull()
            val newUpload = uploadInput.text.toString().toIntOrNull()

            if (newScan != null && newScan >= 15) {
                preferencesHelper.saveScanIntervalMinutes(newScan)
            } else {
                Toast.makeText(this, "Scan interval must be >= 15", Toast.LENGTH_SHORT).show()
            }

            if (newUpload != null && newUpload >= 5) {
                preferencesHelper.saveUploadRetryIntervalMinutes(newUpload)
            } else {
                Toast.makeText(this, "Upload retry interval must be >= 5", Toast.LENGTH_SHORT).show()
            }

            restartMonitoringIfActive()
            refreshDetectedFilesCount()
            dialog.dismiss()
        }
    }
    // ********************************************************************

    private fun restartMonitoringIfActive() {
        if (!startMonitoringButton.isEnabled && startMonitoringButton.text == "Monitoring Active") {
            WorkManager.getInstance(this).cancelUniqueWork("file_monitor")
            startMonitoring()
        }
    }

    private fun triggerManualUpload() {
        if (preferencesHelper.getSessionId() == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        Log.d(TAG, "Manual upload triggered, enqueuing FileMonitorWorker")
        val scanWork = OneTimeWorkRequestBuilder<FileMonitorWorker>().build()
        WorkManager.getInstance(this).enqueue(scanWork)
        Toast.makeText(this, "Manual scan started. New files will be uploaded.", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        checkSessionAndRedirect()
        updateStatus()
        refreshDetectedFilesCount()
        refreshPendingUploadsCount()
        restartMonitoringIfActive()
    }
}