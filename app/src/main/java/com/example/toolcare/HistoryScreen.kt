package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

sealed class HistoryLogItem {
    abstract val key: String
    abstract val timestampStr: String
    abstract val dateObj: Date?

    data class Breakdown(val entry: BreakdownEntry, override val dateObj: Date?) : HistoryLogItem() {
        override val key: String get() = if (entry.entryId.isNotEmpty()) entry.entryId else "bd_${entry.originalToolId}_${entry.timestamp}_${entry.hashCode()}"
        override val timestampStr: String get() = entry.timestamp
    }

    data class Pm(val entry: PmEntry, override val dateObj: Date?) : HistoryLogItem() {
        override val key: String get() = if (entry.pmId.isNotEmpty()) entry.pmId else "pm_${entry.toolId}_${entry.completedDate}_${entry.hashCode()}"
        override val timestampStr: String get() = if (entry.completedDate.isNotEmpty()) entry.completedDate else entry.scheduledDate
    }

    data class Calibration(val entry: CalibrationEntry, override val dateObj: Date?) : HistoryLogItem() {
        override val key: String get() = if (entry.id.isNotEmpty()) entry.id else "cal_${entry.toolId}_${entry.calibrationDate}_${entry.hashCode()}"
        override val timestampStr: String get() = entry.calibrationDate
    }
}

data class HistoryMonthGroup(
    val monthKey: Int,
    val monthYearLabel: String,
    val isCurrentMonth: Boolean,
    val items: List<HistoryLogItem>
)

private fun parseHistoryDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    val clean = dateStr.trim()
    
    val asLong = clean.toLongOrNull()
    if (asLong != null && asLong > 1_000_000_000_000L) {
        return Date(asLong)
    }

    val formats = arrayOf(
        "yyyy-MM-dd HH:mm",
        "dd/MM/yyyy HH:mm",
        "yyyy-MM-dd",
        "dd/MM/yyyy",
        "dd-MM-yyyy",
        "dd MMM yyyy, HH:mm",
        "dd MMM yyyy",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            sdf.isLenient = false
            return sdf.parse(clean)
        } catch (_: Exception) {}
    }
    return null
}

private fun matchesHistorySearch(item: HistoryLogItem, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return when (item) {
        is HistoryLogItem.Breakdown -> {
            item.entry.originalToolId.contains(q, ignoreCase = true) ||
            item.entry.backupToolId.contains(q, ignoreCase = true) ||
            item.entry.problem.contains(q, ignoreCase = true) ||
            item.entry.repairDetails.contains(q, ignoreCase = true) ||
            item.entry.line.contains(q, ignoreCase = true) ||
            item.entry.station.contains(q, ignoreCase = true) ||
            item.entry.reportedBy.contains(q, ignoreCase = true) ||
            item.entry.handoverPerson.contains(q, ignoreCase = true)
        }
        is HistoryLogItem.Pm -> {
            item.entry.toolId.contains(q, ignoreCase = true) ||
            item.entry.toolName.contains(q, ignoreCase = true) ||
            item.entry.line.contains(q, ignoreCase = true) ||
            item.entry.station.contains(q, ignoreCase = true) ||
            item.entry.partsUsed.contains(q, ignoreCase = true)
        }
        is HistoryLogItem.Calibration -> {
            item.entry.toolId.contains(q, ignoreCase = true) ||
            item.entry.testerId.contains(q, ignoreCase = true) ||
            item.entry.status.contains(q, ignoreCase = true)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    tool: OriginalTool? = null,
    breakdownList: List<BreakdownEntry>,
    pmEntries: List<PmEntry> = emptyList(),
    calibrationList: List<CalibrationEntry> = emptyList(),
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val monthNames = remember { arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December") }

    val currentCal = remember { Calendar.getInstance() }
    val currentYear = currentCal.get(Calendar.YEAR)
    val currentMonthIndex = currentCal.get(Calendar.MONTH)
    val currentMonthKey = currentYear * 100 + (currentMonthIndex + 1)

    var selectedItemForDetail by remember { mutableStateOf<HistoryLogItem?>(null) }

    // Filter lists for specific tool context if tool != null
    val targetBreakdowns = remember(breakdownList, tool) {
        if (tool != null && tool.toolId.isNotBlank()) breakdownList.filter { it.originalToolId.equals(tool.toolId, ignoreCase = true) }
        else breakdownList
    }

    val targetPms = remember(pmEntries, tool) {
        if (tool != null && tool.toolId.isNotBlank()) pmEntries.filter { it.toolId.equals(tool.toolId, ignoreCase = true) }
        else pmEntries
    }

    val targetCals = remember(calibrationList, tool) {
        if (tool != null && tool.toolId.isNotBlank()) calibrationList.filter { it.toolId.equals(tool.toolId, ignoreCase = true) }
        else calibrationList
    }

    // Grouping & Sorting Logic
    val monthGroups = remember(targetBreakdowns, targetPms, targetCals) {
        val allItems = mutableListOf<HistoryLogItem>()

        targetBreakdowns.forEach { entry ->
            val d = parseHistoryDate(entry.timestamp)
            allItems.add(HistoryLogItem.Breakdown(entry, d))
        }

        targetPms.filter { it.status == "Done" || it.completedDate.isNotEmpty() }.forEach { entry ->
            val dateStr = if (entry.completedDate.isNotEmpty()) entry.completedDate else entry.scheduledDate
            val d = parseHistoryDate(dateStr)
            allItems.add(HistoryLogItem.Pm(entry, d))
        }

        targetCals.forEach { entry ->
            val d = parseHistoryDate(entry.calibrationDate)
            allItems.add(HistoryLogItem.Calibration(entry, d))
        }

        val groupedMap = mutableMapOf<Int, MutableList<HistoryLogItem>>()
        val labelMap = mutableMapOf<Int, String>()

        allItems.forEach { item ->
            val cal = Calendar.getInstance()
            if (item.dateObj != null) {
                cal.time = item.dateObj!!
            }
            val y = if (item.dateObj != null) cal.get(Calendar.YEAR) else currentYear
            val m = if (item.dateObj != null) cal.get(Calendar.MONTH) else currentMonthIndex
            val key = y * 100 + (m + 1)

            val list = groupedMap.getOrPut(key) { mutableListOf() }
            list.add(item)
            labelMap[key] = "${monthNames[m]} $y"
        }

        if (groupedMap.isEmpty()) {
            groupedMap[currentMonthKey] = mutableListOf()
            labelMap[currentMonthKey] = "${monthNames[currentMonthIndex]} $currentYear"
        }

        groupedMap.map { (key, items) ->
            val sortedItems = items.sortedByDescending { it.dateObj?.time ?: 0L }
            HistoryMonthGroup(
                monthKey = key,
                monthYearLabel = labelMap[key] ?: "${monthNames[currentMonthIndex]} $currentYear",
                isCurrentMonth = (key == currentMonthKey),
                items = sortedItems
            )
        }.sortedByDescending { it.monthKey }
    }

    var searchQuery by remember { mutableStateOf("") }

    val filteredMonthGroups = remember(monthGroups, searchQuery) {
        if (searchQuery.isBlank()) monthGroups
        else {
            monthGroups.mapNotNull { group ->
                val matchedItems = group.items.filter { matchesHistorySearch(it, searchQuery) }
                if (matchedItems.isNotEmpty()) {
                    group.copy(items = matchedItems)
                } else null
            }
        }
    }

    val totalEntriesCount = remember(filteredMonthGroups) { filteredMonthGroups.sumOf { it.items.size } }
    val expandedStates = remember { mutableStateMapOf<Int, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_log_title)) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text(stringResource(R.string.back_button), fontSize = 16.sp)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.total_log_entries_count, totalEntriesCount),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by Tool ID, problem, station, person...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            if (filteredMonthGroups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.no_history_logs), color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredMonthGroups, key = { it.monthKey }) { group ->
                        val isExpanded = expandedStates[group.monthKey] ?: (group.isCurrentMonth || searchQuery.isNotBlank())

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (group.isCurrentMonth) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedStates[group.monthKey] = !isExpanded }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = group.monthYearLabel,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (group.isCurrentMonth) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = MaterialTheme.shapes.extraSmall
                                            ) {
                                                Text(
                                                    text = "CURRENT",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Text(
                                                text = "${group.items.size} Entries",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        if (group.items.isEmpty()) {
                                            Text(
                                                text = "No history records for this month.",
                                                fontSize = 13.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        } else {
                                            group.items.forEach { item ->
                                                when (item) {
                                                    is HistoryLogItem.Breakdown -> HistoryBreakdownCard(entry = item.entry, onClick = { selectedItemForDetail = item })
                                                    is HistoryLogItem.Pm -> HistoryPmCard(entry = item.entry, onClick = { selectedItemForDetail = item })
                                                    is HistoryLogItem.Calibration -> HistoryCalibrationCard(entry = item.entry, onClick = { selectedItemForDetail = item })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedItemForDetail?.let { item ->
        HistoryDetailDialog(item = item, onDismiss = { selectedItemForDetail = null })
    }
}

@Composable
fun HistoryBreakdownCard(entry: BreakdownEntry, onClick: () -> Unit) {
    val isCompleted = entry.status == "Handover Completed"
    val statusColor = if (isCompleted) Color(0xFF10B981) else Color(0xFFDC2626)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Original Tool: ${entry.originalToolId}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = entry.status.uppercase(Locale.ROOT),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Problem: ${entry.problem}",
                fontSize = 13.sp,
                color = Color.Gray
            )

            if (entry.backupToolId.isNotEmpty()) {
                Text(
                    text = "Backup Tool: ${entry.backupToolId}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Date: ${entry.timestamp} (Tap to view details)",
                fontSize = 11.sp,
                color = Color(0xFF2563EB)
            )
        }
    }
}

@Composable
fun HistoryPmCard(entry: PmEntry, onClick: () -> Unit) {
    val isDone = entry.status == "Done"
    val statusColor = if (isDone) Color(0xFF10B981) else Color(0xFFD97706)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PM Task: ${entry.toolName} (${entry.toolId})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = entry.status.uppercase(Locale.ROOT),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Station: ${entry.station} (${entry.line})",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Text(
                text = "Parts Used: ${entry.partsUsed}",
                fontSize = 13.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(4.dp))

            val dateDisp = if (entry.completedDate.isNotEmpty()) entry.completedDate else entry.scheduledDate
            Text(
                text = "Date: $dateDisp (Tap to view details)",
                fontSize = 11.sp,
                color = Color(0xFF2563EB)
            )
        }
    }
}

@Composable
fun HistoryCalibrationCard(entry: CalibrationEntry, onClick: () -> Unit) {
    val isPass = entry.status.equals("PASS", ignoreCase = true)
    val statusColor = if (isPass) Color(0xFF10B981) else Color(0xFFEF4444)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Calibration: ${entry.toolId}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = entry.status.uppercase(Locale.ROOT),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Target Torque: ${String.format(Locale.ROOT, "%.2f", entry.targetTorque)} Nm | Actual: ${String.format(Locale.ROOT, "%.2f", entry.actualTorque)} Nm",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF334155)
            )

            Text(
                text = "Date: ${entry.calibrationDate} (Tap to view details)",
                fontSize = 11.sp,
                color = Color(0xFF2563EB)
            )
        }
    }
}

@Composable
fun HistoryDetailDialog(item: HistoryLogItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = when (item) {
                is HistoryLogItem.Breakdown -> "Breakdown Incident Log"
                is HistoryLogItem.Pm -> "Preventive Maintenance Log"
                is HistoryLogItem.Calibration -> "Torque Calibration Log"
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (item) {
                    is HistoryLogItem.Breakdown -> {
                        val bd = item.entry
                        DetailRow("Original Tool ID", bd.originalToolId)
                        DetailRow("Assigned Backup Tool", bd.backupToolId.ifEmpty { "None Issued" })
                        DetailRow("Location", "Station ${bd.station} (${bd.line})")
                        DetailRow("Problem Description", bd.problem)
                        DetailRow("Current Status", bd.status)
                        if (bd.repairDetails.isNotBlank()) DetailRow("Repair Action Taken", bd.repairDetails)
                        if (bd.handoverPerson.isNotBlank()) DetailRow("Handover To", "${bd.handoverPerson} (${bd.shift})")
                        DetailRow("Log Timestamp", bd.timestamp)
                        if (bd.company.isNotBlank()) DetailRow("Facility", "${bd.company} - ${bd.block}")
                    }
                    is HistoryLogItem.Pm -> {
                        val pm = item.entry
                        DetailRow("PM Task ID", pm.pmId)
                        DetailRow("Tool ID & Name", "${pm.toolId} - ${pm.toolName}")
                        DetailRow("Location", "Station ${pm.station} (${pm.line})")
                        DetailRow("Scheduled Date", pm.scheduledDate)
                        DetailRow("Completion Date", pm.completedDate.ifEmpty { "Pending" })
                        DetailRow("Status", pm.status)
                        DetailRow("Spare Parts Used / Remarks", pm.partsUsed.ifEmpty { "None" })
                        if (pm.company.isNotBlank()) DetailRow("Facility", "${pm.company} - ${pm.block}")
                    }
                    is HistoryLogItem.Calibration -> {
                        val cal = item.entry
                        DetailRow("Calibration Log ID", cal.id)
                        DetailRow("Tool ID", cal.toolId)
                        DetailRow("Target Torque", "${String.format(Locale.ROOT, "%.2f", cal.targetTorque)} Nm")
                        DetailRow("Actual Torque Measured", "${String.format(Locale.ROOT, "%.2f", cal.actualTorque)} Nm")
                        DetailRow("Error Percentage", "${String.format(Locale.ROOT, "%.2f", cal.errorPercent)} %")
                        DetailRow("Final Result Status", cal.status.uppercase(Locale.ROOT))
                        DetailRow("Certified Tester ID", cal.testerId)
                        DetailRow("Calibration Date", cal.calibrationDate)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close Details")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}
