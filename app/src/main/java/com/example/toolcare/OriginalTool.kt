package com.example.toolcare

data class OriginalTool(
    val toolId: String = "",
    val toolName: String = "",
    val make: String = "Bosch",
    val model: String = "",
    val serialNumber: String = "",
    val fitment: String = "", // Fitment / Assembly Name
    val line: String = "",
    val station: String = "",
    val status: String = "OK",
    val torque: String = "",
    val rpm: String = "",
    val application: String = "",
    val installDate: String = "",
    val pmCycleMonths: Int = 6,
    val lastPmDate: String = "",
    val nextPmDate: String = "",
    val active: Boolean = true,
    val company: String = "", // Company Name
    val block: String = "", // Block / Shop Name
    val documentUrl: String = "",
    val photoUrl: String = "",
    val manualName: String = "",
    val certName: String = ""
)
