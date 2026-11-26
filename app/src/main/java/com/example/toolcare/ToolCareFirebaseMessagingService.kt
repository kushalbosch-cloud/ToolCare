package com.example.toolcare

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Locale

class ToolCareFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed FCM token: $token")
        subscribeToTopics()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message Received From: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isNotEmpty()) {
            val title = data["title"] ?: remoteMessage.notification?.title ?: "🚨 Factory Alert"
            val body = data["body"] ?: remoteMessage.notification?.body ?: "New maintenance update"
            val targetScreen = data["screen"] ?: "Home"
            val toolId = data["toolId"] ?: ""

            Log.d(TAG, "Received FCM Data Payload: Title='$title', Screen='$targetScreen'")

            val notificationHelper = NotificationHelper(applicationContext)
            notificationHelper.triggerStandardNotification(
                title = title,
                message = body,
                targetScreen = targetScreen,
                toolId = toolId
            )
        }
    }

    companion object {
        private const val TAG = "ToolCareFCMService"

        fun subscribeUserScopedTopics(company: String = "Mahindra", block: String = "V-Block", role: String = "technician") {
            try {
                val fcm = FirebaseMessaging.getInstance()
                val cleanComp = FirebaseManager.getCompanyId(company)
                val cleanBlk = FirebaseManager.getBlockId(block)
                val cleanRole = role.lowercase(Locale.ROOT).replace("[^a-zA-Z0-9_-]".toRegex(), "_")

                val scopedTopics = listOf(
                    "topic_company_$cleanComp",
                    "topic_block_${cleanComp}_$cleanBlk",
                    "topic_role_${cleanComp}_$cleanRole",
                    "maintenance_team",
                    "breakdown_alerts"
                )

                scopedTopics.forEach { topic ->
                    fcm.subscribeToTopic(topic).addOnCompleteListener { task ->
                        if (task.isSuccessful) Log.d(TAG, "Subscribed to FCM Scoped Topic '$topic'")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM Scoped Topic subscription error", e)
            }
        }

        fun subscribeToTopics() {
            try {
                val topics = listOf("maintenance_team", "super_admins", "technicians", "store_team", "breakdown_alerts", "pm_alerts", "v_block")
                topics.forEach { topic ->
                    FirebaseMessaging.getInstance().subscribeToTopic(topic)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) Log.d(TAG, "Subscribed to '$topic' topic")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM Topic subscription failed", e)
            }
        }

        fun unsubscribeFromTopics() {
            try {
                val topics = listOf("maintenance_team", "super_admins", "technicians", "store_team", "breakdown_alerts", "pm_alerts", "v_block")
                topics.forEach { topic ->
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                }
            } catch (e: Exception) {
                Log.e(TAG, "FCM Topic unsubscription failed", e)
            }
        }
    }
}
