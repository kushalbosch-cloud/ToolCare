package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties

private fun isTodayDate(timestampMs: Long): Boolean {
    if (timestampMs <= 0L) return false
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestampMs }
    val cal2 = java.util.Calendar.getInstance()
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun isTodayDate(dateStr: String?): Boolean {
    if (dateStr.isNullOrBlank()) return false
    val clean = dateStr.trim()
    val today = java.util.Calendar.getInstance()

    val todayDateStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(today.time)
    val todayIsoStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(today.time)
    val todayShortStr = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(today.time)
    val todayDashStr = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.getDefault()).format(today.time)

    if (clean.startsWith(todayDateStr) || clean.startsWith(todayIsoStr) ||
        clean.contains(todayDateStr) || clean.contains(todayIsoStr) ||
        clean.contains(todayShortStr) || clean.startsWith(todayDashStr)) {
        return true
    }
    return false
}

private fun extractMonthYear(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val clean = dateStr.trim()
    val formats = arrayOf("yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd MMM yyyy, HH:mm", "dd MMM yyyy")
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(clean)
            if (date != null) {
                return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {}
    }
    return ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakdownScreen(
    backupToolList: List<BackupTool>,
    originalToolList: List<OriginalTool> = emptyList(),
    breakdownEntries: List<BreakdownEntry> = emptyList(),
    onSubmitBreakdown: (originalId: String, backupId: String?, line: String, station: String, problem: String) -> Unit,
    onUpdateBackupTool: ((BackupTool) -> Unit)? = null,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showReportDialog by remember { mutableStateOf(false) }

    val nonHoldBreakdowns = remember(breakdownEntries) {
        breakdownEntries.filter { bd ->
            !bd.entryId.startsWith("BD_AUTO_") &&
            !bd.status.contains("HOLD", ignoreCase = true) &&
            !bd.problem.contains("Verification Failed", ignoreCase = true)
        }.sortedByDescending { it.createdAt }
    }

    val activeBreakdowns = remember(nonHoldBreakdowns) {
        nonHoldBreakdowns.filter { it.status != ToolStatus.HANDOVER_COMPLETED }
    }

    val todayCompletedLog = remember(nonHoldBreakdowns) {
        nonHoldBreakdowns.filter { bd ->
            (bd.status == ToolStatus.HANDOVER_COMPLETED || bd.status == "Repaired (Pending Handover)" || bd.status == "Closed") &&
            (isTodayDate(bd.handoverAt) || isTodayDate(bd.repairedAt) || isTodayDate(bd.timestamp))
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedMonthFilter by remember { mutableStateOf("All Months") }

    val currentMonthStr = remember {
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val availableMonths = remember(nonHoldBreakdowns, currentMonthStr) {
        val extracted = nonHoldBreakdowns.map { extractMonthYear(it.timestamp) }.filter { it.isNotBlank() }.distinct()
        val list = mutableListOf("All Months")
        if (!extracted.contains(currentMonthStr)) {
            list.add(currentMonthStr)
        }
        list.addAll(extracted)
        list.distinct()
    }

    val filteredActiveBreakdowns = remember(activeBreakdowns, searchQuery, selectedMonthFilter) {
        activeBreakdowns.filter { bd ->
            val matchesSearch = searchQuery.isBlank() ||
                    bd.originalToolId.contains(searchQuery, ignoreCase = true) ||
                    bd.backupToolId.contains(searchQuery, ignoreCase = true) ||
                    bd.problem.contains(searchQuery, ignoreCase = true) ||
                    bd.station.contains(searchQuery, ignoreCase = true)

            val bdMonth = extractMonthYear(bd.timestamp)
            val matchesMonth = selectedMonthFilter == "All Months" || bdMonth.equals(selectedMonthFilter, ignoreCase = true)

            matchesSearch && matchesMonth
        }
    }

    val filteredTodayLog = remember(todayCompletedLog, searchQuery, selectedMonthFilter) {
        todayCompletedLog.filter { bd ->
            val matchesSearch = searchQuery.isBlank() ||
                    bd.originalToolId.contains(searchQuery, ignoreCase = true) ||
                    bd.backupToolId.contains(searchQuery, ignoreCase = true) ||
                    bd.problem.contains(searchQuery, ignoreCase = true) ||
                    bd.station.contains(searchQuery, ignoreCase = true)

            val bdMonth = extractMonthYear(bd.timestamp)
            val matchesMonth = selectedMonthFilter == "All Months" || bdMonth.equals(selectedMonthFilter, ignoreCase = true)

            matchesSearch && matchesMonth
        }
    }

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breakdown Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Report New", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showReportDialog = true },
                containerColor = Color(0xFFDC2626),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Report Breakdown")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${activeBreakdowns.size} Tools Down", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Immediate attention required", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showReportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Log Breakdown", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Color(0xFF0F172A)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Active Breakdown (${filteredActiveBreakdowns.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Today's Log (${filteredTodayLog.size})", fontWeight = FontWeight.Bold) }
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tool ID, backup, station, problem...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            if (availableMonths.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableMonths, key = { it }) { monthName ->
                        FilterChip(
                            selected = selectedMonthFilter == monthName,
                            onClick = { selectedMonthFilter = monthName },
                            label = { Text("📅 $monthName", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            }

            if (selectedTab == 0) {
                if (filteredActiveBreakdowns.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No active breakdown entries matching filter.", color = Color.Gray, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredActiveBreakdowns, key = { it.entryId.ifEmpty { "bd_${it.hashCode()}" } }) { entry ->
                            BreakdownItemCard(entry = entry)
                        }
                    }
                }
            } else {
                if (filteredTodayLog.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No activity recorded for today matching filter.", color = Color.Gray, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            filteredTodayLog,
                            key = { it.entryId.ifEmpty { "today_bd_${it.hashCode()}" } }
                        ) { entry ->
                            BreakdownItemCard(entry = entry)
                        }
                    }
                }
            }
        }
    }

    if (showReportDialog) {
        ReportBreakdownDialog(
            backupToolList = backupToolList,
            originalToolList = originalToolList,
            breakdownEntries = breakdownEntries,
            onDismiss = {
                try {
                    showReportDialog = false
                } catch (e: Exception) {
                    Log.e("BreakdownScreen", "Error dismissing report dialog", e)
                }
            },
            onSubmit = { origId, backId, line, station, problem ->
                showReportDialog = false
                try {
                    onSubmitBreakdown(origId, backId, line, station, problem)
                } catch (e: Exception) {
                    Log.e("BreakdownScreen", "Error during onSubmitBreakdown", e)
                }
            },
            onUpdateBackupTool = onUpdateBackupTool
        )
    }
}

@Composable
fun BreakdownItemCard(entry: BreakdownEntry) {
    val statusBg = when (entry.status) {
        "OPEN", "Reported" -> Color(0xFFFEF2F2)
        "IN REPAIR" -> Color(0xFFFFFBEB)
        "TESTING" -> Color(0xFFEFF6FF)
        else -> Color(0xFFECFDF5)
    }

    val statusTxt = when (entry.status) {
        "OPEN", "Reported" -> Color(0xFFDC2626)
        "IN REPAIR" -> Color(0xFFD97706)
        "TESTING" -> Color(0xFF2563EB)
        else -> Color(0xFF10B981)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_drill_tool),
                contentDescription = "Tool",
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(4.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.originalToolId, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                    Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                        Text(
                            text = entry.status,
                            color = statusTxt,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (entry.backupToolId.isNotBlank() && entry.backupToolId != "No Backup Issued") {
                    Text("Assigned Backup: ${entry.backupToolId}", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                }
                Text("Station ${entry.station} | ${entry.line}", fontSize = 12.sp, color = Color(0xFF475569))

                Spacer(modifier = Modifier.height(4.dp))
                Text("⚠️ ${entry.problem}", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                Text("🕒 ${entry.timestamp}", fontSize = 10.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
fun ReportBreakdownDialog(
    backupToolList: List<BackupTool>,
    originalToolList: List<OriginalTool> = emptyList(),
    breakdownEntries: List<BreakdownEntry> = emptyList(),
    onDismiss: () -> Unit,
    onSubmit: (originalId: String, backupId: String?, line: String, station: String, problem: String) -> Unit,
    onUpdateBackupTool: ((BackupTool) -> Unit)? = null
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var toolSearchQuery by remember { mutableStateOf("") }
    var selectedOrigTool by remember { mutableStateOf<OriginalTool?>(null) }
    var backId by remember { mutableStateOf("None / No Backup Required") }
    var line by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf("") }

    var origDropdownExpanded by remember { mutableStateOf(false) }
    var backupDropdownExpanded by remember { mutableStateOf(false) }

    val filteredToolsForSearch = remember(toolSearchQuery, originalToolList) {
        if (toolSearchQuery.isBlank()) originalToolList
        else originalToolList.filter {
            it.toolId.contains(toolSearchQuery, ignoreCase = true) ||
            it.toolName.contains(toolSearchQuery, ignoreCase = true) ||
            it.model.contains(toolSearchQuery, ignoreCase = true) ||
            it.line.contains(toolSearchQuery, ignoreCase = true) ||
            it.station.contains(toolSearchQuery, ignoreCase = true) ||
            it.serialNumber.contains(toolSearchQuery, ignoreCase = true)
        }
    }

    val activeBreakdownForSelectedTool = remember(selectedOrigTool, toolSearchQuery, breakdownEntries) {
        val tid = selectedOrigTool?.toolId ?: toolSearchQuery.trim()
        if (tid.isBlank()) null
        else breakdownEntries.find {
            it.originalToolId.equals(tid, ignoreCase = true) &&
            it.status != ToolStatus.HANDOVER_COMPLETED
        }
    }

    val allAvailableBackups = remember(backupToolList) {
        backupToolList.filter { ToolStatus.isAvailableBackup(it.status) }
    }

    val safeDismiss: () -> Unit = {
        try {
            keyboardController?.hide()
            origDropdownExpanded = false
            backupDropdownExpanded = false
        } catch (_: Exception) {}
        try {
            onDismiss()
        } catch (e: Exception) {
            Log.e("ReportBreakdownDialog", "Error onDismiss", e)
        }
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        title = { Text("Report Breakdown Issue", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState()).imePadding()) {
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = toolSearchQuery,
                        onValueChange = { query ->
                            toolSearchQuery = query
                            origDropdownExpanded = true
                            val exactMatch = originalToolList.find { it.toolId.equals(query.trim(), ignoreCase = true) }
                            if (exactMatch != null) {
                                selectedOrigTool = exactMatch
                                line = exactMatch.line
                                station = exactMatch.station
                            } else {
                                selectedOrigTool = null
                            }
                        },
                        label = { Text("Search / Type Original Tool ID *") },
                        placeholder = { Text("Type tool ID, model or line...") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { origDropdownExpanded = !origDropdownExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Tool")
                            }
                        },
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = origDropdownExpanded && filteredToolsForSearch.isNotEmpty(),
                        onDismissRequest = { origDropdownExpanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.width(280.dp).heightIn(max = 240.dp)
                    ) {
                        filteredToolsForSearch.forEach { tool ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${tool.toolId} (${tool.toolName})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Line: ${tool.line} | Station: ${tool.station}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                },
                                onClick = {
                                    selectedOrigTool = tool
                                    toolSearchQuery = tool.toolId
                                    line = tool.line
                                    station = tool.station
                                    origDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (activeBreakdownForSelectedTool != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), modifier = Modifier.fillMaxWidth()) {
                        Text("⚠️ Tool ${selectedOrigTool?.toolId ?: toolSearchQuery} is already under active breakdown (${activeBreakdownForSelectedTool.status}). Submit blocked.", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }

                OutlinedTextField(
                    value = line,
                    onValueChange = { line = it },
                    label = { Text("Line (e.g. Line-1)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = station,
                    onValueChange = { station = it },
                    label = { Text("Station (e.g. S-12)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = backId,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Backup Tool Selection (${allAvailableBackups.size} Available)") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { backupDropdownExpanded = !backupDropdownExpanded }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Backup Tool")
                            }
                        },
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = backupDropdownExpanded,
                        onDismissRequest = { backupDropdownExpanded = false },
                        properties = PopupProperties(focusable = false),
                        modifier = Modifier.width(280.dp).heightIn(max = 260.dp)
                    ) {
                        DropdownMenuItem(
                            text = { 
                                Column {
                                    Text("None / No Backup Required", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                                    Text("Submit breakdown report without backup tool", fontSize = 11.sp, color = Color.Gray)
                                }
                            },
                            onClick = {
                                backId = "None / No Backup Required"
                                backupDropdownExpanded = false
                            }
                        )

                        HorizontalDivider()

                        allAvailableBackups.forEach { bTool ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${bTool.toolId} (${bTool.make} ${bTool.model})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
                                        Text("Model: ${bTool.model} | S/N: ${bTool.serialNumber}", fontSize = 11.sp, color = Color(0xFF10B981))
                                    }
                                },
                                onClick = {
                                    backId = bTool.toolId
                                    backupDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = problem,
                    onValueChange = { problem = it },
                    label = { Text("Problem Description *") },
                    modifier = Modifier.fillMaxWidth().height(100.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = (selectedOrigTool != null || toolSearchQuery.isNotBlank()) && activeBreakdownForSelectedTool == null && problem.isNotBlank(),
                onClick = {
                    try {
                        keyboardController?.hide()
                        origDropdownExpanded = false
                        backupDropdownExpanded = false

                        val toolId = selectedOrigTool?.toolId ?: toolSearchQuery.trim()
                        if (toolId.isBlank()) return@Button

                        val finalBackupId = if (backId.isBlank() || 
                                                backId.contains("None", ignoreCase = true) || 
                                                backId.equals("No Backup Issued", ignoreCase = true)) null else backId

                        onSubmit(toolId, finalBackupId, line.trim(), station.trim(), problem.trim())
                    } catch (e: Exception) {
                        Log.e("ReportBreakdownDialog", "Error clicking submit breakdown button", e)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Submit Breakdown")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) { Text("Cancel") }
        }
    )
}
