package com.example.toolcare

data class TrashEntry(
    val id: String = "",
    val entityType: String = "", // "OriginalTool", "BackupTool", "BreakdownEntry", "PmEntry", "Product"
    val originalPath: String = "",
    val recordId: String = "",
    val title: String = "",
    val companyId: String = "",
    val blockId: String = "",
    val dataJson: String = "",
    val deletedBy: String = "",
    val deletedAt: Long = System.currentTimeMillis(),
    val deletedAtStr: String = ""
)
