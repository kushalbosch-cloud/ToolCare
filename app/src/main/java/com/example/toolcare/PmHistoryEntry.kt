package com.example.toolcare

data class PmHistoryEntry(
    val historyId: String = "",
    val toolId: String = "",
    val toolName: String = "",
    val line: String = "",
    val station: String = "",
    val scheduledDate: String = "",
    val actualCompletedDate: String = "",
    val completedDate: String = "",
    val completedBy: String = "",
    val completedByName: String = "",
    val status: String = "DONE",
    val pmCycleMonths: Int = 6,
    val partsUsed: String = "No Parts Used",
    val remarks: String = "",
    val checklist: Map<String, Boolean> = emptyMap(),
    val company: String = "",
    val block: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
