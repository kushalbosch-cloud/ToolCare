package com.example.toolcare

import java.util.Locale

object B2StorageConfig {
    const val MAX_UPLOAD_SIZE_BYTES = 50 * 1024 * 1024L // 50 MB per file limit
    const val MAX_DOWNLOAD_SIZE_BYTES = 20 * 1024 * 1024L // 20 MB per file limit

    const val DEFAULT_B2_BUCKET = "industrial-app-files"
    const val DEFAULT_B2_REGION = "us-west-004"
    const val DEFAULT_B2_ENDPOINT = "https://s3.us-west-004.backblazeb2.com"

    val SUPPORTED_MIME_TYPES = setOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/webp",
        "video/mp4",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.getDefault(), "%.1f GB", gb)
    }

    fun buildObjectKey(
        companyId: String,
        blockId: String,
        feature: String,
        recordId: String,
        filename: String
    ): String {
        val cleanComp = FirebaseManager.getCompanyId(companyId)
        val cleanBlk = FirebaseManager.getBlockId(blockId)
        val cleanFeature = feature.lowercase(Locale.ROOT).replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val cleanRecord = recordId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val cleanFile = filename.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
        val fileId = "FILE_${System.currentTimeMillis()}"

        return "Companies/$cleanComp/Blocks/$cleanBlk/$cleanFeature/$cleanRecord/${fileId}_$cleanFile"
    }
}
