package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    tool: OriginalTool? = null,
    calibrationList: List<CalibrationEntry> = emptyList(),
    toolsList: List<OriginalTool> = emptyList(),
    onSaveCalibration: (CalibrationEntry) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current

    // Build master list of tools combining toolsList from Firebase and default Mahindra V-Block tools
    val combinedToolsList = remember(toolsList) {
        val defaults = listOf(
            OriginalTool(
                toolId = "T-025",
                toolName = "Bosch EXACT ION 12-700",
                model = "Bosch EXACT ION 12-700",
                line = "Line-1",
                station = "S-12",
                status = "OK",
                torque = "12.0 Nm",
                company = "Mahindra",
                block = "V-Block"
            ),
            OriginalTool(
                toolId = "V-B 15-191",
                toolName = "Bosch Torque Tool 15-191",
                model = "Bosch Angle EXACT ION 15-191",
                line = "V-Block Line",
                station = "ST-15",
                status = "OK",
                torque = "15.0 Nm",
                company = "Mahindra",
                block = "V-Block"
            ),
            OriginalTool(
                toolId = "T-026",
                toolName = "Bosch Cordless Nutrunner",
                model = "Bosch Cordless Nutrunner",
                line = "Line-1",
                station = "S-14",
                status = "OK",
                torque = "10.0 Nm",
                company = "Mahindra",
                block = "V-Block"
            ),
            OriginalTool(
                toolId = "V-B 15-192",
                toolName = "Bosch Multi-Spindle 15-192",
                model = "Bosch Nexo Multi-Spindle",
                line = "V-Block Line",
                station = "ST-16",
                status = "OK",
                torque = "18.0 Nm",
                company = "Mahindra",
                block = "V-Block"
            ),
            OriginalTool(
                toolId = "T-010",
                toolName = "Bosch Nexo Cordless Nutrunner",
                model = "Bosch Nexo IxB",
                line = "Line-2",
                station = "S-05",
                status = "OK",
                torque = "20.0 Nm",
                company = "Mahindra",
                block = "V-Block"
            )
        )
        val map = LinkedHashMap<String, OriginalTool>()
        defaults.forEach { map[it.toolId.uppercase(Locale.ROOT)] = it }
        toolsList.forEach {
            if (it.toolId.isNotBlank()) {
                map[it.toolId.uppercase(Locale.ROOT)] = it
            }
        }
        map.values.toList()
    }

    // Pre-select tool if navigated from Tool Profile / QR scan / Tool Master
    val initialTool = remember(tool, combinedToolsList) {
        if (tool != null && tool.toolId.isNotBlank()) {
            combinedToolsList.find { it.toolId.equals(tool.toolId, ignoreCase = true) } ?: tool
        } else {
            combinedToolsList.firstOrNull() ?: OriginalTool(
                toolId = "T-025",
                toolName = "Bosch EXACT ION 12-700",
                model = "Bosch EXACT ION 12-700",
                company = "Mahindra",
                block = "V-Block"
            )
        }
    }

    // Dynamic state for currently selected tool
    var selectedTool by remember(tool) { mutableStateOf(initialTool) }

    // Search and Dropdown state
    var searchQuery by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val filteredTools = remember(searchQuery, combinedToolsList) {
        if (searchQuery.isBlank()) {
            combinedToolsList
        } else {
            combinedToolsList.filter {
                it.toolId.contains(searchQuery, ignoreCase = true) ||
                it.toolName.contains(searchQuery, ignoreCase = true) ||
                it.model.contains(searchQuery, ignoreCase = true) ||
                it.serialNumber.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val toolId = selectedTool.toolId.ifEmpty { "T-025" }
    val model = selectedTool.model.ifEmpty { selectedTool.toolName.ifEmpty { "Bosch EXACT ION 12-700" } }
    val company = selectedTool.company.ifEmpty { "Mahindra" }
    val block = selectedTool.block.ifEmpty { "V-Block" }

    // Find latest calibration entry for selected tool from Firebase calibrationList
    val latestCalibration = remember(calibrationList, toolId) {
        calibrationList.filter { it.toolId.equals(toolId, ignoreCase = true) }
            .maxByOrNull { it.calibrationDate }
    }

    val defaultTargetTorque = remember(selectedTool) {
        val numStr = selectedTool.torque.replace(Regex("[^0-9.]"), "")
        numStr.toDoubleOrNull() ?: 12.0
    }

    val defaultTorquePoints = remember(defaultTargetTorque) {
        listOf(
            TorquePoint(defaultTargetTorque * 0.8, defaultTargetTorque * 0.796, 0.40),
            TorquePoint(defaultTargetTorque, defaultTargetTorque * 0.996, 0.40),
            TorquePoint(defaultTargetTorque * 1.2, defaultTargetTorque * 1.198, 0.17)
        )
    }

    val lastCalDate = latestCalibration?.calibrationDate?.ifEmpty { "10 Sep 2026" } ?: "10 Sep 2026"
    val nextCalDate = latestCalibration?.nextCalibrationDate?.ifEmpty { "10 Oct 2026" } ?: "10 Oct 2026"
    val testerId = latestCalibration?.testerId?.ifEmpty { "TQ-001" } ?: "TQ-001"
    val targetTorque = latestCalibration?.targetTorque ?: defaultTargetTorque
    val actualTorque = latestCalibration?.actualTorque ?: (targetTorque * 0.996)
    val errorPercent = latestCalibration?.errorPercent ?: CalibrationUtils.calculateErrorPercent(targetTorque, actualTorque)
    val status = latestCalibration?.status?.ifEmpty { "PASS" } ?: "PASS"
    val torquePoints = if (latestCalibration != null && latestCalibration.torquePoints.isNotEmpty()) {
        latestCalibration.torquePoints
    } else defaultTorquePoints

    var showReCalibrateDialog by remember { mutableStateOf(false) }
    var showCertificateDialog by remember { mutableStateOf(false) }

    val isPass = status.equals("PASS", ignoreCase = true)
    val statusBg = if (isPass) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
    val statusFg = if (isPass) Color(0xFF10B981) else Color(0xFFEF4444)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calibration", fontWeight = FontWeight.Bold) },
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
            // 1. Tool Search & Dropdown Selection Header
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tool Selection / Search",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                isDropdownExpanded = true
                            },
                            placeholder = { Text("Search Tool ID (e.g. T-025 or V-B 15-191)", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "Search Tool", tint = Color(0xFF2563EB))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = Color.Gray)
                                        }
                                    }
                                    IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown menu", tint = Color(0xFF0F172A))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        DropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 260.dp)
                        ) {
                            if (filteredTools.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No tools match '$searchQuery'", color = Color.Gray, fontSize = 13.sp) },
                                    onClick = { isDropdownExpanded = false }
                                )
                            } else {
                                filteredTools.forEach { toolItem ->
                                    val isSelected = toolItem.toolId.equals(selectedTool.toolId, ignoreCase = true)
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = toolItem.toolId,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = if (isSelected) Color(0xFF2563EB) else Color(0xFF0F172A)
                                                    )
                                                    Text(
                                                        text = toolItem.status,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (toolItem.status == "OK") Color(0xFF10B981) else Color(0xFFEF4444)
                                                    )
                                                }
                                                Text(
                                                    text = "${toolItem.model.ifEmpty { toolItem.toolName }} • ${toolItem.line} (${toolItem.station})",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        },
                                        onClick = {
                                            selectedTool = toolItem
                                            searchQuery = toolItem.toolId
                                            isDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Mahindra V-Block Quick Select Chips
                    Text(
                        text = "Mahindra V-Block Tools:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val quickTools = remember(combinedToolsList) {
                            combinedToolsList.take(4)
                        }
                        quickTools.forEach { qTool ->
                            val isSelected = qTool.toolId.equals(selectedTool.toolId, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTool = qTool
                                    searchQuery = qTool.toolId
                                    isDropdownExpanded = false
                                },
                                label = {
                                    Text(
                                        text = qTool.toolId,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFF2563EB),
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFFF1F5F9),
                                    labelColor = Color(0xFF0F172A)
                                )
                            )
                        }
                    }
                }
            }

            // 2. Dynamic Tool Profile Header Card
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
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(6.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = toolId, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(text = model, fontSize = 13.sp, color = Color(0xFF64748B))
                    }

                    Surface(
                        color = statusBg,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = status.uppercase(Locale.ROOT),
                            color = statusFg,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // 3. Dynamic Specs Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpecRow("Last Calibration", lastCalDate)
                    SpecRow("Next Calibration", nextCalDate)
                    SpecRow("Tester ID", testerId)
                    Divider(color = Color(0xFFF1F5F9))
                    SpecRow("Target Torque", String.format(Locale.ROOT, "%.2f Nm", targetTorque))
                    SpecRow("Actual Torque", String.format(Locale.ROOT, "%.2f Nm", actualTorque))
                    SpecRow("Error", String.format(Locale.ROOT, "%.2f %%", errorPercent))
                }
            }

            // 4. Dynamic Torque Points Table Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Torque Verification Points", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Set (Nm)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                        Text("Actual (Nm)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                        Text("Error (%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF475569))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    torquePoints.forEach { pt ->
                        TorqueRow(
                            set = String.format(Locale.ROOT, "%.2f", pt.setPoint),
                            actual = String.format(Locale.ROOT, "%.2f", pt.actualValue),
                            error = String.format(Locale.ROOT, "%.2f", pt.errorPercent)
                        )
                    }
                }
            }

            // View Certificate Button
            OutlinedButton(
                onClick = { showCertificateDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("View Certificate", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            }

            // Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Text("History", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showReCalibrateDialog = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) {
                    Text("Re-Calibrate", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // --- RE-CALIBRATE INPUT DIALOG ---
    if (showReCalibrateDialog) {
        ReCalibrateDialog(
            toolId = toolId,
            initialTargetTorque = targetTorque,
            initialActualTorque = actualTorque,
            initialTesterId = testerId,
            initialTorquePoints = torquePoints,
            onDismiss = { showReCalibrateDialog = false },
            onSave = { newEntry ->
                val entryToSave = newEntry.copy(
                    toolId = toolId,
                    company = company,
                    block = block
                )
                onSaveCalibration(entryToSave)
                showReCalibrateDialog = false
                Toast.makeText(context, "Re-Calibration Saved (${entryToSave.status})", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- CALIBRATION CERTIFICATE DIALOG ---
    if (showCertificateDialog) {
        CalibrationCertificateDialog(
            toolId = toolId,
            model = model,
            company = company,
            block = block,
            testerId = testerId,
            lastCalDate = lastCalDate,
            nextCalDate = nextCalDate,
            targetTorque = targetTorque,
            actualTorque = actualTorque,
            errorPercent = errorPercent,
            status = status,
            torquePoints = torquePoints,
            onDismiss = { showCertificateDialog = false }
        )
    }
}

@Composable
fun TorqueRow(set: String, actual: String, error: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(set, fontSize = 13.sp, color = Color(0xFF0F172A))
        Text(actual, fontSize = 13.sp, color = Color(0xFF0F172A))
        Text("$error %", fontSize = 13.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReCalibrateDialog(
    toolId: String,
    initialTargetTorque: Double,
    initialActualTorque: Double,
    initialTesterId: String,
    initialTorquePoints: List<TorquePoint>,
    onDismiss: () -> Unit,
    onSave: (CalibrationEntry) -> Unit
) {
    var targetText by remember { mutableStateOf(initialTargetTorque.toString()) }
    var actualText by remember { mutableStateOf(initialActualTorque.toString()) }
    var testerText by remember { mutableStateOf(initialTesterId) }
    var toleranceText by remember { mutableStateOf("2.0") }

    // Torque verification points inputs (3 points)
    var pt1Set by remember { mutableStateOf(initialTorquePoints.getOrNull(0)?.setPoint?.toString() ?: "10.0") }
    var pt1Act by remember { mutableStateOf(initialTorquePoints.getOrNull(0)?.actualValue?.toString() ?: "9.96") }

    var pt2Set by remember { mutableStateOf(initialTorquePoints.getOrNull(1)?.setPoint?.toString() ?: "12.0") }
    var pt2Act by remember { mutableStateOf(initialTorquePoints.getOrNull(1)?.actualValue?.toString() ?: "11.95") }

    var pt3Set by remember { mutableStateOf(initialTorquePoints.getOrNull(2)?.setPoint?.toString() ?: "14.0") }
    var pt3Act by remember { mutableStateOf(initialTorquePoints.getOrNull(2)?.actualValue?.toString() ?: "13.98") }

    // Dynamic error and status calculations
    val targetVal = targetText.toDoubleOrNull() ?: 0.0
    val actualVal = actualText.toDoubleOrNull() ?: 0.0
    val toleranceVal = toleranceText.toDoubleOrNull() ?: 2.0

    val overallError = CalibrationUtils.calculateErrorPercent(targetVal, actualVal)

    val pt1Err = CalibrationUtils.calculateErrorPercent(pt1Set.toDoubleOrNull() ?: 0.0, pt1Act.toDoubleOrNull() ?: 0.0)
    val pt2Err = CalibrationUtils.calculateErrorPercent(pt2Set.toDoubleOrNull() ?: 0.0, pt2Act.toDoubleOrNull() ?: 0.0)
    val pt3Err = CalibrationUtils.calculateErrorPercent(pt3Set.toDoubleOrNull() ?: 0.0, pt3Act.toDoubleOrNull() ?: 0.0)

    val evaluatedStatus = CalibrationUtils.evaluateCalibrationStatus(
        overallErrorPercent = overallError,
        pointErrors = listOf(pt1Err, pt2Err, pt3Err),
        maxTolerancePercent = toleranceVal
    )

    val isPass = evaluatedStatus == "PASS"
    val statusBg = if (isPass) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
    val statusFg = if (isPass) Color(0xFF10B981) else Color(0xFFEF4444)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Re-Calibrate Tool ($toolId)", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Surface(color = statusBg, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        evaluatedStatus,
                        color = statusFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it },
                        label = { Text("Target Torque (Nm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { actualText = it },
                        label = { Text("Actual Torque (Nm)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = testerText,
                        onValueChange = { testerText = it },
                        label = { Text("Tester ID") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = toleranceText,
                        onValueChange = { toleranceText = it },
                        label = { Text("Tolerance (%)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Calculated Error: ", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text("${String.format(Locale.ROOT, "%.2f", overallError)} %", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = statusFg)
                    }
                }

                Text("Torque Verification Points (Set vs Actual)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

                // Point 1
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = pt1Set, onValueChange = { pt1Set = it }, label = { Text("P1 Set") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = pt1Act, onValueChange = { pt1Act = it }, label = { Text("P1 Act") }, modifier = Modifier.weight(1f), singleLine = true)
                    Text("${String.format(Locale.ROOT, "%.2f", pt1Err)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pt1Err <= toleranceVal) Color(0xFF10B981) else Color(0xFFEF4444))
                }

                // Point 2
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = pt2Set, onValueChange = { pt2Set = it }, label = { Text("P2 Set") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = pt2Act, onValueChange = { pt2Act = it }, label = { Text("P2 Act") }, modifier = Modifier.weight(1f), singleLine = true)
                    Text("${String.format(Locale.ROOT, "%.2f", pt2Err)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pt2Err <= toleranceVal) Color(0xFF10B981) else Color(0xFFEF4444))
                }

                // Point 3
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = pt3Set, onValueChange = { pt3Set = it }, label = { Text("P3 Set") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(value = pt3Act, onValueChange = { pt3Act = it }, label = { Text("P3 Act") }, modifier = Modifier.weight(1f), singleLine = true)
                    Text("${String.format(Locale.ROOT, "%.2f", pt3Err)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (pt3Err <= toleranceVal) Color(0xFF10B981) else Color(0xFFEF4444))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val calDate = CalibrationUtils.formatCalibrationDate()
                    val nextCalDate = CalibrationUtils.calculateNextCalibrationDate()

                    val torquePointsList = listOf(
                        TorquePoint(pt1Set.toDoubleOrNull() ?: 0.0, pt1Act.toDoubleOrNull() ?: 0.0, pt1Err),
                        TorquePoint(pt2Set.toDoubleOrNull() ?: 0.0, pt2Act.toDoubleOrNull() ?: 0.0, pt2Err),
                        TorquePoint(pt3Set.toDoubleOrNull() ?: 0.0, pt3Act.toDoubleOrNull() ?: 0.0, pt3Err)
                    )

                    val entry = CalibrationEntry(
                        toolId = toolId,
                        testerId = testerText.ifBlank { "TQ-001" },
                        targetTorque = targetVal,
                        actualTorque = actualVal,
                        errorPercent = overallError,
                        status = evaluatedStatus,
                        calibrationDate = calDate,
                        nextCalibrationDate = nextCalDate,
                        torquePoints = torquePointsList
                    )
                    onSave(entry)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
            ) {
                Text("Save Calibration", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun CalibrationCertificateDialog(
    toolId: String,
    model: String,
    company: String,
    block: String,
    testerId: String,
    lastCalDate: String,
    nextCalDate: String,
    targetTorque: Double,
    actualTorque: Double,
    errorPercent: Double,
    status: String,
    torquePoints: List<TorquePoint>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isPass = status.equals("PASS", ignoreCase = true)
    val statusBg = if (isPass) Color(0xFFECFDF5) else Color(0xFFFEF2F2)
    val statusFg = if (isPass) Color(0xFF10B981) else Color(0xFFEF4444)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .border(2.dp, Color(0xFF2563EB), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CALIBRATION CERTIFICATE", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2563EB))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                Text("Certificate No: CERT-CAL-$toolId", fontSize = 11.sp, color = Color(0xFF64748B))

                SpecRow("Tool ID", toolId)
                SpecRow("Model", model)
                SpecRow("Shop / Block", "$company - $block")
                SpecRow("Tester ID", testerId)
                SpecRow("Calibration Date", lastCalDate)
                SpecRow("Next Due Date", nextCalDate)

                Divider(color = Color(0xFFE2E8F0))

                SpecRow("Target Torque", String.format(Locale.ROOT, "%.2f Nm", targetTorque))
                SpecRow("Actual Torque", String.format(Locale.ROOT, "%.2f Nm", actualTorque))
                SpecRow("Overall Error", String.format(Locale.ROOT, "%.2f %%", errorPercent))

                Spacer(modifier = Modifier.height(4.dp))

                Text("Torque Verification Results", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp)).padding(6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Set (Nm)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Actual (Nm)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Error (%)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                torquePoints.forEach { pt ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(String.format(Locale.ROOT, "%.2f", pt.setPoint), fontSize = 11.sp)
                        Text(String.format(Locale.ROOT, "%.2f", pt.actualValue), fontSize = 11.sp)
                        Text("${String.format(Locale.ROOT, "%.2f", pt.errorPercent)}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = statusFg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("FINAL STATUS: ${status.uppercase(Locale.ROOT)}", fontWeight = FontWeight.Bold, color = statusFg, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    exportCalibrationCertificatePdf(
                        context = context,
                        toolId = toolId,
                        model = model,
                        company = company,
                        block = block,
                        testerId = testerId,
                        lastCalDate = lastCalDate,
                        nextCalDate = nextCalDate,
                        targetTorque = targetTorque,
                        actualTorque = actualTorque,
                        errorPercent = errorPercent,
                        status = status,
                        torquePoints = torquePoints
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Certificate", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.Gray)
            }
        }
    )
}

fun exportCalibrationCertificatePdf(
    context: android.content.Context,
    toolId: String,
    model: String,
    company: String,
    block: String,
    testerId: String,
    lastCalDate: String,
    nextCalDate: String,
    targetTorque: Double,
    actualTorque: Double,
    errorPercent: Double,
    status: String,
    torquePoints: List<TorquePoint>
) {
    try {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = android.graphics.Paint().apply {
            textSize = 18f
            isFakeBoldText = true
            color = android.graphics.Color.parseColor("#2563EB")
        }
        val textPaint = android.graphics.Paint().apply {
            textSize = 11f
            color = android.graphics.Color.BLACK
        }
        val boldPaint = android.graphics.Paint().apply {
            textSize = 12f
            isFakeBoldText = true
            color = android.graphics.Color.BLACK
        }

        var y = 50f
        canvas.drawText("TOOLCARE CALIBRATION CERTIFICATE", 40f, y, titlePaint)
        y += 30f
        canvas.drawText("Certificate No: CERT-CAL-$toolId", 40f, y, textPaint)
        y += 25f
        canvas.drawText("Tool ID: $toolId", 40f, y, textPaint)
        canvas.drawText("Model: $model", 300f, y, textPaint)
        y += 20f
        canvas.drawText("Shop / Block: $company - $block", 40f, y, textPaint)
        canvas.drawText("Tester ID: $testerId", 300f, y, textPaint)
        y += 20f
        canvas.drawText("Calibration Date: $lastCalDate", 40f, y, textPaint)
        canvas.drawText("Next Due Date: $nextCalDate", 300f, y, textPaint)
        y += 30f

        canvas.drawText("Target Torque: ${String.format(Locale.ROOT, "%.2f Nm", targetTorque)}", 40f, y, textPaint)
        canvas.drawText("Actual Torque: ${String.format(Locale.ROOT, "%.2f Nm", actualTorque)}", 300f, y, textPaint)
        y += 20f
        canvas.drawText("Overall Error: ${String.format(Locale.ROOT, "%.2f %%", errorPercent)}", 40f, y, textPaint)
        canvas.drawText("Status: ${status.uppercase(Locale.ROOT)}", 300f, y, boldPaint)
        y += 35f

        canvas.drawText("Torque Verification Results:", 40f, y, boldPaint)
        y += 20f
        canvas.drawText("SetPoint (Nm)   |   Actual (Nm)   |   Error (%)", 40f, y, textPaint)
        y += 15f
        torquePoints.forEach { pt ->
            canvas.drawText("${String.format(Locale.ROOT, "%.2f", pt.setPoint)} Nm      |   ${String.format(Locale.ROOT, "%.2f", pt.actualValue)} Nm      |   ${String.format(Locale.ROOT, "%.2f", pt.errorPercent)}%", 40f, y, textPaint)
            y += 18f
        }

        pdfDocument.finishPage(page)

        val fileName = "CERT_CAL_${toolId.replace(" ", "_")}.pdf"
        val file = java.io.File(context.cacheDir, fileName)
        val out = java.io.FileOutputStream(file)
        pdfDocument.writeTo(out)
        out.close()
        pdfDocument.close()

        val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = android.content.Intent.createChooser(shareIntent, "Export Certificate ($toolId)").apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
        Toast.makeText(context, "Certificate Downloaded Successfully: CERT-CAL-$toolId.pdf", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        android.util.Log.e("ExportCert", "Certificate PDF Export Failed", e)
        Toast.makeText(context, "Export Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}
