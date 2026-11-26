package com.example.toolcare

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationRepository(
    private val firebaseManager: FirebaseManager,
    private val context: Context
) {
    private val notificationHelper = NotificationHelper(context)

    // --- BREAKDOWN NOTIFICATIONS ---

    fun notifyNewBreakdown(
        toolId: String,
        line: String,
        station: String,
        problem: String,
        reportId: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_BD_NEW_${reportId.ifEmpty { "${toolId}_${System.currentTimeMillis()}" }}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "BREAKDOWN_NEW",
            category = "BREAKDOWN",
            priority = "HIGH",
            title = "⚠️ New Breakdown: $toolId",
            message = "Tool $toolId reported as Breakdown on Line $line ($station). Problem: $problem",
            toolId = toolId,
            reportId = reportId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "breakdown",
            company = company,
            block = block,
            createdBy = "System"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerBreakdownAlarm(toolId, "Line $line ($problem)")
    }

    fun notifyCriticalBreakdown(
        toolId: String,
        line: String,
        station: String,
        problem: String,
        reportId: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_CRITICAL_BD_${reportId.ifEmpty { "${toolId}_${System.currentTimeMillis()}" }}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "CRITICAL_BREAKDOWN",
            category = "BREAKDOWN",
            priority = "HIGH",
            title = "🔴 CRITICAL BREAKDOWN: $toolId",
            message = "Emergency halt on Line $line ($station). Immediate maintenance required for $toolId!",
            toolId = toolId,
            reportId = reportId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "breakdown",
            company = company,
            block = block,
            createdBy = "System"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerBreakdownAlarm(toolId, "CRITICAL HALT Line $line ($problem)")
    }

    fun notifyToolUnderRepair(
        toolId: String,
        line: String,
        station: String,
        repairDetails: String,
        reportId: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_BD_REPAIR_${reportId.ifEmpty { "${toolId}_${System.currentTimeMillis()}" }}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "BREAKDOWN_REPAIR",
            category = "BREAKDOWN",
            priority = "ACTION_REQUIRED",
            title = "🔧 Tool Under Repair: $toolId",
            message = "Technician assigned to repair $toolId on Line $line. Details: $repairDetails",
            toolId = toolId,
            reportId = reportId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "repair",
            company = company,
            block = block,
            createdBy = "Maintenance Tech"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerStandardNotification("🔧 Tool Under Repair: $toolId", "Technician assigned on Line $line.")
    }

    fun notifyBreakdownClosed(
        toolId: String,
        line: String,
        station: String,
        actionTaken: String,
        reportId: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_BD_CLOSED_${reportId.ifEmpty { "${toolId}_${System.currentTimeMillis()}" }}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "BREAKDOWN_CLOSED",
            category = "BREAKDOWN",
            priority = "SUCCESS",
            title = "✅ Breakdown Resolved: $toolId",
            message = "Repair handover completed for tool $toolId on Line $line. Action: $actionTaken",
            toolId = toolId,
            reportId = reportId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "breakdown",
            company = company,
            block = block,
            createdBy = "Maintenance Tech"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerStandardNotification("✅ Breakdown Resolved: $toolId", "Repair handover completed on Line $line.")
    }

    // --- PM NOTIFICATIONS ---

    fun notifyPmDueToday(
        toolId: String,
        toolName: String,
        line: String,
        station: String,
        dateStr: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_PM_TODAY_${toolId}_${dateStr}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "PM_DUE_TODAY",
            category = "PM",
            priority = "ACTION_REQUIRED",
            title = "🔧 PM Due Today: $toolId",
            message = "Tool $toolId ($toolName) is scheduled for PM today on Line $line.",
            toolId = toolId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "pm",
            company = company,
            block = block,
            createdBy = "PM System"
        )
        firebaseManager.saveNotification(notification)
    }

    fun notifyPmOverdue(
        toolId: String,
        toolName: String,
        line: String,
        station: String,
        scheduledDate: String,
        daysOverdue: Long,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val notifId = "NOTIF_PM_OVERDUE_${toolId}_${todayStr}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "PM_OVERDUE",
            category = "PM",
            priority = "HIGH",
            title = "⚠️ Overdue PM: $toolId",
            message = "Tool $toolId PM was scheduled on $scheduledDate and is now overdue by $daysOverdue days on Line $line.",
            toolId = toolId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "pm",
            company = company,
            block = block,
            createdBy = "PM System"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerPmNotification("⚠️ Overdue PM: $toolId", "Tool PM is overdue by $daysOverdue days on Line $line.")
    }

    fun notifyPmCompleted(
        toolId: String,
        toolName: String,
        line: String,
        station: String,
        completedDate: String,
        partsUsed: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_PM_COMPLETED_${toolId}_${System.currentTimeMillis()}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "PM_COMPLETED",
            category = "PM",
            priority = "SUCCESS",
            title = "✅ PM Completed: $toolId",
            message = "Preventive maintenance completed for $toolId ($toolName) on Line $line. Remarks: ${partsUsed.ifEmpty { "No Parts Used" }}",
            toolId = toolId,
            line = line,
            station = station,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "pm",
            company = company,
            block = block,
            createdBy = "Technician"
        )
        firebaseManager.saveNotification(notification)
    }

    // --- STOCK & INVENTORY NOTIFICATIONS ---

    fun notifyStockAlert(
        partCode: String,
        partName: String,
        currentStock: Int,
        minStock: Int,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val isOutOfStock = currentStock <= 0
        val notifId = if (isOutOfStock) "NOTIF_STOCK_OUT_${partCode}_${todayStr}" else "NOTIF_STOCK_LOW_${partCode}_${todayStr}"
        val priority = if (isOutOfStock) "HIGH" else "ACTION_REQUIRED"
        val title = if (isOutOfStock) "📦 Out of Stock: $partName" else "📦 Low Stock: $partName"
        val message = if (isOutOfStock) "$partName ($partCode) stock is 0. Reorder immediately!" else "$partName ($partCode) stock ($currentStock) is below minimum level ($minStock)."

        val notification = NotificationModel(
            notificationId = notifId,
            type = if (isOutOfStock) "STOCK_OUT" else "STOCK_LOW",
            category = "STOCK",
            priority = priority,
            title = title,
            message = message,
            partId = partCode,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "stock",
            company = company,
            block = block,
            createdBy = "Inventory System"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerStandardNotification(title, message)
    }

    fun notifyStockIssued(
        partCode: String,
        partName: String,
        qtySold: Int,
        toolId: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_STOCK_ISSUED_${partCode}_${System.currentTimeMillis()}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "STOCK_ISSUED",
            category = "STOCK",
            priority = "INFO",
            title = "📦 Part Issued: $partName",
            message = "$qtySold x $partName ($partCode) issued for tool $toolId.",
            partId = partCode,
            toolId = toolId,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "stock",
            company = company,
            block = block,
            createdBy = "Store Operator"
        )
        firebaseManager.saveNotification(notification)
    }

    fun notifyStockReceived(
        partCode: String,
        partName: String,
        qtyPurchased: Int,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_STOCK_RECEIVED_${partCode}_${System.currentTimeMillis()}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "STOCK_RECEIVED",
            category = "STOCK",
            priority = "SUCCESS",
            title = "📦 Part Received: $partName",
            message = "$qtyPurchased x $partName ($partCode) refilled into stock inventory.",
            partId = partCode,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "stock",
            company = company,
            block = block,
            createdBy = "Store Manager"
        )
        firebaseManager.saveNotification(notification)
    }

    // --- BACKUP TOOL NOTIFICATIONS ---

    fun notifyBackupAlert(
        toolId: String,
        toolName: String,
        event: String, // "ISSUED", "RETURNED", "REQUIRED", "REPLACED", "AVAILABLE"
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_BACKUP_${event}_${toolId}_${System.currentTimeMillis()}"
        val priority = if (event == "REQUIRED") "ACTION_REQUIRED" else if (event == "ISSUED" || event == "REPLACED") "ACTION_REQUIRED" else "INFO"
        val title = when (event) {
            "ISSUED" -> "🔄 Backup Tool Assigned: $toolId"
            "RETURNED" -> "🔄 Backup Tool Returned: $toolId"
            "REPLACED" -> "🔄 Backup Tool Replaced: $toolId"
            "AVAILABLE" -> "🔄 Backup Tool Available: $toolId"
            else -> "🔄 Backup Tool Required: $toolId"
        }
        val message = "Backup tool $toolId ($toolName) status updated to $event."

        val notification = NotificationModel(
            notificationId = notifId,
            type = "BACKUP_$event",
            category = "BACKUP",
            priority = priority,
            title = title,
            message = message,
            toolId = toolId,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "backup",
            company = company,
            block = block,
            createdBy = "Tool Room"
        )
        firebaseManager.saveNotification(notification)
    }

    // --- ADMIN & SYSTEM NOTIFICATIONS ---

    fun notifyAdminAnnouncement(
        title: String,
        message: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_ADMIN_ANNOUNCEMENT_${System.currentTimeMillis()}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "ADMIN_ANNOUNCEMENT",
            category = "ADMIN",
            priority = "INFO",
            title = "📢 Admin Announcement: $title",
            message = message,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "admin",
            company = company,
            block = block,
            createdBy = "Super Admin"
        )
        firebaseManager.saveNotification(notification)
        notificationHelper.triggerStandardNotification("📢 Admin Announcement: $title", message)
    }

    fun notifyToolAddedOrUpdated(
        toolId: String,
        toolName: String,
        actionType: String, // "ADDED", "UPDATED", "DELETED"
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_TOOL_${actionType}_${toolId}_${System.currentTimeMillis()}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "TOOL_$actionType",
            category = "ADMIN",
            priority = "INFO",
            title = "ℹ️ Tool $actionType: $toolId",
            message = "Tool $toolId ($toolName) was $actionType in the system database.",
            toolId = toolId,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "tool_details",
            company = company,
            block = block,
            createdBy = "Admin"
        )
        firebaseManager.saveNotification(notification)
    }

    fun notifyUserAccessChanged(
        userId: String,
        userName: String,
        newRole: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ) {
        val notifId = "NOTIF_USER_ACCESS_${userId}_${System.currentTimeMillis()}"
        val notification = NotificationModel(
            notificationId = notifId,
            type = "ADMIN_ACCESS_CHANGED",
            category = "ADMIN",
            priority = "INFO",
            title = "👤 User Access Changed: $userName",
            message = "User $userName access role updated to '$newRole'.",
            userId = userId,
            createdAt = System.currentTimeMillis(),
            isRead = false,
            actionRoute = "admin",
            company = company,
            block = block,
            createdBy = "Admin"
        )
        firebaseManager.saveNotification(notification)
    }
}
