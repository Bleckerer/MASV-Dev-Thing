package com.cambrian.masv_dev

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cambrian.masv_dev.databinding.ItemPackageBinding
import com.cambrian.masv_dev.databinding.ItemPackageFileBinding
import com.cambrian.masv_dev.models.MasvPackage
import com.cambrian.masv_dev.models.PackageFile
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PackagesAdapter(
    private var items: MutableList<MasvPackage> = mutableListOf(),
    private val onExpandRequest: (MasvPackage) -> Unit
) : RecyclerView.Adapter<PackagesAdapter.ViewHolder>() {

    private val TAG = "PackagesAdapter"
    private val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val expandedStates = mutableMapOf<String, Boolean>()

    // UTC parser with milliseconds
    private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPackageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<MasvPackage>) {
        items.clear()
        items.addAll(newItems)
        expandedStates.clear()
        notifyDataSetChanged()
    }

    fun updateFiles(packageId: String, files: List<PackageFile>) {
        val index = items.indexOfFirst { it.id == packageId }
        if (index != -1) {
            val updated = items[index].copy(files = files)
            items[index] = updated
            notifyItemChanged(index)
            Log.d(TAG, "Updated files for package $packageId, now have ${files.size} files")
        } else {
            Log.e(TAG, "Package $packageId not found in list")
        }
    }

    inner class ViewHolder(
        private val binding: ItemPackageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentPackage: MasvPackage? = null

        init {
            binding.root.setOnClickListener {
                currentPackage?.let { pkg ->
                    val isExpanded = expandedStates[pkg.id] == true
                    expandedStates[pkg.id] = !isExpanded
                    if (!isExpanded) {
                        onExpandRequest(pkg)
                    }
                    notifyItemChanged(adapterPosition)
                }
            }
        }

        fun bind(item: MasvPackage) {
            currentPackage = item
            binding.packageNameText.text = item.name
            binding.packageStateText.text = item.state.uppercase()
            binding.packageDateText.text = formatDate(item.created_at)
            binding.packageFilesText.text = "${item.total_files ?: 0} files"

            val statusColor = when (item.state.lowercase()) {
                "open" -> 0xFF4CAF50.toInt()
                "closed" -> 0xFFFF9800.toInt()
                "completed" -> 0xFF2196F3.toInt()
                else -> 0xFF9E9E9E.toInt()
            }
            binding.packageStateText.setBackgroundColor(statusColor)
            binding.packageStateText.setTextColor(0xFFFFFFFF.toInt())

            val isExpanded = expandedStates[item.id] == true
            if (isExpanded && item.files != null) {
                showFiles(item.files!!)
            } else {
                hideFiles()
            }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val parsed = utcParser.parse(dateString)
                if (parsed != null) {
                    val date = displayDateFormat.format(parsed)
                    val time = dateFormat.format(parsed)
                    "$date at $time"
                } else {
                    dateString
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse date: $dateString", e)
                dateString
            }
        }

        private fun showFiles(files: List<PackageFile>) {
            binding.filesContainer.removeAllViews()

            for (file in files) {
                val fileBinding = ItemPackageFileBinding.inflate(
                    LayoutInflater.from(binding.root.context),
                    binding.filesContainer,
                    false
                )
                fileBinding.fileNameText.text = file.name
                fileBinding.fileSizeText.text = formatFileSize(file.size)
                // Set state – now "completed" or "pending"
                fileBinding.fileStateText.text = file.state.uppercase()
                binding.filesContainer.addView(fileBinding.root)
            }
            binding.filesContainer.visibility = View.VISIBLE
        }

        private fun hideFiles() {
            binding.filesContainer.removeAllViews()
            binding.filesContainer.visibility = View.GONE
        }

        private fun formatFileSize(size: Long): String {
            return when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
                else -> "${size / (1024 * 1024 * 1024)} GB"
            }
        }
    }
}