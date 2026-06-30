package com.cambrian.masv_dev

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
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
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.cambrian.masv_dev.data.UploadDatabase
import com.cambrian.masv_dev.data.UploadEntity
import com.cambrian.masv_dev.data.UploadStatus
import com.cambrian.masv_dev.utils.PreferencesHelper
import java.io.File
import java.util.concurrent.TimeUnit
import android.widget.ImageButton
import kotlinx.coroutines.launch

@SuppressLint("SetTextI18n")
@Suppress("DuplicatedCode")
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
    private lateinit var selectFilesButton: Button  // Replaced scanNowButton
    private lateinit var manualUploadButton: Button
    private lateinit var viewHistoryButton: Button
    private lateinit var cancelUploadButton: Button
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
                Toast.makeText(this, getString(R.string.folder_selected, getFolderPathFromUri(uri)), Toast.LENGTH_LONG).show()
                Log.d(TAG, "Selected folder URI: $uri")
            }
        }
    }

    // NEW: File picker for manual file selection
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            handleSelectedFiles(uris)
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

        findViewById<Button>(R.id.viewPackagesButton).setOnClickListener {
            startActivity(Intent(this, PackagesActivity::class.java))
        }

        preferencesHelper = PreferencesHelper(this)

        checkSessionAndRedirect()

        statusTextView = findViewById(R.id.statusTextView)
        detectedCountTextView = findViewById(R.id.detectedCountTextView)
        pendingCountTextView = findViewById(R.id.pendingCountTextView)
        uploadStatusTextView = findViewById(R.id.uploadStatusTextView)
        selectFolderButton = findViewById(R.id.selectFolderButton)
        startMonitoringButton = findViewById(R.id.startMonitoringButton)
        selectFilesButton = findViewById(R.id.selectFilesButton)  // NEW ID
        manualUploadButton = findViewById(R.id.manualUploadButton)
        viewHistoryButton = findViewById(R.id.viewHistoryButton)
        cancelUploadButton = findViewById(R.id.cancelUploadButton)
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
                Toast.makeText(this, R.string.please_select_folder_first, Toast.LENGTH_SHORT).show()
            }
        }

        // REPLACED: Select Files (instead of Manual Scan)
        selectFilesButton.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        manualUploadButton.setOnClickListener { triggerManualUpload() }

        viewHistoryButton.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
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
                val speed = progress.getDouble("speed", 0.0)
                val etaSeconds = progress.getLong("eta", 0L)

                progressOverlay.isVisible = true

                cancelUploadButton.visibility = View.VISIBLE
                cancelUploadButton.isEnabled = true
                cancelUploadButton.text = "Cancel Upload"
                cancelUploadButton.setOnClickListener {
                    WorkManager.getInstance(this).cancelWorkById(activeWork.id)
                    cancelUploadButton.isEnabled = false
                    cancelUploadButton.text = "Cancelling..."
                }

                if (totalFiles > 0) {
                    progressTitle.text = getString(R.string.uploading_file_x_of_y, currentFile, totalFiles)

                    val detailsText = if (fileName.isNotEmpty() && totalBytes > 0) {
                        val transferredMB = bytesTransferred / (1024 * 1024)
                        val totalMB = totalBytes / (1024 * 1024)
                        var text = fileName + "\n$transferredMB MB / $totalMB MB"
                        if (speed > 0) {
                            var speedEta = String.format("%.1f", speed) + " MB/s"
                            if (etaSeconds > 0) {
                                speedEta += " · ~" + formatEta(etaSeconds)
                            }
                            text += "\n$speedEta"
                        }
                        text
                    } else if (fileName.isNotEmpty()) {
                        getString(R.string.file_prefix, fileName)
                    } else {
                        getString(R.string.preparing_upload)
                    }
                    progressDetails.text = detailsText

                    if (totalBytes > 0) {
                        val percent = (bytesTransferred * 100 / totalBytes).toInt()
                        progressBar.isIndeterminate = false
                        progressBar.max = 100
                        progressBar.progress = percent
                        progressPercent.isVisible = true
                        progressPercent.text = "$percent%"
                    } else {
                        progressBar.isIndeterminate = true
                        progressPercent.isVisible = false
                    }
                } else {
                    progressTitle.text = getString(R.string.preparing_upload)
                    progressDetails.text = ""
                    progressBar.isIndeterminate = true
                    progressPercent.isVisible = false
                }
            } else {
                cancelUploadButton.visibility = View.GONE
                cancelUploadButton.isEnabled = true
                cancelUploadButton.text = "Cancel Upload"

                if (preferencesHelper.hasPendingBatch()) {
                    progressOverlay.isVisible = true
                    progressTitle.text = getString(R.string.upload_paused_retrying)
                    progressDetails.text = getString(R.string.waiting_for_next_attempt)
                    progressBar.isIndeterminate = true
                    progressPercent.isVisible = false
                } else {
                    if (progressOverlay.isVisible) {
                        progressTitle.text = getString(R.string.finalising_package)
                        progressDetails.text = ""
                        progressBar.isIndeterminate = true
                        progressPercent.isVisible = false
                        android.os.Handler(mainLooper).postDelayed({
                            progressOverlay.isVisible = false
                        }, 1500)
                    } else {
                        progressOverlay.isVisible = false
                    }
                    refreshDetectedFilesCount()
                    refreshPendingUploadsCount()
                }
            }
        }
    }

    // ---- NEW: Handle selected files from picker ----
    private fun handleSelectedFiles(uris: List<Uri>) {
        lifecycleScope.launch {
            val batchId = System.currentTimeMillis().toString()
            val uriStrings = uris.map { it.toString() }.toSet()

            // Save to Preferences
            preferencesHelper.saveBatch(batchId, uriStrings, "", "")
            preferencesHelper.saveCurrentBatchId(batchId)

            // Insert into Room as PENDING
            val uploadDao = UploadDatabase.getInstance(this@MainActivity).uploadDao()
            val entities = uris.map { uri ->
                val fileName = getFileNameFromUri(uri) ?: "unknown"
                UploadEntity(
                    fileName = fileName,
                    fileSize = getFileSizeFromUri(uri) ?: 0L,
                    uri = uri.toString(),
                    batchId = batchId,
                    status = UploadStatus.PENDING,
                    progressPercent = 0,
                    timestamp = System.currentTimeMillis()
                )
            }
            uploadDao.insertAll(entities)

            Toast.makeText(
                this@MainActivity,
                getString(R.string.files_added_to_queue, uris.size),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun checkSessionAndRedirect() {
        if (preferencesHelper.getSessionId() == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun formatEta(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
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
                    .setTitle(R.string.storage_permission_title)
                    .setMessage(R.string.storage_permission_message)
                    .setPositiveButton(R.string.open_settings) { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = "package:$packageName".toUri()
                        startActivity(intent)
                    }
                    .setNegativeButton(R.string.cancel_button, null)
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
        statusTextView.text = if (folderPath != null) {
            getString(R.string.monitoring_prefix, folderPath)
        } else {
            getString(R.string.no_folder_selected)
        }
    }

    private fun updateButtonsEnabledState() {
        val folderSelected = preferencesHelper.getMonitoredFolderUri() != null
        startMonitoringButton.isEnabled = folderSelected
        selectFilesButton.isEnabled = true  // Always enabled
        manualUploadButton.isEnabled = true // Always enabled
    }

    private fun refreshDetectedFilesCount() {
        val folderUriString = preferencesHelper.getMonitoredFolderUri()
        if (folderUriString == null) {
            detectedCountTextView.text = getString(R.string.no_folder_selected_detected)
            return
        }
        val folderUri = folderUriString.toUri()
        Thread {
            try {
                val root = DocumentFile.fromTreeUri(this, folderUri)
                if (root == null) {
                    runOnUiThread { detectedCountTextView.text = getString(R.string.unable_to_access_folder) }
                    return@Thread
                }
                val count = countScanFilesRecursively(root)
                runOnUiThread { detectedCountTextView.text = getString(R.string.detected_files_in_folder, count) }
            } catch (e: Exception) {
                Log.e(TAG, "Error counting files", e)
                runOnUiThread { detectedCountTextView.text = getString(R.string.error_detected_files) }
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
        pendingCountTextView.text = getString(R.string.pending_uploads_with_count, pendingCount)
        val lastUpload = preferencesHelper.getLastUploadTime()
        uploadStatusTextView.text = getString(R.string.last_upload_with_time, lastUpload ?: getString(R.string.never))
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
        Toast.makeText(this, getString(R.string.monitoring_started, intervalMinutes), Toast.LENGTH_SHORT).show()
        val immediateRequest = OneTimeWorkRequestBuilder<FileMonitorWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueue(immediateRequest)
        startMonitoringButton.isEnabled = false
        startMonitoringButton.text = getString(R.string.monitoring_active)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        menu.add(R.string.logout_title).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                showSettingsDialog()
                true
            }
            else -> {
                if (item.title == getString(R.string.logout_title)) {
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
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                preferencesHelper.clearSession()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
            .setNegativeButton(R.string.no, null)
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
                .setTitle(R.string.logout_title)
                .setMessage(R.string.logout_message)
                .setPositiveButton(R.string.yes) { _, _ ->
                    PreferencesHelper(this).clearSession()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                .setNegativeButton(R.string.no, null)
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
                Toast.makeText(this, R.string.scan_interval_too_low, Toast.LENGTH_SHORT).show()
            }
            if (newUpload != null && newUpload >= 5) {
                preferencesHelper.saveUploadRetryIntervalMinutes(newUpload)
            } else {
                Toast.makeText(this, R.string.upload_retry_too_low, Toast.LENGTH_SHORT).show()
            }
            restartMonitoringIfActive()
            refreshDetectedFilesCount()
            dialog.dismiss()
        }
    }

    private fun restartMonitoringIfActive() {
        if (!startMonitoringButton.isEnabled && startMonitoringButton.text == getString(R.string.monitoring_active)) {
            WorkManager.getInstance(this).cancelUniqueWork("file_monitor")
            startMonitoring()
        }
    }

    // ---- UPDATED: Manual Upload now checks for pending batch first ----
    private fun triggerManualUpload() {
        if (preferencesHelper.getSessionId() == null) {
            Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Check if we have a pending batch (from folder scan or manual selection)
        val batchId = preferencesHelper.getCurrentBatchId()
        if (batchId != null && preferencesHelper.getBatchUris(batchId)?.isNotEmpty() == true) {
            // Upload the pending batch
            Log.d(TAG, "Uploading pending batch: $batchId")
            Toast.makeText(this, R.string.uploading_pending_batch, Toast.LENGTH_SHORT).show()
            val inputData = workDataOf("batchId" to batchId)
            val batchWork = OneTimeWorkRequestBuilder<BatchUploadWorker>()
                .setInputData(inputData)
                .addTag("batch_upload")
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(this).enqueue(batchWork)
        } else {
            // Fallback: scan the folder (original behaviour)
            Log.d(TAG, "No pending batch, scanning folder")
            val scanWork = OneTimeWorkRequestBuilder<FileMonitorWorker>().build()
            WorkManager.getInstance(this).enqueue(scanWork)
            Toast.makeText(this, R.string.manual_scan_started, Toast.LENGTH_SHORT).show()
        }
    }

    // ---- Helper methods for file picker ----
    private fun getFileNameFromUri(uri: Uri): String? {
        return DocumentFile.fromSingleUri(this, uri)?.name
            ?: uri.path?.substringAfterLast('/')
    }

    private fun getFileSizeFromUri(uri: Uri): Long? {
        return try {
            contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize
            }
        } catch (e: Exception) {
            null
        }
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