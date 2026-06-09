package com.cambrian.masv_dev.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID_STATUS = "scan_monitor_status"
        const val CHANNEL_ID_ALERTS = "scan_monitor_alerts"
        const val NOTIFICATION_ID_NEW_FILES = 1001
        const val NOTIFICATION_ID_UPLOAD_START = 1002
        const val NOTIFICATION_ID_NO_NETWORK = 1003
        const val NOTIFICATION_ID_UPLOAD_SUCCESS = 1004
        const val NOTIFICATION_ID_UPLOAD_FAIL = 1005
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val statusChannel = NotificationChannel(
                CHANNEL_ID_STATUS,
                "Upload Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows upload progress and file detection"
                setShowBadge(true)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Important alerts about connection issues or errors"
                setShowBadge(true)
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(statusChannel)
            manager.createNotificationChannel(alertsChannel)
        }
    }

    fun showNewFilesDetected(count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New files found")
            .setContentText("$count new file(s) ready to upload.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_NEW_FILES, notification)
    }

    fun showUploadStarted(fileName: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Uploading to MASV")
            .setContentText("Uploading: $fileName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_UPLOAD_START, notification)
    }

    fun showWaitingForNetwork() {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("No internet connection")
            .setContentText("Uploads are paused. Will resume automatically when connected.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_NO_NETWORK, notification)
    }

    fun dismissWaitingForNetwork() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID_NO_NETWORK)
    }

    fun showUploadSuccess(fileCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_STATUS)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("Upload complete")
            .setContentText("$fileCount file(s) successfully uploaded to MASV.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_UPLOAD_SUCCESS, notification)
    }

    fun showUploadFailure(error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Upload failed")
            .setContentText(error)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID_UPLOAD_FAIL, notification)
    }

    fun cancelUploadStarted() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID_UPLOAD_START)
    }
}