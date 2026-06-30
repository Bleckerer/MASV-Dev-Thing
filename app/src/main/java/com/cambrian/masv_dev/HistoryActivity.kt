package com.cambrian.masv_dev

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import com.cambrian.masv_dev.data.UploadDatabase
import com.cambrian.masv_dev.databinding.ActivityHistoryBinding
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.launch

class HistoryActivity : BaseActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: HistoryAdapter
    private val uploadDao by lazy { UploadDatabase.getInstance(this).uploadDao() }
    private val preferencesHelper by lazy { PreferencesHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Theme toggle – uses BaseActivity's fade method
        binding.themeToggleButton.setOnClickListener {
            switchThemeWithFade()
        }

        // More options (three‑dot) button
        binding.moreButton.setOnClickListener { view ->
            showPopupMenu(view)
        }

        adapter = HistoryAdapter()
        binding.recyclerView.adapter = adapter

        loadHistory()
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.history_popup_menu, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_clear_history -> {
                    showResetConfirmation()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val allUploads = uploadDao.getAllUploads()
            if (allUploads.isEmpty()) {
                binding.emptyStateText.visibility = android.view.View.VISIBLE
                binding.recyclerView.visibility = android.view.View.GONE
            } else {
                binding.emptyStateText.visibility = android.view.View.GONE
                binding.recyclerView.visibility = android.view.View.VISIBLE
                adapter.submitList(allUploads)
            }
        }
    }

    private fun showResetConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.reset_upload_history)
            .setMessage(R.string.reset_history_message)
            .setPositiveButton(R.string.reset_button) { _, _ ->
                resetHistory()
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }

    private fun resetHistory() {
        lifecycleScope.launch {
            uploadDao.deleteAll()
            preferencesHelper.saveUploadedFiles(emptySet())
            preferencesHelper.clearCurrentBatchId()
            loadHistory()
            AlertDialog.Builder(this@HistoryActivity)
                .setTitle("History Cleared")
                .setMessage(R.string.upload_history_cleared)
                .setPositiveButton("OK", null)
                .show()
        }
    }
}