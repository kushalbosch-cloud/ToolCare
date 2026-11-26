package com.example.toolcare

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_breakdowns")
data class LocalBreakdownEntity(
    @PrimaryKey val entryId: String,
    val originalToolId: String = "",
    val backupToolId: String = "",
    val line: String = "",
    val station: String = "",
    val problem: String = "",
    val timestamp: String = "",
    val status: String = "Reported",
    val repairDetails: String = "",
    val handoverPerson: String = "",
    val shift: String = "",
    val company: String = "",
    val block: String = "",
    val createdAt: Long = 0L,
    val reportedBy: String = "",
    val repairedAt: Long = 0L,
    val handoverAt: Long = 0L,
    val syncStatus: Boolean = false // false = pending upload to Firebase, true = synced
)
