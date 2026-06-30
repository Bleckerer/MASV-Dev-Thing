package com.cambrian.masv_dev.models

data class PackageFile(
    val id: String,
    val name: String,
    val size: Long,
    val state: String // "completed", "pending", etc.
)