package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolMasterScreen(
    tool: OriginalTool? = null,
    originalToolList: List<OriginalTool> = emptyList(),
    breakdownList: List<BreakdownEntry> = emptyList(),
    pmEntries: List<PmEntry> = emptyList(),
    calibrationList: List<CalibrationEntry> = emptyList(),
    userRole: String = "technician",
    firebaseManager: FirebaseManager? = null,
    onNavigate: (String) -> Unit = {},
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current

    // Combined master list of tools
    val combinedToolsList = remember(originalToolList) {
        val defaults = listOf(
            OriginalTool(toolId = "T-025", toolName = "Bosch EXACT ION 12-700", model = "Bosch EXACT ION 12-700", line = "Line-1", station = "S-12", status = "OK", company = "Mahindra", block = "V-Block", installDate = "10 Jan 2024"),
            OriginalTool(toolId = "V-B 15-191", toolName = "Bosch Torque Tool 15-191", model = "Bosch Angle EXACT ION 15-191", line = "V-Block Line", station = "ST-15", status = "OK", company = "Mahindra", block = "V-Block", installDate = "15 Mar 2024"),
            OriginalTool(toolId = "T-026", toolName = "Bosch Cordless Nutrunner", model = "Bosch Cordless Nutrunner", line = "Line-1", station = "S-14", status = "OK", company = "Mahindra", block = "V-Block", installDate = "22 Feb 2024"),
            OriginalTool(toolId = "V-B 15-192", toolName = "Bosch Multi-Spindle 15-192", model = "Bosch Nexo Multi-Spindle", line = "V-Block Line", station = "ST-16", status = "OK", company = "Mahindra", block = "V-Block", installDate = "05 May 2024"),
            OriginalTool(toolId = "T-010", toolName = "Bosch Nexo Cordless Nutrunner", model = "Bosch Nexo IxB", line = "Line-2", station = "S-05", status = "OK", company = "Mahindra", block = "V-Block", installDate = "18 Jun 2024")
        )
        val map = LinkedHashMap<String, OriginalTool>()
        defaults.forEach { map[it.toolId.uppercase(Locale.ROOT)] = it }
        originalToolList.forEach {
            if (it.toolId.isNotBlank()) {
                map[it.toolId.uppercase(Locale.ROOT)] = it
            }
        }
        map.values.toList()
    }

    // Dynamic initial selected tool
    val initialTool = remember(tool, combinedToolsList) {
        if (tool != null && tool.toolId.isNotBlank()) {
            combinedToolsList.find { it.toolId.equals(tool.toolId, ignoreCase = true) } ?: tool
        } else {
            combinedToolsList.firstOrNull() ?: OriginalTool(toolId = "T-025", toolName = "Bosch EXACT ION 12-700", model = "Bosch EXACT ION 12-700")
        }
    }

    var selectedTool by remember(tool, originalToolList) {
        val match = if (tool != null && tool.toolId.isNotBlank()) {
            originalToolList.find { it.toolId.equals(tool.toolId, ignoreCase = true) } ?: tool
        } else {
            combinedToolsList.firstOrNull() ?: initialTool
        }
        mutableStateOf(match)
    }

    // Search and Dropdown State
    var searchQuery by remember { mutableStateOf("") }
    var isSearchMenuExpanded by remember { mutableStateOf(false) }

    val filteredTools = remember(searchQuery, combinedToolsList) {
        if (searchQuery.isBlank()) combinedToolsList
        else combinedToolsList.filter {
            it.toolId.contains(searchQuery, ignoreCase = true) ||
            it.toolName.contains(searchQuery, ignoreCase = true) ||
            it.model.contains(searchQuery, ignoreCase = true)
        }
    }

    // Modal Dialog States
    var showParameterDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDocumentsDialog by remember { mutableStateOf(false) }
    var showPhotosDialog by remember { mutableStateOf(false) }

    // Admin Upload Dialog States
    var showAdminUploadDocDialog by remember { mutableStateOf(false) }
    var showAdminUploadPhotoDialog by remember { mutableStateOf(false) }

    val toolId = selectedTool.toolId.ifEmpty { "T-025" }
    val model = selectedTool.model.ifEmpty { selectedTool.toolName.ifEmpty { "Bosch EXACT ION 12-700" } }
    val serialNo = selectedTool.serialNumber.ifEmpty { "SN-$toolId" }
    val station = selectedTool.station.ifEmpty { "S-12" }
    val line = selectedTool.line.ifEmpty { "Line-1" }
    val status = selectedTool.status.ifEmpty { "OK" }
    val installDate = selectedTool.installDate.ifEmpty { "10 Jan 2024" }

    // Brand scope strictly restricted to Bosch across all records
    val makeName = "Bosch"

    val toolType = when {
        model.contains("Spindle", ignoreCase = true) -> "Multi-Spindle"
        model.contains("Nutrunner", ignoreCase = true) -> "Electric Nutrunner"
        else -> "Cordless Shut-off Clutch"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tool Master ($toolId)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Home, contentDescription = "Back")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { isSearchMenuExpanded = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search Tool", tint = Color.White)
                        }

                        DropdownMenu(
                            expanded = isSearchMenuExpanded,
                            onDismissRequest = { isSearchMenuExpanded = false },
                            modifier = Modifier.width(280.dp).heightIn(max = 320.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search Tool ID...", fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                singleLine = true
                            )

                            if (filteredTools.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No tools found", color = Color.Gray, fontSize = 13.sp) },
                                    onClick = { isSearchMenuExpanded = false }
                                )
                            } else {
                                filteredTools.forEach { t ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(t.toolId, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("${t.model.ifEmpty { t.toolName }} (${t.station})", fontSize = 11.sp, color = Color.Gray)
                                            }
                                        },
                                        onClick = {
                                            selectedTool = t
                                            isSearchMenuExpanded = false
                                            searchQuery = ""
                                        }
                                    )
                                }
                            }
                        }
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
            // Header Profile Card
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
                            .size(72.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(6.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = if (status == "OK") Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = status.uppercase(Locale.ROOT),
                                color = if (status == "OK") Color(0xFF10B981) else Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = toolId, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(text = model, fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(text = "Serial No: $serialNo", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            // Real-Time Tool Specifications Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Tool Specifications", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Divider(color = Color(0xFFF1F5F9))

                    val fitmentName = selectedTool.fitment.ifEmpty { selectedTool.application.ifEmpty { "Engine Block Fitment" } }
                    MasterSpecRow("Fitment / Assembly", fitmentName)
                    MasterSpecRow("Tool Type", toolType)
                    MasterSpecRow("Make / Manufacturer", makeName)
                    MasterSpecRow("Model Name", model)
                    MasterSpecRow("Station", station)
                    MasterSpecRow("Line", line)
                    MasterSpecRow("Department", "Assembly (M&M)")
                    MasterSpecRow("Purchase / Install Date", installDate)
                    MasterSpecRow("Warranty Status", "Active (In Warranty)")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Status", fontSize = 13.sp, color = Color(0xFF64748B))
                        Surface(color = if (status == "OK") Color(0xFFECFDF5) else Color(0xFFFEF2F2), shape = RoundedCornerShape(12.dp)) {
                            Text(status, color = if (status == "OK") Color(0xFF10B981) else Color(0xFFDC2626), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }

            // Tool Location Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tool Location & Line Allocation", fontSize = 13.sp, color = Color(0xFF64748B))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = "Pin", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "$station ($line)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                }
            }

            // Functional Bottom Quick Action Buttons
            Text("Quick Tool Actions", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ActionIconBtn("Parameter", Icons.Default.Settings) { showParameterDialog = true }
                ActionIconBtn("History", Icons.Default.Refresh) { showHistoryDialog = true }
                ActionIconBtn("Documents", Icons.Default.Edit) { showDocumentsDialog = true }
                ActionIconBtn("Photos", Icons.Default.Person) { showPhotosDialog = true }
            }
        }
    }

    // 1. Parameter Dialog with Full Admin CRUD Editing
    if (showParameterDialog) {
        var editName by remember(selectedTool) { mutableStateOf(selectedTool.toolName.ifEmpty { "Bosch EXACT ION 12-700" }) }
        var editModel by remember(selectedTool) { mutableStateOf(selectedTool.model.ifEmpty { "Bosch EXACT ION 12-700" }) }
        var editLine by remember(selectedTool) { mutableStateOf(selectedTool.line.ifEmpty { "Line-1" }) }
        var editStation by remember(selectedTool) { mutableStateOf(selectedTool.station.ifEmpty { "S-12" }) }
        var editSerial by remember(selectedTool) { mutableStateOf(selectedTool.serialNumber.ifEmpty { "SN-$toolId" }) }
        var editTorque by remember(selectedTool) { mutableStateOf(selectedTool.torque.ifEmpty { "12.00 Nm" }) }
        var editRpm by remember(selectedTool) { mutableStateOf(selectedTool.rpm.ifEmpty { "700 RPM" }) }
        var editApp by remember(selectedTool) { mutableStateOf(selectedTool.application.ifEmpty { "Engine Block Bolting" }) }
        var editStatus by remember(selectedTool) { mutableStateOf(selectedTool.status.ifEmpty { "OK" }) }
        var editInstallDate by remember(selectedTool) { mutableStateOf(selectedTool.installDate.ifEmpty { "10 Jan 2024" }) }
        var isEditingParams by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showParameterDialog = false },
            title = { Text("Tool Parameters ($toolId)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isEditingParams && userRole == "admin") {
                        Text("Admin Editing Parameters:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Tool Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editModel,
                            onValueChange = { editModel = it },
                            label = { Text("Model Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editLine,
                                onValueChange = { editLine = it },
                                label = { Text("Line") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editStation,
                                onValueChange = { editStation = it },
                                label = { Text("Station") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = editSerial,
                            onValueChange = { editSerial = it },
                            label = { Text("Serial Number") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editTorque,
                                onValueChange = { editTorque = it },
                                label = { Text("Target Torque") },
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editRpm,
                                onValueChange = { editRpm = it },
                                label = { Text("RPM Rating") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = editApp,
                            onValueChange = { editApp = it },
                            label = { Text("Application / Fitment") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editStatus,
                            onValueChange = { editStatus = it },
                            label = { Text("Status (OK / Breakdown)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editInstallDate,
                            onValueChange = { editInstallDate = it },
                            label = { Text("Install Date") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        MasterSpecRow("Tool Name", selectedTool.toolName.ifEmpty { "Bosch EXACT ION 12-700" })
                        MasterSpecRow("Manufacturer", "Bosch")
                        MasterSpecRow("Target Torque", selectedTool.torque.ifEmpty { "12.00 Nm" })
                        MasterSpecRow("Max Torque Limit", "14.50 Nm")
                        MasterSpecRow("Min Torque Limit", "10.00 Nm")
                        MasterSpecRow("Rpm Rating", selectedTool.rpm.ifEmpty { "700 RPM" })
                        MasterSpecRow("Application", selectedTool.application.ifEmpty { "Engine Block Bolting" })
                        MasterSpecRow("Controller Type", "Bosch Tightening System")
                        MasterSpecRow("Shut-off Strategy", "Clutch / Transducer Shutoff")
                    }
                }
            },
            confirmButton = {
                if (userRole == "admin") {
                    if (isEditingParams) {
                        Button(
                            onClick = {
                                val updated = selectedTool.copy(
                                    toolName = editName,
                                    model = editModel,
                                    line = editLine,
                                    station = editStation,
                                    serialNumber = editSerial,
                                    torque = editTorque,
                                    rpm = editRpm,
                                    application = editApp,
                                    fitment = editApp,
                                    status = editStatus,
                                    installDate = editInstallDate
                                )
                                selectedTool = updated
                                firebaseManager?.saveOriginalTool(updated)
                                isEditingParams = false
                                showParameterDialog = false
                                Toast.makeText(context, "Tool Parameters Updated in Firebase", Toast.LENGTH_SHORT).show()
                            }
                        ) { Text("Save Parameters") }
                    } else {
                        Button(onClick = { isEditingParams = true }) { Text("Edit Parameters") }
                    }
                } else {
                    Button(onClick = { showParameterDialog = false }) { Text("Close") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showParameterDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 2. Tool History Dialog strictly bound to toolId
    if (showHistoryDialog) {
        val toolBreakdowns = breakdownList.filter { it.originalToolId.equals(toolId, ignoreCase = true) }
        val toolPMs = pmEntries.filter { it.toolId.equals(toolId, ignoreCase = true) }
        val toolCals = calibrationList.filter { it.toolId.equals(toolId, ignoreCase = true) }

        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = { Text("Tool History Logs ($toolId)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Breakdown History (${toolBreakdowns.size})", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    if (toolBreakdowns.isEmpty()) {
                        Text("No recorded breakdowns for this tool.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        toolBreakdowns.forEach { bd ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Problem: ${bd.problem}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Date: ${bd.timestamp} | Status: ${bd.status}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("PM History (${toolPMs.size})", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    if (toolPMs.isEmpty()) {
                        Text("No PM records found for this tool.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        toolPMs.forEach { pm ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("PM Task: ${pm.pmId}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Scheduled: ${pm.scheduledDate} | Status: ${pm.status}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Calibration History (${toolCals.size})", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    if (toolCals.isEmpty()) {
                        Text("No calibration records found for this tool.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        toolCals.forEach { cal ->
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Date: ${cal.calibrationDate} | Status: ${cal.status}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Actual Torque: ${cal.actualTorque} Nm | Tester: ${cal.testerId}", fontSize = 11.sp, color = Color.Gray)
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

    // 3. Documents Dialog with Admin Upload Option
    if (showDocumentsDialog) {
        val hasManual = selectedTool.manualName.isNotBlank() || selectedTool.documentUrl.isNotBlank()
        val hasCert = selectedTool.certName.isNotBlank()

        AlertDialog(
            onDismissRequest = { showDocumentsDialog = false },
            title = { Text("Tool Documents ($toolId)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasManual) {
                        DocRow(selectedTool.manualName.ifEmpty { "User & Service Manual (.pdf)" }, "Available") {
                            Toast.makeText(context, "Opening User Manual PDF...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (hasCert) {
                        DocRow(selectedTool.certName.ifEmpty { "Calibration Certificate (.pdf)" }, "Available") {
                            Toast.makeText(context, "Opening Calibration Certificate...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    
                    if (!hasManual && !hasCert) {
                        DocRow("User & Service Manual (.pdf)", "Available") { Toast.makeText(context, "Opening User Manual PDF...", Toast.LENGTH_SHORT).show() }
                        DocRow("Calibration Certificate (.pdf)", "Available") { Toast.makeText(context, "Opening Calibration Certificate...", Toast.LENGTH_SHORT).show() }
                        DocRow("Maintenance SOP Sheet (.pdf)", "Available") { Toast.makeText(context, "Opening SOP Document...", Toast.LENGTH_SHORT).show() }
                    }

                    if (userRole.isNotBlank() && userRole.lowercase() != "viewer") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Divider()
                        Button(
                            onClick = { showAdminUploadDocDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Upload / Attach Technical Document")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showDocumentsDialog = false }) { Text("Close") }
            }
        )
    }

    // Admin Upload Document Dialog (Company & Block Scoped B2)
    if (showAdminUploadDocDialog) {
        var docNameInput by remember { mutableStateOf("") }
        var docUrlInput by remember { mutableStateOf("") }
        var selectedFileUri by remember { mutableStateOf<android.net.Uri?>(null) }
        var selectedFileName by remember { mutableStateOf("") }
        var selectedFileSizeFormatted by remember { mutableStateOf("") }
        var selectedSizeBytes by remember { mutableLongStateOf(0L) }
        var selectedMimeType by remember { mutableStateOf("application/pdf") }

        val docPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri: android.net.Uri? ->
            uri?.let { fileUri ->
                val helper = B2StorageHelper(context)
                when (val validation = helper.validatePreflightUpload(fileUri)) {
                    is B2UploadValidationResult.Valid -> {
                        selectedFileUri = fileUri
                        selectedFileName = validation.filename
                        selectedFileSizeFormatted = validation.formattedSize
                        selectedSizeBytes = validation.sizeBytes
                        selectedMimeType = validation.contentType
                        if (docNameInput.isBlank()) {
                            docNameInput = validation.filename.substringBeforeLast(".")
                        }
                        Toast.makeText(context, "Selected ${validation.filename} (${validation.formattedSize})", Toast.LENGTH_SHORT).show()
                    }
                    is B2UploadValidationResult.Error -> {
                        Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAdminUploadDocDialog = false },
            title = { Text("Attach Technical Document to $toolId", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Select a local file or paste a Google Drive link:", fontSize = 12.sp, color = Color.Gray)

                    Button(
                        onClick = { docPickerLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Local Document / PDF", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (selectedFileUri != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(selectedFileName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    Text("Size: $selectedFileSizeFormatted • Type: $selectedMimeType", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = docNameInput,
                        onValueChange = { docNameInput = it },
                        label = { Text("Document Title *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = docUrlInput,
                        onValueChange = { docUrlInput = it },
                        label = { Text("Google Drive URL (Optional Fallback)") },
                        placeholder = { Text("https://drive.google.com/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (docNameInput.isNotBlank()) {
                            val targetComp = selectedTool.company.ifEmpty { "Mahindra" }
                            val targetBlk = selectedTool.block.ifEmpty { "V-Block" }
                            val isB2 = selectedFileUri != null
                            val objKey = if (isB2) {
                                B2StorageConfig.buildObjectKey(targetComp, targetBlk, "documents", toolId, selectedFileName)
                            } else ""

                            val techDoc = TechnicalDocument(
                                toolId = toolId,
                                title = docNameInput.trim(),
                                category = "Manuals",
                                description = "Admin technical document for $toolId",
                                fileType = if (isB2) (if (selectedMimeType.contains("pdf")) "PDF" else "DOC") else "PDF",
                                fileSize = if (isB2) selectedFileSizeFormatted else "2.4 MB",
                                url = if (isB2) selectedFileUri.toString() else docUrlInput.ifEmpty { "https://drive.google.com" },
                                storageProvider = if (isB2) "BACKBLAZE_B2" else "GOOGLE_DRIVE",
                                objectKey = objKey,
                                originalFileName = selectedFileName,
                                contentType = selectedMimeType,
                                sizeBytes = selectedSizeBytes,
                                companyId = targetComp,
                                blockId = targetBlk
                            )

                            firebaseManager?.saveTechnicalDocument(techDoc)

                            val updatedTool = selectedTool.copy(manualName = docNameInput, documentUrl = techDoc.url)
                            selectedTool = updatedTool
                            firebaseManager?.saveOriginalTool(updatedTool)

                            showAdminUploadDocDialog = false
                            Toast.makeText(context, "Document '$docNameInput' Attached & Saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter a document title.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) { Text("Save Document") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminUploadDocDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 4. Photos Dialog with Admin Upload Option
    if (showPhotosDialog) {
        AlertDialog(
            onDismissRequest = { showPhotosDialog = false },
            title = { Text("Tool Photos ($toolId)", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_drill_tool),
                        contentDescription = "Tool Photo",
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(12.dp)
                    )
                    Text("Model: $model", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Serial No: $serialNo", fontSize = 12.sp, color = Color.Gray)

                    if (selectedTool.photoUrl.isNotBlank()) {
                        Text("Photo Attachment: ${selectedTool.photoUrl}", fontSize = 11.sp, color = Color(0xFF2563EB))
                    }

                    if (userRole == "admin") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showAdminUploadPhotoDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Admin: Upload / Attach Photo")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showPhotosDialog = false }) { Text("Close") }
            }
        )
    }

    // Admin Upload Photo Dialog (Company & Block Scoped B2)
    if (showAdminUploadPhotoDialog) {
        var photoUrlInput by remember { mutableStateOf("") }
        var selectedPhotoUri by remember { mutableStateOf<android.net.Uri?>(null) }
        var selectedPhotoName by remember { mutableStateOf("") }
        var selectedPhotoSizeFormatted by remember { mutableStateOf("") }
        var selectedPhotoSizeBytes by remember { mutableLongStateOf(0L) }
        var selectedPhotoMimeType by remember { mutableStateOf("image/jpeg") }

        val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
        ) { uri: android.net.Uri? ->
            uri?.let { fileUri ->
                val helper = B2StorageHelper(context)
                when (val validation = helper.validatePreflightUpload(fileUri)) {
                    is B2UploadValidationResult.Valid -> {
                        selectedPhotoUri = fileUri
                        selectedPhotoName = validation.filename
                        selectedPhotoSizeFormatted = validation.formattedSize
                        selectedPhotoSizeBytes = validation.sizeBytes
                        selectedPhotoMimeType = validation.contentType
                        Toast.makeText(context, "Selected Photo ${validation.filename} (${validation.formattedSize})", Toast.LENGTH_SHORT).show()
                    }
                    is B2UploadValidationResult.Error -> {
                        Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAdminUploadPhotoDialog = false },
            title = { Text("Attach Photo to $toolId", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Select a photo from device or paste image URL:", fontSize = 12.sp, color = Color.Gray)

                    Button(
                        onClick = { photoPickerLauncher.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Choose Local Photo / Image", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (selectedPhotoUri != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(selectedPhotoName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                                    Text("Size: $selectedPhotoSizeFormatted", fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = photoUrlInput,
                        onValueChange = { photoUrlInput = it },
                        label = { Text("Photo URL / Reference Path (Optional Fallback)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalPhotoRef = if (selectedPhotoUri != null) selectedPhotoUri.toString() else photoUrlInput
                        if (finalPhotoRef.isNotBlank()) {
                            val targetComp = selectedTool.company.ifEmpty { "Mahindra" }
                            val targetBlk = selectedTool.block.ifEmpty { "V-Block" }
                            val isB2 = selectedPhotoUri != null
                            val objKey = if (isB2) {
                                B2StorageConfig.buildObjectKey(targetComp, targetBlk, "photos", toolId, selectedPhotoName)
                            } else ""

                            if (isB2) {
                                val techPhotoDoc = TechnicalDocument(
                                    toolId = toolId,
                                    title = "Tool Photo - $toolId",
                                    category = "Diagrams",
                                    description = "Admin tool photo attachment for $toolId",
                                    fileType = "IMAGE",
                                    fileSize = selectedPhotoSizeFormatted,
                                    url = selectedPhotoUri.toString(),
                                    storageProvider = "BACKBLAZE_B2",
                                    objectKey = objKey,
                                    originalFileName = selectedPhotoName,
                                    contentType = selectedPhotoMimeType,
                                    sizeBytes = selectedPhotoSizeBytes,
                                    companyId = targetComp,
                                    blockId = targetBlk
                                )
                                firebaseManager?.saveTechnicalDocument(techPhotoDoc)
                            }

                            val updated = selectedTool.copy(photoUrl = finalPhotoRef)
                            selectedTool = updated
                            firebaseManager?.saveOriginalTool(updated)

                            showAdminUploadPhotoDialog = false
                            Toast.makeText(context, "Photo Attached & Saved!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please choose a photo or enter a URL.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A))
                ) { Text("Save Photo") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminUploadPhotoDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MasterSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFF64748B))
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
    }
}

@Composable
fun DocRow(title: String, status: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2563EB))
        Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(6.dp)) {
            Text(status, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

@Composable
fun ActionIconBtn(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color(0xFF0F172A))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
    }
}
