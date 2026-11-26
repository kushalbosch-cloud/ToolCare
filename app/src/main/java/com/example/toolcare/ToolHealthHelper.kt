package com.example.toolcare

import java.text.SimpleDateFormat
import java.util.*

object ToolHealthHelper {

    fun getToolHealthStatus(
        toolId: String,
        breakdownList: List<BreakdownEntry>,
        pmEntries: List<PmEntry>
    ): String {
        val toolBreakdowns = breakdownList.count { it != null && it.originalToolId == toolId }
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = Date()
        
        val missedPmCount = pmEntries.count { entry ->
            entry != null && 
            entry.toolId == toolId && 
            entry.status == "Pending" && 
            try {
                val dateStr = entry.scheduledDate
                if (dateStr.isNullOrBlank()) false
                else {
                    val scheduledDate = dateFormat.parse(dateStr)
                    scheduledDate != null && scheduledDate.before(currentDate)
                }
            } catch (_: Exception) {
                false
            }
        }

        return when {
            toolBreakdowns > 2 -> "🔴 High Risk (Frequent Breakdown)"
            missedPmCount > 1 -> "🟠 High Risk (Missed PMs)"
            toolBreakdowns > 0 || missedPmCount > 0 -> "🟡 Monitor"
            else -> "🟢 Healthy"
        }
    }
    
    fun isHighRisk(toolId: String, breakdownList: List<BreakdownEntry>, pmEntries: List<PmEntry>): Boolean {
        val status = getToolHealthStatus(toolId, breakdownList, pmEntries)
        return status.contains("High Risk")
    }
}
