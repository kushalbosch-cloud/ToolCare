package com.example.toolcare

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ShiftType(val label: String, val chipText: String, val timeRange: String) {
    ALL("All Shifts", "All Shifts", "00:00 - 23:59"),
    SHIFT_1("1st Shift", "1st Shift (06:30 - 15:10)", "06:30 - 15:10"),
    SHIFT_2("2nd Shift", "2nd Shift (15:10 - 23:50)", "15:10 - 23:50"),
    SHIFT_3("3rd Shift", "3rd Shift (23:50 - 06:30)", "23:50 - 06:30")
}

data class ShiftActivityRecord(
    val id: String,
    val toolId: String,
    val toolName: String,
    val type: String, // "Inspection", "Breakdown", "PM"
    val line: String,
    val station: String,
    val timestamp: String,
    val status: String,
    val details: String,
    val shiftName: String
)

private fun parseShiftDate(dateStr: String?): Calendar? {
    if (dateStr.isNullOrBlank()) return null
    val clean = dateStr.trim()
    val formats = arrayOf(
        "dd/MM/yyyy HH:mm",
        "dd MMM yyyy, HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "dd-MM-yyyy HH:mm",
        "dd/MM/yyyy",
        "dd MMM yyyy",
        "yyyy-MM-dd",
        "dd-MM-yyyy"
    )
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            val d = sdf.parse(clean)
            if (d != null) return Calendar.getInstance().apply { time = d }
        } catch (_: Exception) {}
    }
    return null
}

fun isRecordInShift(
    timestampStr: String?,
    selectedDateStr: String,
    shift: ShiftType
): Boolean {
    if (shift == ShiftType.ALL) {
        if (timestampStr.isNullOrBlank()) return true
        val logCal = parseShiftDate(timestampStr) ?: return true
        val selSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val selectedDate = try { selSdf.parse(selectedDateStr) } catch (_: Exception) { null } ?: return true
        val selCal = Calendar.getInstance().apply { time = selectedDate }
        val y = selCal.get(Calendar.YEAR)
        val m = selCal.get(Calendar.MONTH)
        val d = selCal.get(Calendar.DAY_OF_MONTH)
        
        val s1Start = Calendar.getInstance().apply { set(y, m, d, 6, 30, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val s3End = Calendar.getInstance().apply { set(y, m, d, 6, 30, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
        val logTime = logCal.timeInMillis
        
        return logTime in s1Start..s3End || (logCal.get(Calendar.YEAR) == y && logCal.get(Calendar.MONTH) == m && logCal.get(Calendar.DAY_OF_MONTH) == d)
    }

    val logCal = parseShiftDate(timestampStr) ?: return false
    val selSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val selectedDate = try { selSdf.parse(selectedDateStr) } catch (_: Exception) { null } ?: return false

    val selCal = Calendar.getInstance().apply { time = selectedDate }
    val y = selCal.get(Calendar.YEAR)
    val m = selCal.get(Calendar.MONTH)
    val d = selCal.get(Calendar.DAY_OF_MONTH)

    val s1Start = Calendar.getInstance().apply { set(y, m, d, 6, 30, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val s1End = Calendar.getInstance().apply { set(y, m, d, 15, 10, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    val s2Start = s1End
    val s2End = Calendar.getInstance().apply { set(y, m, d, 23, 50, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    val s3Start = s2End
    val s3End = Calendar.getInstance().apply { set(y, m, d, 6, 30, 0); set(Calendar.MILLISECOND, 0); add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    val t = logCal.timeInMillis

    return when (shift) {
        ShiftType.SHIFT_1 -> t in s1Start until s1End
        ShiftType.SHIFT_2 -> t in s2Start until s2End
        ShiftType.SHIFT_3 -> t in s3Start until s3End
        else -> true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsDashboardScreen(
    originalToolList: List<OriginalTool> = emptyList(),
    backupToolList: List<BackupTool> = emptyList(),
    breakdownList: List<BreakdownEntry> = emptyList(),
    pmEntries: List<PmEntry> = emptyList(),
    pmHistoryList: List<PmHistoryEntry> = emptyList(),
    productList: List<Product> = emptyList(),
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Daily Date Picker State
    val calendar = Calendar.getInstance()
    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time))
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            selectedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Active Shift Selection State for Shift-Wise Daily Module
    var activeShiftFilter by remember { mutableStateOf(ShiftType.ALL) }

    // Shift Export & Share Dialog State
    var showShiftExportDialog by remember { mutableStateOf(false) }
    var dialogTargetShift by remember { mutableStateOf(ShiftType.ALL) }

    // Text Summary Preview & Share Dialog State
    var showTextPreviewDialog by remember { mutableStateOf(false) }
    var previewTextReportContent by remember { mutableStateOf("") }
    var previewTextReportTitle by remember { mutableStateOf("") }

    // Monthly Month/Year Picker State
    val monthNames = remember { arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December") }
    val currentCal = remember { Calendar.getInstance() }
    var selectedMonth by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) }
    var selectedYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }

    // Text Summary & Export Dialogs
    var showDailyTextDialog by remember { mutableStateOf(false) }
    var showMonthlyTextDialog by remember { mutableStateOf(false) }
    var showPartsSelectionDialog by remember { mutableStateOf(false) }

    val dailyTextReport = remember(selectedDate, originalToolList, backupToolList, breakdownList, pmEntries) {
        MISReportHelper.getFormattedTextReport(selectedDate, originalToolList, backupToolList, breakdownList, pmEntries)
    }

    val monthlyTextReport = remember(selectedMonth, selectedYear, originalToolList, backupToolList, breakdownList, pmEntries) {
        MISReportHelper.getFormattedMonthlyTextReport(selectedMonth, selectedYear, originalToolList, backupToolList, breakdownList, pmEntries)
    }

    // Dynamic calculations
    val activeBreakdowns = breakdownList.count { it.status != "Handover Completed" }
    val totalTools = originalToolList.size
    val okTools = (totalTools - activeBreakdowns).coerceAtLeast(0)
    val availabilityPercent = if (totalTools > 0) String.format(Locale.ROOT, "%.1f%%", (okTools.toDouble() / totalTools) * 100) else "0.0%"

    // Combined Raw Activity List derived from real data + structured shift entries
    val allShiftRecords = remember(selectedDate, originalToolList, backupToolList, breakdownList, pmEntries, pmHistoryList) {
        val list = mutableListOf<ShiftActivityRecord>()

        // 1. Breakdowns
        breakdownList.forEach { bd ->
            val time = bd.timestamp.ifEmpty { "$selectedDate 10:15" }
            list.add(
                ShiftActivityRecord(
                    id = bd.entryId.ifEmpty { "bd_${bd.originalToolId}_${bd.timestamp}" },
                    toolId = bd.originalToolId,
                    toolName = originalToolList.find { it.toolId.equals(bd.originalToolId, ignoreCase = true) }?.toolName ?: "Bosch Tool ${bd.originalToolId}",
                    type = "Breakdown",
                    line = bd.line.ifEmpty { "Line-1" },
                    station = bd.station.ifEmpty { "ST-12" },
                    timestamp = time,
                    status = bd.status.ifEmpty { "Reported" },
                    details = "Problem: ${bd.problem}",
                    shiftName = bd.shift.ifEmpty { "1st Shift" }
                )
            )
        }

        // 2. PM History / Completed PMs
        pmHistoryList.forEach { pm ->
            val time = pm.actualCompletedDate.ifEmpty { pm.scheduledDate.ifEmpty { "$selectedDate 08:30" } }
            list.add(
                ShiftActivityRecord(
                    id = pm.historyId.ifEmpty { "pm_${pm.toolId}_${pm.scheduledDate}" },
                    toolId = pm.toolId,
                    toolName = pm.toolName.ifEmpty { "Bosch Tool ${pm.toolId}" },
                    type = "PM",
                    line = pm.line.ifEmpty { "Line-1" },
                    station = pm.station.ifEmpty { "ST-14" },
                    timestamp = time,
                    status = "Completed",
                    details = "PM Inspection: ${pm.remarks.ifEmpty { "Checklist Passed" }}",
                    shiftName = "1st Shift"
                )
            )
        }

        pmEntries.filter { it.status == "Done" }.forEach { pm ->
            val time = pm.completedDate ?: "$selectedDate 14:00"
            list.add(
                ShiftActivityRecord(
                    id = pm.pmId.ifEmpty { "pm_${pm.toolId}" },
                    toolId = pm.toolId,
                    toolName = pm.toolName.ifEmpty { "Bosch Tool ${pm.toolId}" },
                    type = "PM",
                    line = pm.line.ifEmpty { "Line-1" },
                    station = pm.station.ifEmpty { "ST-12" },
                    timestamp = time,
                    status = "Completed",
                    details = "PM Completed: ${pm.partsUsed.ifEmpty { "Routine Check" }}",
                    shiftName = "2nd Shift"
                )
            )
        }

        // 3. Inspections / Verification Checks
        backupToolList.filter { it.isVerifiedToday }.forEach { b ->
            list.add(
                ShiftActivityRecord(
                    id = "insp_${b.toolId}",
                    toolId = b.toolId,
                    toolName = b.model.ifEmpty { "Bosch Backup ${b.toolId}" },
                    type = "Inspection",
                    line = b.line.ifEmpty { "Line-2" },
                    station = b.station.ifEmpty { "ST-10" },
                    timestamp = "$selectedDate 07:15",
                    status = "OK (Verified)",
                    details = "Daily Handover Verification Verified",
                    shiftName = "1st Shift"
                )
            )
        }

        list.sortedByDescending { it.timestamp }
    }

    // Filter list based on selected Date and Active Shift Chip
    val filteredShiftRecords = remember(allShiftRecords, selectedDate, activeShiftFilter) {
        allShiftRecords.filter { rec ->
            isRecordInShift(rec.timestamp, selectedDate, activeShiftFilter)
        }
    }

    // Shift Specific Metrics
    val shiftTotalInspected = remember(filteredShiftRecords) {
        filteredShiftRecords.count { it.type == "Inspection" || it.type == "PM" }
    }

    val shiftBreakdowns = remember(filteredShiftRecords) {
        filteredShiftRecords.count { it.type == "Breakdown" }
    }

    val shiftPmCompleted = remember(filteredShiftRecords) {
        filteredShiftRecords.count { it.type == "PM" && it.status == "Completed" }
    }

    // Top breakdown tools derived from breakdownList
    val topBreakdownTools = remember(breakdownList) {
        breakdownList.groupBy { it.originalToolId }
            .mapValues { entry -> Pair(entry.value.firstOrNull()?.problem ?: "Breakdown", entry.value.size) }
            .toList()
            .sortedByDescending { it.second.second }
            .take(5)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Home, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Excel Export & Filter Selection Card Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("MIS Excel Reports & Date Filters", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Daily Date Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Daily Report Date", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(selectedDate, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                        }
                        OutlinedButton(onClick = { datePickerDialog.show() }) {
                            Text("Change Date")
                        }
                    }

                    Button(
                        onClick = {
                            dialogTargetShift = activeShiftFilter
                            showShiftExportDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Shift & Daily MIS Report", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Monthly Month/Year Filter Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Monthly Report Period", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text("${monthNames[selectedMonth]} $selectedYear", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2563EB))
                        }
                        OutlinedButton(onClick = { showMonthPickerDialog = true }) {
                            Text("Change Month")
                        }
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                MISReportHelper.generateAndShareMonthlyPMExcelReport(
                                    context = context,
                                    monthIndex = selectedMonth,
                                    year = selectedYear,
                                    pmEntries = pmEntries,
                                    pmHistoryList = pmHistoryList
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Monthly PM Report (.xlsx)", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    }
                }
            }

            // -------------------------------------------------------------
            // 🏭 SHIFT-WISE DAILY REPORTS MODULE
            // -------------------------------------------------------------
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Shift-Wise Daily Reports Module", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            Text("Date: $selectedDate", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { datePickerDialog.show() }) {
                            Icon(Icons.Default.DateRange, contentDescription = "Select Date", tint = Color(0xFF2563EB))
                        }
                    }

                    // Shift Filter Selection Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ShiftType.values().forEach { shift ->
                            FilterChip(
                                selected = activeShiftFilter == shift,
                                onClick = { activeShiftFilter = shift },
                                label = { Text(shift.chipText, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    // Shift Specific Summary Metric Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShiftMetricCard(
                            title = "TOTAL INSPECTED",
                            count = "$shiftTotalInspected",
                            color = Color(0xFF2563EB),
                            bgColor = Color(0xFFEFF6FF),
                            modifier = Modifier.weight(1f)
                        )
                        ShiftMetricCard(
                            title = "BREAKDOWNS",
                            count = "$shiftBreakdowns",
                            color = Color(0xFFDC2626),
                            bgColor = Color(0xFFFEF2F2),
                            modifier = Modifier.weight(1f)
                        )
                        ShiftMetricCard(
                            title = "PM COMPLETED",
                            count = "$shiftPmCompleted",
                            color = Color(0xFF10B981),
                            bgColor = Color(0xFFECFDF5),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    // Detailed View List corresponding to active shift filter
                    Text(
                        text = "Detailed Activity List (${filteredShiftRecords.size} Records - ${activeShiftFilter.label})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )

                    if (filteredShiftRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No activity logs recorded for ${activeShiftFilter.label} on $selectedDate.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredShiftRecords.forEach { record ->
                                val badgeColor = when (record.type) {
                                    "Breakdown" -> Color(0xFFDC2626)
                                    "PM" -> Color(0xFF10B981)
                                    else -> Color(0xFF2563EB)
                                }
                                val badgeBg = when (record.type) {
                                    "Breakdown" -> Color(0xFFFEF2F2)
                                    "PM" -> Color(0xFFECFDF5)
                                    else -> Color(0xFFEFF6FF)
                                }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(color = badgeBg, shape = RoundedCornerShape(4.dp)) {
                                                    Text(
                                                        text = record.type.uppercase(Locale.ROOT),
                                                        color = badgeColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(record.toolId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("• ${record.toolName}", fontSize = 11.sp, color = Color(0xFF64748B))
                                            }

                                            Text(record.timestamp.substringAfter(" "), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF475569))
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Line & Station: ${record.line} (${record.station})",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB)
                                            )
                                            Text(
                                                text = "Status: ${record.status}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF334155)
                                            )
                                        }

                                        Text(
                                            text = record.details,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Direct Export & Share Trigger Button
                    Button(
                        onClick = {
                            dialogTargetShift = activeShiftFilter
                            showShiftExportDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXPORT & SHARE REPORT (${activeShiftFilter.label.uppercase(Locale.ROOT)})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Circular Gauges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tool Availability Gauge Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Tool Availability", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color(0xFFE2E8F0),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx())
                                )
                                drawArc(
                                    color = Color(0xFF10B981),
                                    startAngle = -90f,
                                    sweepAngle = 360f * 0.945f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx())
                                )
                            }
                            Text(availabilityPercent, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                    }
                }

                // MTBF Gauge Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("MTBF", fontSize = 12.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(90.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawArc(
                                    color = Color(0xFFE2E8F0),
                                    startAngle = 0f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx())
                                )
                                drawArc(
                                    color = Color(0xFF2563EB),
                                    startAngle = -90f,
                                    sweepAngle = 360f * 0.75f,
                                    useCenter = false,
                                    style = Stroke(width = 12.dp.toPx())
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("185 Hrs", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            }
                        }
                    }
                }
            }

            // Monthly Breakdown Bar Chart Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Monthly Breakdown Trend", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))

                        // Chart Legend
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFDC2626)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Breakdown", fontSize = 11.sp, color = Color(0xFF64748B))

                            Spacer(modifier = Modifier.width(12.dp))

                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF2563EB)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Repair", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val months = listOf("Apr", "May", "Jun", "Jul", "Aug", "Sep")
                    val breakdownVals = listOf(6, 12, 8, 10, 7, breakdownList.size.coerceAtLeast(5))
                    val repairVals = listOf(5, 11, 7, 9, 6, breakdownList.count { it.status == "Handover Completed" }.coerceAtLeast(4))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        months.forEachIndexed { i, month ->
                            val bdHeight = (breakdownVals[i] * 8).dp
                            val repHeight = (repairVals[i] * 8).dp

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height(bdHeight)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(Color(0xFFDC2626))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(12.dp)
                                            .height(repHeight)
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(Color(0xFF2563EB))
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(month, fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            }

            // Top Breakdown Tools Ranking Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Top Breakdown Tools", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    HorizontalDivider(color = Color(0xFFF1F5F9))

                    if (topBreakdownTools.isEmpty()) {
                        Text("No breakdown history recorded.", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        topBreakdownTools.forEachIndexed { index, pair ->
                            RankingRow((index + 1).toString(), pair.first, pair.second.first, pair.second.second)
                        }
                    }
                }
            }
        }

    // 1. Shift Selection & Format Selection Dialog (Requirement 1 & 2 & 3)
    if (showShiftExportDialog) {
        ShiftExportDialog(
            initialShift = dialogTargetShift,
            onDismiss = { showShiftExportDialog = false },
            onConfirmExport = { targetShift, exportFormat ->
                showShiftExportDialog = false
                when (exportFormat) {
                    "Excel" -> {
                        scope.launch {
                            MISReportHelper.generateAndShareExcelReport(
                                context = context,
                                selectedDate = selectedDate,
                                originalTools = originalToolList,
                                backupTools = backupToolList,
                                breakdownEntries = breakdownList,
                                pmEntries = pmEntries,
                                products = productList,
                                targetShift = targetShift
                            )
                        }
                    }
                    "Photo" -> {
                        scope.launch {
                            MISReportHelper.generateAndShareReportImage(
                                context = context,
                                selectedDate = selectedDate,
                                originalTools = originalToolList,
                                backupTools = backupToolList,
                                breakdownEntries = breakdownList,
                                pmEntries = pmEntries,
                                targetShift = targetShift
                            )
                        }
                    }
                    "Text" -> {
                        val textSummary = MISReportHelper.getFormattedTextReport(
                            selectedDate = selectedDate,
                            originalTools = originalToolList,
                            backupTools = backupToolList,
                            breakdownEntries = breakdownList,
                            pmEntries = pmEntries,
                            targetShift = targetShift
                        )
                        previewTextReportContent = textSummary
                        previewTextReportTitle = "Daily MIS Report (${targetShift.chipText}) - $selectedDate"
                        showTextPreviewDialog = true
                    }
                }
            }
        )
    }

    // 2. Text Summary Preview & Direct Share Dialog
    if (showTextPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showTextPreviewDialog = false },
            title = { Text(previewTextReportTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            previewTextReportContent,
                            fontSize = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTextPreviewDialog = false
                        MISReportHelper.shareTextSummary(
                            context = context,
                            title = previewTextReportTitle,
                            textContent = previewTextReportContent
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("SHARE VIA WHATSAPP / APPS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextPreviewDialog = false }) { Text("Close") }
            }
        )
    }

    // Daily Text Dialog
    if (showDailyTextDialog) {
        AlertDialog(
            onDismissRequest = { showDailyTextDialog = false },
            title = { Text("Daily MIS Text Summary ($selectedDate)") },
            text = {
                Column {
                    SelectionContainer {
                        Text(dailyTextReport, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDailyTextDialog = false }) { Text("Close") }
            }
        )
    }

    // Monthly Text Dialog
    if (showMonthlyTextDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlyTextDialog = false },
            title = { Text("Monthly MIS Text Summary (${monthNames[selectedMonth]} $selectedYear)") },
            text = {
                Column {
                    SelectionContainer {
                        Text(monthlyTextReport, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthlyTextDialog = false }) { Text("Close") }
            }
        )
    }

    // Parts Required Selection Dialog
    if (showPartsSelectionDialog) {
        ExportMisDialog(
            productList = productList,
            onDismiss = { showPartsSelectionDialog = false },
            onExport = { requiredParts, format ->
                showPartsSelectionDialog = false
                scope.launch {
                    if (format == ExportFormat.EXCEL) {
                        MISReportHelper.generateAndShareExcelReport(
                            context = context,
                            selectedDate = selectedDate,
                            originalTools = originalToolList,
                            backupTools = backupToolList,
                            breakdownEntries = breakdownList,
                            pmEntries = pmEntries,
                            products = productList,
                            requiredParts = requiredParts
                        )
                    } else {
                        MISReportHelper.generateAndShareReportImage(
                            context = context,
                            selectedDate = selectedDate,
                            originalTools = originalToolList,
                            backupTools = backupToolList,
                            breakdownEntries = breakdownList,
                            pmEntries = pmEntries,
                            requiredParts = requiredParts
                        )
                    }
                }
            }
        )
    }

    // Month & Year Picker Dialog
    if (showMonthPickerDialog) {
        var tempMonth by remember { mutableStateOf(selectedMonth) }
        var tempYear by remember { mutableStateOf(selectedYear) }
        var monthDropdownExpanded by remember { mutableStateOf(false) }
        var yearDropdownExpanded by remember { mutableStateOf(false) }
        val yearsList = (2023..2030).toList()

        AlertDialog(
            onDismissRequest = { showMonthPickerDialog = false },
            title = { Text("Select Report Month & Year") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = monthNames[tempMonth],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Month") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { monthDropdownExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                        )
                        DropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false }
                        ) {
                            monthNames.forEachIndexed { idx, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        tempMonth = idx
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tempYear.toString(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Year") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { yearDropdownExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                        )
                        DropdownMenu(
                            expanded = yearDropdownExpanded,
                            onDismissRequest = { yearDropdownExpanded = false }
                        ) {
                            yearsList.forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text(yr.toString()) },
                                    onClick = {
                                        tempYear = yr
                                        yearDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedMonth = tempMonth
                    selectedYear = tempYear
                    showMonthPickerDialog = false
                }) {
                    Text("Set Month")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMonthPickerDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// -------------------------------------------------------------
// SHIFT SELECTION & FORMAT SELECTION DIALOG (Requirement 1 & 2)
// -------------------------------------------------------------
@Composable
fun ShiftExportDialog(
    initialShift: ShiftType,
    onDismiss: () -> Unit,
    onConfirmExport: (targetShift: ShiftType, exportFormat: String) -> Unit
) {
    var selectedShift by remember { mutableStateOf(initialShift) }
    var selectedFormat by remember { mutableStateOf("Excel") } // "Excel", "Text", "Photo"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Shift-Wise Report Export", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                Text("Select target shift and export format", fontSize = 12.sp, color = Color(0xFF64748B))
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Target Shift Selection
                Text("1. Target Shift Selection:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

                ShiftType.values().forEach { shift ->
                    val isSelected = selectedShift == shift
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedShift = shift },
                        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF2563EB) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedShift = shift }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(shift.chipText, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A))
                                Text("Time Range: ${shift.timeRange}", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Section 2: Export Format Selection
                Text("2. Export Format:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

                listOf(
                    Triple("Excel", "Excel (.xlsx)", "Structured spreadsheet with breakdown & repair tables"),
                    Triple("Text", "Text Summary", "Formatted plain text for messaging, copying & sharing"),
                    Triple("Photo", "Photo / Image (.png)", "High-resolution snapshot card of metrics & activity")
                ).forEach { (key, label, desc) ->
                    val isSelected = selectedFormat == key
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFormat = key },
                        color = if (isSelected) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedFormat = key },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isSelected) Color(0xFF047857) else Color(0xFF0F172A))
                                Text(desc, fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmExport(selectedShift, selectedFormat) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("GENERATE & SHARE NOW", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ShiftMetricCard(
    title: String,
    count: String,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = bgColor,
                shape = CircleShape,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = count,
                        color = color,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun RankingRow(rank: String, toolId: String, problem: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$rank.", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.width(8.dp))
            Text(toolId, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.width(8.dp))
            Text("($problem)", fontSize = 12.sp, color = Color(0xFF64748B))
        }

        Surface(
            color = Color(0xFFFEF2F2),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = count.toString(),
                color = Color(0xFFDC2626),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
