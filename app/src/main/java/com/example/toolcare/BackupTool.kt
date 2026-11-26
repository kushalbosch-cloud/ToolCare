package com.example.toolcare

import com.google.firebase.database.PropertyName

data class BackupTool(
    val toolId: String = "",
    val make: String = "Bosch",
    val model: String = "",
    val serialNumber: String = "",
    val toolType: String = "Cordless Drill",
    val line: String = "Assembly-2",
    val station: String = "ST-14",
    val torque: String = "12 Nm",
    val rpm: String = "2700",
    val status: String = "Available",
    val assignedUser: String = "Unassigned",
    val location: String = "Tool Room",
    val lastPmDate: String = "20 Sep 2026",
    val nextPmDate: String = "20 Oct 2026",
    val pmFrequency: String = "Weekly",
    val remarks: String = "",
    val company: String = "",
    val block: String = "",
    val lastVerifiedTimestamp: Long = 0L,
    @get:PropertyName("isVerifiedToday") @set:PropertyName("isVerifiedToday") var isVerifiedToday: Boolean = false
) {
    val isVerified: Boolean
        get() {
            if (isVerifiedToday) return true
            if (lastVerifiedTimestamp <= 0L) return false
            val current = System.currentTimeMillis()
            val diff = current - lastVerifiedTimestamp
            return diff in 0 until (24 * 60 * 60 * 1000L)
        }

    val hoursRemainingUntilReset: Int
        get() {
            if (lastVerifiedTimestamp <= 0L) return 0
            val elapsedMs = System.currentTimeMillis() - lastVerifiedTimestamp
            val remainingMs = (24 * 60 * 60 * 1000L) - elapsedMs
            return if (remainingMs > 0) (remainingMs / (60 * 60 * 1000L)).toInt() + 1 else 0
        }
}
