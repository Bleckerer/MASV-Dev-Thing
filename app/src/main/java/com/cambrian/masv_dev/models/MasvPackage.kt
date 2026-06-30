package com.cambrian.masv_dev.models

data class MasvPackage(
    val id: String,
    val name: String,
    val state: String,
    val created_at: String,
    val total_files: Int? = null,      // renamed from file_count
    val size: Long? = null,
    val access_token: String? = null,  // NEW – used to fetch files
    val files: List<PackageFile>? = null
)