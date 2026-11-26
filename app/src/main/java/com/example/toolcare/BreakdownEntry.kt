package com.example.toolcare

import com.google.firebase.database.PropertyName

data class UsedSparePart(
    val partId: String = "",
    val partName: String = "",
    val quantityUsed: Int = 1,
    @get:PropertyName("isManualEntry") @set:PropertyName("isManualEntry") var isManualEntry: Boolean = false
)

data class BreakdownEntry(
    val entryId: String = "",
    val originalToolId: String = "",
    val backupToolId: String = "",
    val line: String = "",
    val station: String = "",
    val problem: String = "",
    val timestamp: String = "",
    var status: String = "Reported",
    var repairDetails: String = "",
    var usedSpareParts: List<UsedSparePart> = emptyList(),
    var handoverPerson: String = "",
    var shift: String = "",
    val company: String = "",
    val block: String = "",
    val createdAt: Long = 0L,
    val reportedBy: String = "",
    val repairedAt: Long = 0L,
    val repairedBy: String = "",
    val handoverAt: Long = 0L
)
