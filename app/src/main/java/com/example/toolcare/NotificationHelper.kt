package com.example.toolcare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    private val channelId = "toolcare_notifications_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()

        val channel = NotificationChannel(
            channelId,
            "ToolCare High Priority Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "High priority push notifications for breakdowns, backup tool assignments, and PM alerts"
            try {
                setSound(soundUri, audioAttributes)
            } catch (_: Exception) {
                setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI, audioAttributes)
            }
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            enableLights(true)
            setShowBadge(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

        try {
            manager.createNotificationChannel(channel)
        } catch (_: SecurityException) {
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
    }

    fun triggerBreakdownAlarm(toolId: String, lineInfo: String) {
        triggerStandardNotification(
            title = "🚨 Emergency Breakdown: $toolId",
            message = if (lineInfo.startsWith("Line:") || lineInfo.startsWith("Tool breakdown")) lineInfo else "Tool breakdown reported on Line: $lineInfo",
            targetScreen = "Breakdown",
            toolId = toolId
        )
    }

    fun triggerBackupAssignmentNotification(origToolId: String, backupToolId: String, shopBlock: String) {
        triggerStandardNotification(
            title = "⚙️ Backup Tool Assigned: $backupToolId",
            message = "Backup Tool $backupToolId assigned for breakdown tool $origToolId in $shopBlock",
            targetScreen = "Backup Tools",
            toolId = backupToolId
        )
    }

    fun triggerPmNotification(title: String, message: String) {
        triggerStandardNotification(
            title = title,
            message = message,
            targetScreen = "PM Management",
            toolId = ""
        )
    }

    fun triggerStandardNotification(title: String, message: String, targetScreen: String = "Home", toolId: String = "") {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("screen", targetScreen)
            if (toolId.isNotBlank()) putExtra("toolId", toolId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setSound(soundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            manager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            try {
                builder.setSound(null)
                manager.notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (_: Exception) {
                // Ignore if POST_NOTIFICATIONS permission is not granted
            }
        } catch (_: Exception) {
            // Ignore any system notification errors safely
        }
    }
}
