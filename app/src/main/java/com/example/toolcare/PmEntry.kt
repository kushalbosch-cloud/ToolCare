package com.example.toolcare

data class PmEntry(
    val pmId: String = "",
    val toolId: String = "",
    val toolName: String = "",
    val line: String = "",
    val station: String = "",
    val scheduledDate: String = "",
    val status: String = "PENDING", // PENDING, TODAY, OVERDUE, IN_PROGRESS, DONE
    val active: Boolean = true,
    val completed: Boolean = false,
    val startedAt: String = "",
    val startedBy: String = "",
    val partsUsed: String = "No Parts Used",
    val completedDate: String = "",
    val pmCycleMonths: Int = 6,
    val company: String = "",
    val block: String = ""
)
