package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    firebaseManager: FirebaseManager,
    originalToolList: List<OriginalTool>,
    backupToolList: List<BackupTool>,
    breakdownList: List<BreakdownEntry>,
    company: String,
    block: String,
    userRole: String = "technician",
    activeBlockFilter: String = "ALL",
    onBlockFilterChange: (String) -> Unit = {},
    onBackClick: () -> Unit,
) {
    val totalOriginal = originalToolList.size
    val activeBreakdowns = breakdownList.count { 
        it.status != "Handover Completed" &&
        !it.entryId.startsWith("BD_AUTO_") &&
        !it.status.contains("HOLD", ignoreCase = true) &&
        !it.problem.contains("Verification Failed", ignoreCase = true)
    }
    val backupInUse = backupToolList.count { it.status == "In Use" }
    val availableBackup = backupToolList.count { it.status == "Available" }
    val context = LocalContext.current

    val pmEntries by firebaseManager.getPmEntries(company, block).collectAsState(initial = emptyList())
    val pmHistoryList by firebaseManager.getPmHistory(company, block).collectAsState(initial = emptyList())
    val productList by firebaseManager.getProducts().collectAsState(initial = emptyList())

    // --- Multi-Block Switcher Data ---
    val isMultiBlockUser = remember(userRole) { userRole.lowercase() in listOf("admin", "technician", "super admin") }
    var masterBlocksMap by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }

    LaunchedEffect(company) {
        firebaseManager.getMasterCompanyList { map ->
            if (map.isNotEmpty()) masterBlocksMap = map
        }
    }

    val availableBlocksForCompany = remember(masterBlocksMap, company) {
        val blocksFromDb = masterBlocksMap[company] ?: emptyList()
        if (blocksFromDb.isNotEmpty()) listOf("All Blocks") + blocksFromDb
        else listOf("All Blocks", "V-Block", "Engine Block", "Transmission", "Assembly-1", "Assembly-2")
    }

    var blockDropdownExpanded by remember { mutableStateOf(false) }

    // --- Daily Date Picker State ---
    val calendar = Calendar.getInstance()
    var selectedDate by remember {
        mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time))
    }

    val datePickerDialog = android.app.DatePickerDialog(
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

    // --- Monthly Date Picker State ---
    val monthNames = remember { arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December") }
    val currentCal = remember { Calendar.getInstance() }
    var selectedMonth by remember { mutableStateOf(currentCal.get(Calendar.MONTH)) } // 0-indexed
    var selectedYear by remember { mutableStateOf(currentCal.get(Calendar.YEAR)) }
    var showMonthPickerDialog by remember { mutableStateOf(false) }

    // Dialog state for viewing text reports & exporting
    var showDailyTextReportDialog by remember { mutableStateOf(false) }
    var showMonthlyTextReportDialog by remember { mutableStateOf(false) }
    var showExportDailyMisDialog by remember { mutableStateOf(false) }

    val dailyTextReport = remember(selectedDate, originalToolList, backupToolList, breakdownList, pmEntries) {
        MISReportHelper.getFormattedTextReport(selectedDate, originalToolList, backupToolList, breakdownList, pmEntries)
    }

    val monthlyTextReport = remember(selectedMonth, selectedYear, originalToolList, backupToolList, breakdownList, pmEntries) {
        MISReportHelper.getFormattedMonthlyTextReport(selectedMonth, selectedYear, originalToolList, backupToolList, breakdownList, pmEntries)
    }

    val scope = rememberCoroutineScope()

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Block Switcher Header (Multi-Block Access for Admin/Technician vs Strict Isolation for Operators)
            if (isMultiBlockUser) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                            Text("Multi-Block Switcher:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                        }

                        Box {
                            OutlinedCard(
                                onClick = { blockDropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (activeBlockFilter.equals("ALL", ignoreCase = true)) "All Blocks" else activeBlockFilter,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
                                }
                            }

                            DropdownMenu(
                                expanded = blockDropdownExpanded,
                                onDismissRequest = { blockDropdownExpanded = false }
                            ) {
                                availableBlocksForCompany.forEach { blk ->
                                    DropdownMenuItem(
                                        text = { Text(blk, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                        onClick = {
                                            val targetFilter = if (blk == "All Blocks") "ALL" else blk
                                            onBlockFilterChange(targetFilter)
                                            blockDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                        Text(
                            text = "Assigned Block: $block (Isolated Operator View)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.system_summary),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            DashboardMetricCard(stringResource(R.string.total_original_tools), totalOriginal.toString(), Color(0xFF2563EB))
            DashboardMetricCard(stringResource(R.string.total_backup_tools), availableBackup.toString(), Color(0xFF10B981))
            DashboardMetricCard(stringResource(R.string.backup_tools_in_use), backupInUse.toString(), Color(0xFFD97706))
            DashboardMetricCard(stringResource(R.string.active_breakdown_repair), activeBreakdowns.toString(), Color(0xFFDC2626))

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- DAILY MIS REPORT SECTION ---
            Text(
                text = "Daily PM MIS Report",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Report Date: $selectedDate", fontWeight = FontWeight.Medium)
                Button(onClick = { datePickerDialog.show() }) {
                    Text("Pick Date")
                }
            }

            Button(
                onClick = { showDailyTextReportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64748B))
            ) {
                Text("View / Copy Daily Text Summary", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = { showExportDailyMisDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Export & Share Daily Report (.xlsx)", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // --- MONTHLY MIS REPORT SECTION ---
            Text(
                text = "Monthly MIS Report & Analytics",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Selected Month: ${monthNames[selectedMonth]} $selectedYear",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = { showMonthPickerDialog = true }) {
                    Text("Select Month/Year")
                }
            }

            // Monthly Computed Metrics Card
            val monthlyBreakdownsCount = breakdownList.count { MISReportHelper.isDateInMonthAndYear(it.timestamp, selectedMonth, selectedYear) }
            val monthlyRepairsCompletedCount = breakdownList.count { it.status == "Handover Completed" && MISReportHelper.isDateInMonthAndYear(it.timestamp, selectedMonth, selectedYear) }
            val monthlyCompletedPmsCount = pmHistoryList.count { MISReportHelper.isDateInMonthAndYear(it.actualCompletedDate, selectedMonth, selectedYear) || MISReportHelper.isDateInMonthAndYear(it.completedDate, selectedMonth, selectedYear) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Summary for ${monthNames[selectedMonth]} $selectedYear", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monthly Breakdowns Reported:")
                        Text(monthlyBreakdownsCount.toString(), fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monthly Repairs Completed:")
                        Text(monthlyRepairsCompletedCount.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monthly PMs Completed:")
                        Text(monthlyCompletedPmsCount.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                }
            }

            Button(
                onClick = { showMonthlyTextReportDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))
            ) {
                Text("View / Copy Monthly Text Summary", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    scope.launch {
                        MISReportHelper.generateAndShareMonthlyExcelReport(
                            context,
                            selectedMonth,
                            selectedYear,
                            originalToolList,
                            backupToolList,
                            breakdownList,
                            pmEntries,
                            pmHistoryList
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Export & Share Monthly Report (.xlsx)", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // --- Daily Text Report Dialog ---
    if (showDailyTextReportDialog) {
        AlertDialog(
            onDismissRequest = { showDailyTextReportDialog = false },
            title = { Text("Daily MIS Text Summary") },
            text = {
                Column {
                    SelectionContainer {
                        Text(dailyTextReport, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDailyTextReportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // --- Monthly Text Report Dialog ---
    if (showMonthlyTextReportDialog) {
        AlertDialog(
            onDismissRequest = { showMonthlyTextReportDialog = false },
            title = { Text("Monthly MIS Text Summary") },
            text = {
                Column {
                    SelectionContainer {
                        Text(monthlyTextReport, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMonthlyTextReportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // --- Export Daily MIS Dialog ---
    if (showExportDailyMisDialog) {
        ExportMisDialog(
            productList = productList,
            onDismiss = { showExportDailyMisDialog = false },
            onExport = { requiredParts, format ->
                showExportDailyMisDialog = false
                scope.launch {
                    if (format == ExportFormat.IMAGE) {
                        MISReportHelper.generateAndShareReportImage(
                            context,
                            selectedDate,
                            originalToolList,
                            backupToolList,
                            breakdownList,
                            pmEntries,
                            requiredParts
                        )
                    } else {
                        MISReportHelper.generateAndShareExcelReport(
                            context,
                            selectedDate,
                            originalToolList,
                            backupToolList,
                            breakdownList,
                            pmEntries,
                            productList,
                            requiredParts
                        )
                    }
                }
            }
        )
    }

    // --- Month / Year Picker Dialog ---
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
                    // Month Dropdown
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

                    // Year Dropdown
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
                TextButton(onClick = { showMonthPickerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DashboardMetricCard(label: String, value: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
