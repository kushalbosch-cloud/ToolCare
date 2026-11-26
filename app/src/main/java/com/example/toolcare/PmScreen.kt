package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private fun parsePmDate(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    val formats = arrayOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            sdf.isLenient = false
            return sdf.parse(dateStr.trim())
        } catch (_: Exception) { }
    }
    return null
}

private fun calculateNextPmDate(actualCompletedDateStr: String, pmCycleMonths: Int = 6): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calNext = Calendar.getInstance()
    calNext.time = parsePmDate(actualCompletedDateStr) ?: Date()
    calNext.add(Calendar.MONTH, if (pmCycleMonths > 0) pmCycleMonths else 6)
    return dateFormat.format(calNext.time)
}

private fun isPmCompleted(status: String?): Boolean {
    if (status.isNullOrBlank()) return false
    val s = status.trim().lowercase(Locale.ROOT)
    return s == "done" || s == "completed" || s == "pass"
}

private fun derivePmStatus(scheduledDateStr: String, status: String, todayDate: Date): String {
    if (isPmCompleted(status)) return "DONE"
    if (status.uppercase(Locale.ROOT) == "IN_PROGRESS") return "IN_PROGRESS"

    val scheduled = parsePmDate(scheduledDateStr) ?: return "PENDING"

    val scheduledCal = Calendar.getInstance().apply {
        time = scheduled
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val todayCal = Calendar.getInstance().apply {
        time = todayDate
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    return when {
        scheduledCal.before(todayCal) -> "OVERDUE"
        scheduledCal.equals(todayCal) -> "TODAY"
        else -> "PENDING"
    }
}

data class UsedPartItem(
    val partNo: String,
    val partName: String,
    var quantity: Int,
    val isManual: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PmScreen(
    firebaseManager: FirebaseManager,
    originalToolList: List<OriginalTool>,
    breakdownList: List<BreakdownEntry>,
    productList: List<Product> = emptyList(),
    userRole: String = "technician",
    userName: String = "Technician",
    company: String,
    block: String,
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pmEntries by remember(company, block) { firebaseManager.getPmEntries(company, block) }.collectAsState(initial = emptyList())
    val pmHistoryList by remember(company, block) { firebaseManager.getPmHistory(company, block) }.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf("TODAY") }

    var showInspectionWorkflow by remember { mutableStateOf(false) }
    var currentPmEntryForInspection by remember { mutableStateOf<PmEntry?>(null) }
    var currentWorkflowStep by remember { mutableStateOf(1) }
    var showCompleteConfirmationDialog by remember { mutableStateOf(false) }

    var showHistoryDialog by remember { mutableStateOf(false) }
    var selectedToolIdForHistory by remember { mutableStateOf("") }

    var selectedCompletedPmForDetails by remember { mutableStateOf<PmHistoryEntry?>(null) }

    var showAdminEditDialog by remember { mutableStateOf(false) }
    var adminEditTool by remember { mutableStateOf<OriginalTool?>(null) }

    val monthNames = remember { arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December") }
    val currentCal = remember { Calendar.getInstance() }
    var exportMonth by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) }
    var exportYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }
    var showExportMonthDialog by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val currentDateStr = dateFormat.format(Date())

    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val todayDate = todayCal.time

    LaunchedEffect(originalToolList, pmEntries) {
        if (originalToolList.isNotEmpty() && pmEntries.isNotEmpty()) {
            val sortedTools = originalToolList.filter {
                it.company.equals(company, ignoreCase = true) && it.block.equals(block, ignoreCase = true)
            }.sortedBy { it.toolId }

            sortedTools.forEachIndexed { index, tool ->
                val hasActiveSchedule = pmEntries.any { it.toolId.equals(tool.toolId, ignoreCase = true) }
                if (!hasActiveSchedule && tool.toolId.isNotBlank()) {
                    val targetScheduledDate = if (tool.nextPmDate.isNotBlank()) {
                        tool.nextPmDate
                    } else {
                        val daysToAdd = index / 3
                        val scheduleCalendar = Calendar.getInstance()
                        scheduleCalendar.add(Calendar.DAY_OF_YEAR, daysToAdd)
                        dateFormat.format(scheduleCalendar.time)
                    }

                    val pmId = "PM_${tool.toolId}"
                    val pmEntry = PmEntry(
                        pmId = pmId,
                        toolId = tool.toolId,
                        toolName = tool.toolName,
                        line = tool.line,
                        station = tool.station,
                        scheduledDate = targetScheduledDate,
                        status = "Pending",
                        pmCycleMonths = if (tool.pmCycleMonths > 0) tool.pmCycleMonths else 6,
                        company = company,
                        block = block
                    )
                    firebaseManager.savePmEntry(pmEntry)
                }
            }
        }
    }

    val locallyCompletedToolIds = remember { mutableStateListOf<String>() }

    val activePmListWithDerivedStatus = remember(pmEntries, originalToolList, todayDate, locallyCompletedToolIds.toList()) {
        pmEntries.map { entry ->
            val matchingTool = originalToolList.find { it.toolId.equals(entry.toolId, ignoreCase = true) }
            val effectiveScheduledDate = if (entry.scheduledDate.isNotBlank()) {
                entry.scheduledDate
            } else {
                matchingTool?.nextPmDate?.ifEmpty { dateFormat.format(todayDate) } ?: dateFormat.format(todayDate)
            }
            val isLocallyDone = locallyCompletedToolIds.contains(entry.toolId)
            val effectiveStatus = if (isLocallyDone) "DONE" else entry.status
            val derived = derivePmStatus(effectiveScheduledDate, effectiveStatus, todayDate)
            entry.copy(scheduledDate = effectiveScheduledDate, status = derived)
        }
    }

    val todaySchedules = activePmListWithDerivedStatus.filter {
        (it.status == "TODAY" || it.status == "IN_PROGRESS") &&
        !locallyCompletedToolIds.contains(it.toolId) &&
        !isPmCompleted(it.status)
    }
    val pendingSchedules = activePmListWithDerivedStatus.filter { it.status == "PENDING" && !locallyCompletedToolIds.contains(it.toolId) }
    val overdueSchedules = activePmListWithDerivedStatus.filter { it.status == "OVERDUE" && !locallyCompletedToolIds.contains(it.toolId) }

    val todayCount = todaySchedules.size
    val pendingCount = pendingSchedules.size
    val overdueCount = overdueSchedules.size

    val completedTodayCount = pmHistoryList.count { hist ->
        hist.actualCompletedDate == currentDateStr ||
        hist.completedDate.contains(currentDateStr) ||
        hist.completedDate.startsWith(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()))
    }

    val doneTotalCount = pmHistoryList.size
    val totalTodayProgress = todayCount + completedTodayCount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PM Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (selectedFilterTab == "DONE") {
                        IconButton(onClick = { showExportMonthDialog = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Export Excel", tint = Color.White)
                        }
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "PM Schedule Refreshed", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PmDashboardCard("TODAY'S PM", "$todayCount", Icons.Default.CheckCircle, Color(0xFF2563EB), Color(0xFFEFF6FF), Modifier.weight(1f)) {
                    selectedFilterTab = "TODAY"
                }
                PmDashboardCard("PENDING", "$pendingCount", Icons.Default.Warning, Color(0xFFD97706), Color(0xFFFFFBEB), Modifier.weight(1f)) {
                    selectedFilterTab = "PENDING"
                }
                PmDashboardCard("OVERDUE", "$overdueCount", Icons.Default.Clear, Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.weight(1f)) {
                    selectedFilterTab = "OVERDUE"
                }
                PmDashboardCard("DONE", "$doneTotalCount", Icons.Default.CheckCircle, Color(0xFF10B981), Color(0xFFECFDF5), Modifier.weight(1f)) {
                    selectedFilterTab = "DONE"
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Tool ID, Serial No., Model, Station...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilterTab == "ALL",
                    onClick = { selectedFilterTab = "ALL" },
                    label = { Text("ALL (${originalToolList.size.let { if (it > 0) it else activePmListWithDerivedStatus.size }})", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedFilterTab == "TODAY",
                    onClick = { selectedFilterTab = "TODAY" },
                    label = { Text("TODAY ($todayCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedFilterTab == "PENDING",
                    onClick = { selectedFilterTab = "PENDING" },
                    label = { Text("PENDING ($pendingCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedFilterTab == "OVERDUE",
                    onClick = { selectedFilterTab = "OVERDUE" },
                    label = { Text("OVERDUE ($overdueCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedFilterTab == "DONE",
                    onClick = { selectedFilterTab = "DONE" },
                    label = { Text("DONE ($doneTotalCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedFilterTab == "TODAY") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("TODAY'S PM PROGRESS", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF2563EB))
                            Text(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()), fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                        Surface(color = Color(0xFF2563EB), shape = RoundedCornerShape(12.dp)) {
                            Text("Progress: $completedTodayCount / $totalTodayProgress Completed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            if (selectedFilterTab == "DONE") {
                Button(
                    onClick = { showExportMonthDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EXPORT MONTHLY PM EXCEL REPORT (.xlsx)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                }
            }

            if (selectedFilterTab == "DONE") {
                val filteredHistory = remember(pmHistoryList, searchQuery) {
                    pmHistoryList.filter { hist ->
                        hist.toolId.contains(searchQuery, ignoreCase = true) ||
                        hist.toolName.contains(searchQuery, ignoreCase = true) ||
                        hist.line.contains(searchQuery, ignoreCase = true) ||
                        hist.station.contains(searchQuery, ignoreCase = true) ||
                        hist.completedByName.contains(searchQuery, ignoreCase = true)
                    }.sortedByDescending { it.timestamp }
                }

                if (filteredHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No completed PM history records found.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredHistory, key = { it.historyId.ifEmpty { "${it.toolId}_${it.timestamp}" } }) { historyEntry ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedCompletedPmForDetails = historyEntry },
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(historyEntry.toolId, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                            Text(historyEntry.toolName, fontSize = 13.sp, color = Color(0xFF64748B))
                                        }

                                        Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(6.dp)) {
                                            Text("COMPLETED", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    Text("Location: Line ${historyEntry.line} | Station ${historyEntry.station}", fontSize = 12.sp, color = Color(0xFF475569))
                                    Text("Completed On: ${historyEntry.completedDate} • By: ${historyEntry.completedByName.ifEmpty { "Technician" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    Text("Parts Used: ${historyEntry.partsUsed}", fontSize = 12.sp, color = Color(0xFF2563EB))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { selectedCompletedPmForDetails = historyEntry },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("VIEW DETAILS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                selectedToolIdForHistory = historyEntry.toolId
                                                showHistoryDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("ALL HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val filteredActiveList = remember(selectedFilterTab, activePmListWithDerivedStatus, originalToolList, searchQuery) {
                    val rawList = when (selectedFilterTab) {
                        "TODAY" -> todaySchedules
                        "PENDING" -> pendingSchedules
                        "OVERDUE" -> overdueSchedules
                        else -> {
                            if (originalToolList.isNotEmpty()) {
                                originalToolList.map { tool ->
                                    activePmListWithDerivedStatus.find { it.toolId.equals(tool.toolId, ignoreCase = true) }
                                        ?: PmEntry(
                                            pmId = "PM_${tool.toolId}",
                                            toolId = tool.toolId,
                                            toolName = tool.toolName,
                                            line = tool.line,
                                            station = tool.station,
                                            scheduledDate = tool.nextPmDate.ifEmpty { dateFormat.format(Date()) },
                                            status = "PENDING",
                                            company = company,
                                            block = block
                                        )
                                }
                            } else {
                                activePmListWithDerivedStatus
                            }
                        }
                    }

                    rawList.filter { entry ->
                        entry.toolId.contains(searchQuery, ignoreCase = true) ||
                        entry.toolName.contains(searchQuery, ignoreCase = true) ||
                        entry.line.contains(searchQuery, ignoreCase = true) ||
                        entry.station.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (filteredActiveList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when (selectedFilterTab) {
                                "TODAY" -> "No PM scheduled for today."
                                "PENDING" -> "No upcoming pending PMs."
                                "OVERDUE" -> "Great — no overdue PMs."
                                else -> "No active PM schedules found."
                            },
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredActiveList, key = { it.pmId.ifEmpty { "pm_${it.toolId}" } }) { entry ->
                            val scheduledObj = parsePmDate(entry.scheduledDate)
                            val statusDerived = entry.status
                            val isOverdue = statusDerived == "OVERDUE"

                            val daysOverdue = if (isOverdue && scheduledObj != null) {
                                val diff = todayDate.time - scheduledObj.time
                                (diff / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
                            } else 0

                            val statusBadgeText = when (statusDerived) {
                                "IN_PROGRESS" -> "IN PROGRESS"
                                "OVERDUE" -> "OVERDUE BY $daysOverdue DAYS"
                                "TODAY" -> "DUE TODAY"
                                else -> "UPCOMING (6-MO CYCLE)"
                            }

                            val statusBg = when (statusDerived) {
                                "IN_PROGRESS" -> Color(0xFFFFFBEB)
                                "OVERDUE" -> Color(0xFFFEF2F2)
                                "TODAY" -> Color(0xFFEFF6FF)
                                else -> Color(0xFFF1F5F9)
                            }

                            val statusTxtColor = when (statusDerived) {
                                "IN_PROGRESS" -> Color(0xFFD97706)
                                "OVERDUE" -> Color(0xFFDC2626)
                                "TODAY" -> Color(0xFF2563EB)
                                else -> Color(0xFF64748B)
                            }

                            val matchingTool = originalToolList.find { it.toolId.equals(entry.toolId, ignoreCase = true) }
                            val serialNo = matchingTool?.serialNumber?.ifEmpty { "SN-${entry.toolId}" } ?: "SN-${entry.toolId}"
                            val model = matchingTool?.model?.ifEmpty { entry.toolName } ?: entry.toolName

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.toolId, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                            Text(model, fontSize = 13.sp, color = Color(0xFF64748B))
                                        }

                                        Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                                            Text(statusBadgeText, color = statusTxtColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    Text("Location: Line ${entry.line} | Station ${entry.station} • Serial No: $serialNo", fontSize = 12.sp, color = Color(0xFF475569))
                                    Text("PM Cycle: 6 Months • Scheduled Date: ${entry.scheduledDate}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isOverdue) Color(0xFFDC2626) else Color(0xFF0F172A))

                                    if (statusDerived == "IN_PROGRESS") {
                                        Text("Started At: ${entry.startedAt.ifEmpty { "Today" }} • By: ${entry.startedBy.ifEmpty { userName }}", fontSize = 12.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                selectedToolIdForHistory = entry.toolId
                                                showHistoryDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("VIEW HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (userRole.lowercase(Locale.ROOT) in listOf("admin", "super_admin")) {
                                            OutlinedButton(
                                                onClick = {
                                                    adminEditTool = matchingTool ?: OriginalTool(toolId = entry.toolId, toolName = entry.toolName, line = entry.line, station = entry.station)
                                                    showAdminEditDialog = true
                                                },
                                                modifier = Modifier.size(36.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit Schedule", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        if (selectedFilterTab != "ALL") {
                                            Button(
                                                onClick = {
                                                    val startTimeStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
                                                    val activeKey = if (entry.pmId.isNotEmpty()) entry.pmId else "PM_${entry.toolId}"
                                                    val inProgressEntry = entry.copy(
                                                        pmId = activeKey,
                                                        status = "IN_PROGRESS",
                                                        startedAt = startTimeStr,
                                                        startedBy = userName,
                                                        company = company,
                                                        block = block
                                                    )
                                                    firebaseManager.savePmEntry(inProgressEntry)

                                                    currentPmEntryForInspection = inProgressEntry
                                                    currentWorkflowStep = 1
                                                    showInspectionWorkflow = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = if (statusDerived == "IN_PROGRESS") Color(0xFFD97706) else Color(0xFF2563EB))
                                            ) {
                                                Text(if (statusDerived == "IN_PROGRESS") "CONTINUE PM" else "START PM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

    if (showInspectionWorkflow && currentPmEntryForInspection != null) {
        val pmTool = currentPmEntryForInspection!!
        val matchingTool = originalToolList.find { it.toolId.equals(pmTool.toolId, ignoreCase = true) }
        val serialNo = matchingTool?.serialNumber?.ifEmpty { "SN-${pmTool.toolId}" } ?: "SN-${pmTool.toolId}"
        val model = matchingTool?.model?.ifEmpty { pmTool.toolName } ?: pmTool.toolName

        val checklistItems = remember {
            mutableStateListOf(
                "Tool Cleaning" to false,
                "Visual Inspection" to false,
                "Cable Inspection" to false,
                "Connector Inspection" to false,
                "Motor Inspection" to false,
                "Gearbox Inspection" to false,
                "Fastener Inspection" to false,
                "Torque Verification" to false,
                "RPM Verification" to false,
                "Functional Test" to false
            )
        }

        var pmResult by remember { mutableStateOf("PASS") }
        var observationText by remember { mutableStateOf("") }
        var selectedUsedParts = remember { mutableStateListOf<UsedPartItem>() }
        var showSparePartPicker by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showInspectionWorkflow = false },
            title = {
                Text(
                    when (currentWorkflowStep) {
                        1 -> "PM Inspection Checklist (${pmTool.toolId})"
                        2 -> "PM Result & Parts Used (${pmTool.toolId})"
                        else -> "Complete PM Review (${pmTool.toolId})"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(pmTool.toolId, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2563EB))
                            Text("Model: $model • S/N: $serialNo", fontSize = 12.sp, color = Color(0xFF0F172A))
                            Text("Line: ${pmTool.line} • Station: ${pmTool.station}", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    }

                    if (currentWorkflowStep == 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Required Checklist Items *", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val completedCount = checklistItems.count { it.second }
                            Text("$completedCount / ${checklistItems.size} Checked", fontSize = 12.sp, color = if (completedCount == checklistItems.size) Color(0xFF10B981) else Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                        }

                        checklistItems.forEachIndexed { idx, pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { checklistItems[idx] = pair.first to !pair.second }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pair.second,
                                    onCheckedChange = { isChecked -> checklistItems[idx] = pair.first to isChecked }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(pair.first, fontSize = 13.sp)
                            }
                        }
                    } else if (currentWorkflowStep == 2) {
                        Text("Select Inspection Result:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = pmResult == "PASS",
                                onClick = { pmResult = "PASS" },
                                label = { Text("PASS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = pmResult == "PASS WITH OBSERVATION",
                                onClick = { pmResult = "PASS WITH OBSERVATION" },
                                label = { Text("OBSERVATION", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            FilterChip(
                                selected = pmResult == "FAIL",
                                onClick = { pmResult = "FAIL" },
                                label = { Text("FAIL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (pmResult != "PASS") {
                            OutlinedTextField(
                                value = observationText,
                                onValueChange = { observationText = it },
                                label = { Text("Mandatory Observation / Action Taken *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = false
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Parts Used During PM", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            TextButton(onClick = { showSparePartPicker = true }) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ ADD SPARE PART", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (selectedUsedParts.isEmpty()) {
                            Text("No spare parts added for this PM.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            selectedUsedParts.forEach { partItem ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(partItem.partName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Surface(
                                                    color = if (partItem.isManual) Color(0xFFFEF3C7) else Color(0xFFEFF6FF),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (partItem.isManual) "Manual Entry" else "Inventory List",
                                                        color = if (partItem.isManual) Color(0xFFD97706) else Color(0xFF2563EB),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Text("Part No: ${partItem.partNo} • Qty: ${partItem.quantity}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = { selectedUsedParts.remove(partItem) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Ready to Complete PM", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF10B981))
                                Text("Result: $pmResult", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (observationText.isNotBlank()) {
                                    Text("Observation: $observationText", fontSize = 12.sp)
                                }
                                val partsSummary = if (selectedUsedParts.isEmpty()) "No Parts Used"
                                else selectedUsedParts.joinToString { item ->
                                    val type = if (item.isManual) "Manual Entry" else "Inventory List"
                                    "${item.partName} x${item.quantity} [$type]"
                                }
                                Text("Parts Used: $partsSummary", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))

                                val actualCompletedDateStr = currentDateStr
                                val nextPmDateStr = calculateNextPmDate(actualCompletedDateStr, pmTool.pmCycleMonths)
                                Text("Next PM Scheduled Date: $nextPmDateStr", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (currentWorkflowStep < 3) {
                    Button(
                        onClick = {
                            if (currentWorkflowStep == 1 && checklistItems.any { !it.second }) {
                                Toast.makeText(context, "Please complete all checklist items before proceeding", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (currentWorkflowStep == 2 && pmResult != "PASS" && observationText.isBlank()) {
                                Toast.makeText(context, "Please enter an observation for $pmResult", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            currentWorkflowStep++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("NEXT", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            showCompleteConfirmationDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("COMPLETE PM", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showInspectionWorkflow = false }) { Text("Cancel") }
            }
        )

        if (showCompleteConfirmationDialog) {
            AlertDialog(
                onDismissRequest = { showCompleteConfirmationDialog = false },
                title = { Text("Confirm PM Completion", fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to complete PM for tool ${pmTool.toolId}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val cal = Calendar.getInstance()
                            val actualCompletedDateStr = currentDateStr
                            val completedDateDisplay = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(cal.time)

                            val partsSummaryStr = if (selectedUsedParts.isEmpty()) "No Parts Used"
                            else selectedUsedParts.joinToString { item ->
                                val entryType = if (item.isManual) "Manual Entry" else "Inventory List"
                                "${item.partName} (${item.partNo}) x${item.quantity} [$entryType]"
                            }

                            val checklistMap = checklistItems.toMap()

                            val nextPmDateStr = calculateNextPmDate(actualCompletedDateStr, pmTool.pmCycleMonths)

                            if (!locallyCompletedToolIds.contains(pmTool.toolId)) {
                                locallyCompletedToolIds.add(pmTool.toolId)
                            }

                            val histKey = "PM_HIST_${pmTool.toolId}_${System.currentTimeMillis()}"
                            val pmHistoryEntry = PmHistoryEntry(
                                historyId = histKey,
                                toolId = pmTool.toolId,
                                toolName = pmTool.toolName,
                                line = pmTool.line,
                                station = pmTool.station,
                                scheduledDate = pmTool.scheduledDate,
                                actualCompletedDate = actualCompletedDateStr,
                                completedDate = completedDateDisplay,
                                completedBy = userName,
                                completedByName = userName,
                                status = "DONE",
                                pmCycleMonths = pmTool.pmCycleMonths,
                                partsUsed = partsSummaryStr,
                                remarks = observationText.ifEmpty { pmResult },
                                checklist = checklistMap,
                                company = company,
                                block = block
                            )

                            val activeScheduleKey = "PM_${pmTool.toolId}"
                            val nextActiveSchedule = PmEntry(
                                pmId = activeScheduleKey,
                                toolId = pmTool.toolId,
                                toolName = pmTool.toolName,
                                line = pmTool.line,
                                station = pmTool.station,
                                scheduledDate = nextPmDateStr,
                                status = "Pending",
                                startedAt = "",
                                startedBy = "",
                                partsUsed = "No Parts Used",
                                completedDate = "",
                                pmCycleMonths = pmTool.pmCycleMonths,
                                company = company,
                                block = block
                            )

                            val matchingTool = originalToolList.find { it.toolId.equals(pmTool.toolId, ignoreCase = true) }
                            val updatedTool = (matchingTool ?: OriginalTool(
                                toolId = pmTool.toolId,
                                toolName = pmTool.toolName,
                                line = pmTool.line,
                                station = pmTool.station
                            )).copy(
                                lastPmDate = actualCompletedDateStr,
                                nextPmDate = nextPmDateStr,
                                pmCycleMonths = pmTool.pmCycleMonths,
                                company = company,
                                block = block
                            )

                            firebaseManager.completePmTransaction(
                                historyEntry = pmHistoryEntry,
                                nextActiveSchedule = nextActiveSchedule,
                                updatedTool = updatedTool,
                                onSuccess = {
                                    selectedUsedParts.forEach { usedPart ->
                                        if (!usedPart.isManual) {
                                            val matchingProd = productList.find { it.productCode.equals(usedPart.partNo, ignoreCase = true) }
                                            if (matchingProd != null) {
                                                val sale = SalesEntry(
                                                    timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                                                    productCode = usedPart.partNo,
                                                    productName = usedPart.partName,
                                                    toolId = pmTool.toolId,
                                                    qtySold = usedPart.quantity,
                                                    salePrice = matchingProd.unitPrice,
                                                    totalRevenue = usedPart.quantity * matchingProd.unitPrice,
                                                    paymentMode = "Preventive Maintenance",
                                                    remarks = "PM Auto Deduct for ${pmTool.toolId}",
                                                    company = company,
                                                    block = block
                                                )
                                                firebaseManager.recordSale(sale)
                                                firebaseManager.deductProductStock(usedPart.partNo, company, block, usedPart.quantity)
                                            }
                                        }
                                    }

                                    Toast.makeText(context, "PM Completed Successfully! Next PM Scheduled for $nextPmDateStr", Toast.LENGTH_SHORT).show()
                                    showCompleteConfirmationDialog = false
                                    showInspectionWorkflow = false
                                },
                                onFailure = { ex ->
                                    locallyCompletedToolIds.remove(pmTool.toolId)
                                    Toast.makeText(context, "PM completion failed. Please try again: ${ex.message}", Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Confirm & Complete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCompleteConfirmationDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showSparePartPicker) {
            var pickerTab by remember { mutableStateOf(0) }
            var selectedProduct by remember { mutableStateOf<Product?>(null) }
            var productDropdownExpanded by remember { mutableStateOf(false) }
            var inventoryQtyText by remember { mutableStateOf("1") }

            var manualPartName by remember { mutableStateOf("") }
            var manualPartNo by remember { mutableStateOf("") }
            var manualQtyText by remember { mutableStateOf("1") }

            AlertDialog(
                onDismissRequest = { showSparePartPicker = false },
                title = { Text("Add Spare Part Used", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TabRow(selectedTabIndex = pickerTab) {
                            Tab(
                                selected = pickerTab == 0,
                                onClick = { pickerTab = 0 },
                                text = { Text("Inventory List", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = pickerTab == 1,
                                onClick = { pickerTab = 1 },
                                text = { Text("Manual Entry", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (pickerTab == 0) {
                            Text("Select Part from Inventory:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedProduct?.let { "${it.productName} (${it.productCode})" } ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Choose Spare Part") },
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        IconButton(onClick = { productDropdownExpanded = !productDropdownExpanded }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Part")
                                        }
                                    },
                                    singleLine = true
                                )

                                DropdownMenu(
                                    expanded = productDropdownExpanded,
                                    onDismissRequest = { productDropdownExpanded = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier.width(280.dp).heightIn(max = 240.dp)
                                ) {
                                    if (productList.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No products in inventory", color = Color.Gray, fontSize = 12.sp) },
                                            onClick = { productDropdownExpanded = false }
                                        )
                                    } else {
                                        productList.forEach { prod ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(prod.productName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text("Part No: ${prod.productCode} | Stock: ${prod.currentStock}", fontSize = 11.sp, color = Color.Gray)
                                                    }
                                                },
                                                onClick = {
                                                    selectedProduct = prod
                                                    productDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = inventoryQtyText,
                                onValueChange = { inventoryQtyText = it.filter { char -> char.isDigit() } },
                                label = { Text("Quantity") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        } else {
                            Text("Enter Custom Part Details:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = manualPartName,
                                onValueChange = { manualPartName = it },
                                label = { Text("Part Name / Description *") },
                                placeholder = { Text("e.g. Gasket Seal / Custom Switch") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = manualPartNo,
                                onValueChange = { manualPartNo = it },
                                label = { Text("Part No / Code (Optional)") },
                                placeholder = { Text("e.g. MANUAL-01") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = manualQtyText,
                                onValueChange = { manualQtyText = it.filter { char -> char.isDigit() } },
                                label = { Text("Quantity") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pickerTab == 0) {
                                val prod = selectedProduct
                                if (prod != null) {
                                    val qty = inventoryQtyText.toIntOrNull() ?: 1
                                    val existing = selectedUsedParts.find { it.partNo == prod.productCode && !it.isManual }
                                    if (existing != null) {
                                        existing.quantity += qty
                                    } else {
                                        selectedUsedParts.add(UsedPartItem(prod.productCode, prod.productName, qty, isManual = false))
                                    }
                                    showSparePartPicker = false
                                }
                            } else {
                                if (manualPartName.isNotBlank()) {
                                    val qty = manualQtyText.toIntOrNull() ?: 1
                                    val code = manualPartNo.ifBlank { "MANUAL" }
                                    selectedUsedParts.add(UsedPartItem(code, manualPartName.trim(), qty, isManual = true))
                                    showSparePartPicker = false
                                }
                            }
                        }
                    ) {
                        Text("Add Part")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSparePartPicker = false }) { Text("Cancel") }
                }
            )
        }
    }

    if (selectedCompletedPmForDetails != null) {
        val completedPm = selectedCompletedPmForDetails!!
        val matchingTool = originalToolList.find { it.toolId.equals(completedPm.toolId, ignoreCase = true) }
        val serialNo = matchingTool?.serialNumber?.ifEmpty { "SN-${completedPm.toolId}" } ?: "SN-${completedPm.toolId}"
        val model = matchingTool?.model?.ifEmpty { completedPm.toolName } ?: completedPm.toolName

        AlertDialog(
            onDismissRequest = { selectedCompletedPmForDetails = null },
            title = { Text("Completed PM Details (${completedPm.toolId})", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(completedPm.toolId, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF10B981))
                                Surface(color = Color(0xFF10B981), shape = RoundedCornerShape(6.dp)) {
                                    Text("COMPLETED", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            Text("Model: $model", fontSize = 13.sp, color = Color(0xFF0F172A))
                            Text("Serial No: $serialNo", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text("Location: Line ${completedPm.line} • Station ${completedPm.station}", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text("Completed By: ${completedPm.completedByName.ifEmpty { "Technician" }}", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Maintenance Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                            HorizontalDivider(color = Color(0xFFF1F5F9))

                            SpecRow("Scheduled Date", completedPm.scheduledDate)
                            SpecRow("Actual Completion", completedPm.actualCompletedDate)
                            SpecRow("Completed Timestamp", completedPm.completedDate.ifEmpty { "N/A" })
                            SpecRow("Status", completedPm.status)

                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Spare Parts Used / Remarks:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2563EB))
                            Text(completedPm.partsUsed.ifEmpty { "No Parts Used" }, fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.SemiBold)

                            if (completedPm.remarks.isNotBlank()) {
                                Text("Inspection Remarks: ${completedPm.remarks}", fontSize = 12.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { selectedCompletedPmForDetails = null }) { Text("Close") }
            }
        )
    }

    if (showAdminEditDialog && adminEditTool != null) {
        val editTool = adminEditTool!!
        var newDateStr by remember { mutableStateOf(editTool.nextPmDate.ifEmpty { currentDateStr }) }
        var newCycleMonthsStr by remember { mutableStateOf(editTool.pmCycleMonths.toString()) }
        var changeReason by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAdminEditDialog = false },
            title = { Text("Admin: Edit PM Schedule (${editTool.toolId})", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tool ID: ${editTool.toolId} • ${editTool.model}", fontSize = 12.sp, color = Color(0xFF64748B))

                    OutlinedTextField(
                        value = newDateStr,
                        onValueChange = { newDateStr = it },
                        label = { Text("New Scheduled Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newCycleMonthsStr,
                        onValueChange = { newCycleMonthsStr = it },
                        label = { Text("PM Cycle Months (Default: 6)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = changeReason,
                        onValueChange = { changeReason = it },
                        label = { Text("Audit Reason for Schedule Change *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (changeReason.isBlank()) {
                            Toast.makeText(context, "Please enter an audit reason", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val newCycleMonths = newCycleMonthsStr.toIntOrNull() ?: 6

                        val activeScheduleKey = "PM_${editTool.toolId}"
                        val updatedSchedule = PmEntry(
                            pmId = activeScheduleKey,
                            toolId = editTool.toolId,
                            toolName = editTool.toolName,
                            line = editTool.line,
                            station = editTool.station,
                            scheduledDate = newDateStr,
                            status = "Pending",
                            pmCycleMonths = newCycleMonths,
                            company = company,
                            block = block
                        )
                        firebaseManager.savePmEntry(updatedSchedule)

                        val updatedTool = editTool.copy(
                            nextPmDate = newDateStr,
                            pmCycleMonths = newCycleMonths,
                            company = company,
                            block = block
                        )
                        firebaseManager.saveOriginalTool(updatedTool)

                        firebaseManager.logAdminDateChangeAudit(
                            toolId = editTool.toolId,
                            oldDate = editTool.nextPmDate,
                            newDate = newDateStr,
                            changedBy = userName,
                            changedByName = userName,
                            reason = changeReason
                        )

                        Toast.makeText(context, "PM Schedule Updated by Admin", Toast.LENGTH_SHORT).show()
                        showAdminEditDialog = false
                        adminEditTool = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text("Save & Log Audit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showExportMonthDialog) {
        var tempMonth by remember { mutableStateOf(exportMonth) }
        var tempYear by remember { mutableStateOf(exportYear) }
        var monthExpanded by remember { mutableStateOf(false) }
        var yearExpanded by remember { mutableStateOf(false) }

        val yearsList = (2023..2030).toList()

        AlertDialog(
            onDismissRequest = { showExportMonthDialog = false },
            title = { Text("Export Monthly PM Excel Report", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Select month and year to generate a full Excel report of all PMs completed during that period.", fontSize = 12.sp, color = Color(0xFF64748B))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = monthNames[tempMonth],
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Month") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { monthExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                        )
                        DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                            monthNames.forEachIndexed { idx, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        tempMonth = idx
                                        monthExpanded = false
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
                            label = { Text("Select Year") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { IconButton(onClick = { yearExpanded = true }) { Icon(Icons.Default.ArrowDropDown, null) } }
                        )
                        DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                            yearsList.forEach { yr ->
                                DropdownMenuItem(
                                    text = { Text(yr.toString()) },
                                    onClick = {
                                        tempYear = yr
                                        yearExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        exportMonth = tempMonth
                        exportYear = tempYear
                        showExportMonthDialog = false
                        scope.launch {
                            MISReportHelper.generateAndShareMonthlyPMExcelReport(
                                context = context,
                                monthIndex = exportMonth,
                                year = exportYear,
                                pmEntries = pmEntries,
                                pmHistoryList = pmHistoryList
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("GENERATE & EXPORT EXCEL", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportMonthDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showHistoryDialog && selectedToolIdForHistory.isNotBlank()) {
        val toolHistoryRecords = pmHistoryList.filter { it.toolId.equals(selectedToolIdForHistory, ignoreCase = true) }.sortedByDescending { it.timestamp }

        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Tool PM History ($selectedToolIdForHistory)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total PMs", fontSize = 11.sp, color = Color.Gray)
                                Text("${toolHistoryRecords.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Completed", fontSize = 11.sp, color = Color.Gray)
                                Text("${toolHistoryRecords.count { isPmCompleted(it.status) }}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF10B981))
                            }
                        }
                    }

                    Text("PM History Records", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    if (toolHistoryRecords.isEmpty()) {
                        Text("No PM history records found for this tool.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        toolHistoryRecords.forEach { pmHist ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Status: ${pmHist.status}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF10B981))
                                        Text("Completed: ${pmHist.completedDate}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text("By: ${pmHist.completedByName.ifEmpty { "Technician" }}", fontSize = 11.sp, color = Color.DarkGray)
                                    Text("Parts Used: ${pmHist.partsUsed}", fontSize = 11.sp, color = Color(0xFF2563EB))
                                    if (pmHist.remarks.isNotBlank()) {
                                        Text("Remarks: ${pmHist.remarks}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showHistoryDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun PmDashboardCard(
    label: String,
    count: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 4.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = count, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(text = label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        }
    }
}
