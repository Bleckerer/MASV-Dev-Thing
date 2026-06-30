package com.cambrian.masv_dev

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.cambrian.masv_dev.databinding.ActivityPackagesBinding
import com.cambrian.masv_dev.models.MasvPackage   // ✅ Import MasvPackage
import com.cambrian.masv_dev.utils.PreferencesHelper
import kotlinx.coroutines.launch

class PackagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPackagesBinding
    private lateinit var adapter: PackagesAdapter
    private val preferencesHelper by lazy { PreferencesHelper(this) }
    private val TAG = "PackagesActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPackagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "MASV Packages"

        adapter = PackagesAdapter(
            items = mutableListOf(),
            onExpandRequest = { pkg -> fetchFilesForPackage(pkg) }
        )
        binding.packagesRecyclerView.adapter = adapter

        loadPackages()
    }

    private fun loadPackages() {
        val apiKey = preferencesHelper.getSessionId()
        val teamId = preferencesHelper.getTeamId()

        if (apiKey.isNullOrEmpty() || teamId.isNullOrEmpty()) {
            Toast.makeText(this, "Not logged in. Please log in again.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "apiKey or teamId is null")
            finish()
            return
        }

        Log.d(TAG, "Loading packages with teamId: $teamId")

        binding.loadingProgress.visibility = View.VISIBLE
        binding.emptyStateText.visibility = View.GONE
        binding.packagesRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val packages = ApiClient.getPackages(apiKey, teamId)
                Log.d(TAG, "Received ${packages.size} packages")
                binding.loadingProgress.visibility = View.GONE
                if (packages.isEmpty()) {
                    binding.emptyStateText.visibility = View.VISIBLE
                    binding.emptyStateText.text = "No packages found"
                    binding.packagesRecyclerView.visibility = View.GONE
                } else {
                    binding.emptyStateText.visibility = View.GONE
                    binding.packagesRecyclerView.visibility = View.VISIBLE
                    adapter.submitList(packages)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load packages", e)
                binding.loadingProgress.visibility = View.GONE
                binding.emptyStateText.visibility = View.VISIBLE
                binding.emptyStateText.text = "Failed to load packages: ${e.message ?: "unknown error"}"
                Toast.makeText(
                    this@PackagesActivity,
                    "Failed to load packages: ${e.message ?: "unknown error"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun fetchFilesForPackage(pkg: MasvPackage) {
        val token = pkg.access_token
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "No access token for package ${pkg.id}")
            Toast.makeText(this, "Package token not found", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val files = ApiClient.getPackageFiles(token, pkg.id)
                Log.d(TAG, "Fetched ${files.size} files for package ${pkg.name}")
                adapter.updateFiles(pkg.id, files)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch files for package ${pkg.id}", e)
                Toast.makeText(
                    this@PackagesActivity,
                    "Failed to load files: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}