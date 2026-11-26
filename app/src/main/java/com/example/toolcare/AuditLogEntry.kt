package com.example.toolcare

data class AuditLogEntry(
    val id: String = "",
    val timestamp: String = "",
    val user: String = "Super Admin",
    val module: String = "",
    val action: String = "",
    val recordId: String = "",
    val oldValue: String = "",
    val newValue: String = ""
)
