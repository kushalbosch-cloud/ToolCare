package com.example.toolcare

data class TechnicalDocument(
    val id: String = "",
    val toolId: String = "",
    val title: String = "",
    val category: String = "Diagrams", // "Diagrams", "Certificates", "SOP", "Technical Datasheet", "Manuals"
    val description: String = "",
    val fileType: String = "PDF",
    val pages: String = "12 Pages",
    val fileSize: String = "2.4 MB",
    val updatedDate: String = "02 Oct 2026",
    val url: String = "https://drive.google.com/drive/my-drive",
    val visibleTo: String = "All Users", // "All Users" or "Admin Only"
    val storageProvider: String = "GOOGLE_DRIVE", // "BACKBLAZE_B2" or "GOOGLE_DRIVE"
    val objectKey: String = "",
    val originalFileName: String = "",
    val contentType: String = "application/pdf",
    val sizeBytes: Long = 0L,
    val companyId: String = "mahindra_001",
    val blockId: String = "vblock_001",
    val uploadedBy: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
)
