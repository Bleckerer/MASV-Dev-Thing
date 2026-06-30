package com.cambrian.masv_dev

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cambrian.masv_dev.data.UploadEntity
import com.cambrian.masv_dev.databinding.ItemHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private var items: List<UploadEntity> = emptyList()
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<UploadEntity>) {
        items = newItems
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: UploadEntity) {
            binding.fileNameText.text = item.fileName
            binding.fileSizeText.text = formatFileSize(item.fileSize)
            binding.statusText.text = item.status.name
            binding.progressBar.progress = item.progressPercent
            binding.timestampText.text = dateFormat.format(Date(item.timestamp))

            // Color status badge
            val statusColor = when (item.status) {
                com.cambrian.masv_dev.data.UploadStatus.COMPLETED -> 0xFF4CAF50.toInt()
                com.cambrian.masv_dev.data.UploadStatus.FAILED -> 0xFFF44336.toInt()
                com.cambrian.masv_dev.data.UploadStatus.UPLOADING -> 0xFFFF9800.toInt()
                com.cambrian.masv_dev.data.UploadStatus.PENDING -> 0xFF9E9E9E.toInt()
            }
            binding.statusText.setBackgroundColor(statusColor)
            binding.statusText.setTextColor(0xFFFFFFFF.toInt())
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