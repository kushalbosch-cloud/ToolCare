package com.example.toolcare

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private val BD_FORMATS = listOf("dd/MM/yyyy HH:mm", "dd MMM yyyy, HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm")

private fun parseReportedAt(ts: String): Long? {
    for (f in BD_FORMATS) {
        try {
            val sdf = SimpleDateFormat(f, Locale.getDefault()).apply { isLenient = false }
            return sdf.parse(ts.trim())?.time
        } catch (_: Exception) {}
    }
    return null
}

private fun formatDowntime(ms: Long): String {
    val mins = (ms / 60_000).coerceAtLeast(0)
    val d = mins / (60 * 24)
    val h = (mins / 60) % 24
    val m = mins % 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        else -> "${m}m"
    }
}

@Composable
fun DownNowSection(
    breakdowns: List<BreakdownEntry>,
    onOpen: (BreakdownEntry) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = remember(breakdowns) {
        breakdowns.filter { 
            it.status != "Handover Completed" &&
            !it.entryId.startsWith("BD_AUTO_") &&
            !it.status.contains("HOLD", ignoreCase = true) &&
            !it.problem.contains("Verification Failed", ignoreCase = true)
        }.sortedBy { parseReportedAt(it.timestamp) ?: Long.MAX_VALUE }
    }
    if (active.isEmpty()) return

    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Down now", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(6.dp)) {
                Text(
                    "${active.size} tools", color = Color(0xFFDC2626), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        active.take(3).forEach { e ->
            val since = parseReportedAt(e.timestamp)
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e) },
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.originalToolId, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (since != null) formatDowntime(now - since) else e.timestamp,
                            color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 13.sp
                        )
                    }
                    Text("${e.line} • ${e.station} — ${e.problem}", fontSize = 12.sp, color = Color(0xFF475569), maxLines = 2)
                    val hasBackup = e.backupToolId.isNotBlank() && e.backupToolId != "No Backup Issued"
                    Text(
                        if (hasBackup) "Backup ${e.backupToolId} in use" else "No backup issued",
                        fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        color = if (hasBackup) Color(0xFF2563EB) else Color(0xFFD97706)
                    )
                }
            }
        }
        if (active.size > 3) {
            OutlinedButton(onClick = onSeeAll, modifier = Modifier.fillMaxWidth()) {
                Text("See all ${active.size} breakdowns")
            }
        }
    }
}
