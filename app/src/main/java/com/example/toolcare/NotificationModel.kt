package com.example.toolcare

import com.google.firebase.database.PropertyName

data class NotificationModel(
    val notificationId: String = "",
    val type: String = "ALERT", // PM_DUE_TODAY, PM_DUE_SOON, PM_OVERDUE, PM_MISSED, PM_COMPLETED, PM_RESCHEDULED, BREAKDOWN_NEW, BREAKDOWN_ASSIGNED, BREAKDOWN_REPAIR, BREAKDOWN_CLOSED, CRITICAL_BREAKDOWN, BACKUP_REQUIRED, BACKUP_ASSIGNED, BACKUP_REPLACED, BACKUP_RETURNED, BACKUP_AVAILABLE, STOCK_LOW, STOCK_OUT, STOCK_ADDED, STOCK_ISSUED, STOCK_RECEIVED, ADMIN_NEW_USER, ADMIN_ACCESS_CHANGED, ADMIN_ANNOUNCEMENT, TOOL_ADDED, TOOL_UPDATED, TOOL_DELETED, SYSTEM
    val category: String = "BREAKDOWN", // PM, BREAKDOWN, BACKUP, STOCK, ADMIN, SYSTEM
    val priority: String = "HIGH", // HIGH (🔴 Critical), ACTION_REQUIRED (🟠 Medium), SUCCESS (🟢 Normal), INFO (🔵 Info)
    val title: String = "",
    val message: String = "",
    val toolId: String = "",
    val partId: String = "",
    val reportId: String = "",
    val userId: String = "",
    val line: String = "",
    val station: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val readAt: Long = 0L,
    @get:PropertyName("isRead") @set:PropertyName("isRead") var isRead: Boolean = false,
    val actionRoute: String = "breakdown", // pm, breakdown, backup, stock, admin, tool_details, home
    val status: String = "ACTIVE",
    val company: String = "",
    val block: String = "",
    val createdBy: String = "System"
)
