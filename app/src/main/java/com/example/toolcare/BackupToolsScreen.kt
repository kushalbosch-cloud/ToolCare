package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupToolsScreen(
    backupToolList: List<BackupTool>,
    firebaseManager: FirebaseManager? = null,
    userRole: String = "technician",
    userName: String = "Technician",
    onAddBackupTool: (BackupTool) -> Unit,
    onUpdateBackupTool: (BackupTool) -> Unit = { onAddBackupTool(it) },
    onDeleteBackupTool: ((BackupTool) -> Unit)? = null,
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current

    BackHandler { onBackClick() }

    val canEdit = userRole.lowercase(Locale.ROOT) in listOf("admin", "super_admin", "technician")

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTool by remember { mutableStateOf<BackupTool?>(null) }
    var deletingTool by remember { mutableStateOf<BackupTool?>(null) }
    var verifyingNotOkTool by remember { mutableStateOf<BackupTool?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTabFilter by remember { mutableStateOf("All") }

    val totalCount = backupToolList.size
    val availableCount = backupToolList.count { ToolStatus.isAvailableBackup(it.status) }
    val usedCount = backupToolList.count { it.status.equals("In Use", ignoreCase = true) }
    val missingCount = backupToolList.count { it.status.contains("Missing", ignoreCase = true) || it.status.equals("Under Repair", ignoreCase = true) }
    val holdCount = backupToolList.count { it.status.contains("Hold", ignoreCase = true) || !it.isVerified }

    val filteredTools = remember(backupToolList, searchQuery, selectedTabFilter) {
        backupToolList.filter { tool ->
            val matchesSearch = searchQuery.isBlank() ||
                    tool.toolId.contains(searchQuery, ignoreCase = true) ||
                    tool.model.contains(searchQuery, ignoreCase = true) ||
                    tool.serialNumber.contains(searchQuery, ignoreCase = true) ||
                    tool.line.contains(searchQuery, ignoreCase = true) ||
                    tool.station.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedTabFilter) {
                "Available" -> ToolStatus.isAvailableBackup(tool.status)
                "In Use" -> tool.status.equals("In Use", ignoreCase = true)
                "Missing" -> tool.status.contains("Missing", ignoreCase = true) || tool.status.equals("Under Repair", ignoreCase = true)
                "Hold" -> tool.status.contains("Hold", ignoreCase = true) || !tool.isVerified
                else -> true
            }

            matchesSearch && matchesTab
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.backup_tools_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
            if (canEdit) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Backup Tool")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackupKpiCard("TOTAL", "$totalCount", Icons.Default.Build, Color(0xFF2563EB), Color(0xFFEFF6FF), Modifier.weight(1f)) {
                    selectedTabFilter = "All"
                }
                BackupKpiCard("AVAILABLE", "$availableCount", Icons.Default.CheckCircle, Color(0xFF10B981), Color(0xFFECFDF5), Modifier.weight(1f)) {
                    selectedTabFilter = "Available"
                }
                BackupKpiCard("IN USE", "$usedCount", Icons.Default.Refresh, Color(0xFFD97706), Color(0xFFFFFBEB), Modifier.weight(1f)) {
                    selectedTabFilter = "In Use"
                }
                BackupKpiCard("MISSING", "$missingCount", Icons.Default.Warning, Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.weight(1f)) {
                    selectedTabFilter = "Missing"
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search by Tool ID, Model, SN, Station...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedTabFilter == "All",
                    onClick = { selectedTabFilter = "All" },
                    label = { Text("ALL ($totalCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedTabFilter == "Available",
                    onClick = { selectedTabFilter = "Available" },
                    label = { Text("AVAILABLE ($availableCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedTabFilter == "In Use",
                    onClick = { selectedTabFilter = "In Use" },
                    label = { Text("IN USE ($usedCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedTabFilter == "Missing",
                    onClick = { selectedTabFilter = "Missing" },
                    label = { Text("MISSING ($missingCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
                FilterChip(
                    selected = selectedTabFilter == "Hold",
                    onClick = { selectedTabFilter = "Hold" },
                    label = { Text("HOLD ($holdCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (selectedTabFilter == "Hold") Color.Unspecified else Color(0xFFD97706)) }
                )
            }

            if (filteredTools.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = stringResource(R.string.no_backup_tools), color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        filteredTools,
                        key = { tool -> if (tool.toolId.isNotEmpty()) tool.toolId else "backup_${tool.hashCode()}" }
                    ) { tool ->
                        BackupToolCardItem(
                            tool = tool,
                            canEdit = canEdit,
                            onLongClick = { if (canEdit) editingTool = it },
                            onEditClick = { if (canEdit) editingTool = it },
                            onDeleteClick = { if (canEdit) deletingTool = it },
                            onOkClick = {
                                if (firebaseManager != null) {
                                    firebaseManager.verifyBackupTool(it, isOk = true, verifiedBy = userName)
                                    Toast.makeText(context, "✓ Tool ${it.toolId} Verified (OK) by $userName!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val updated = it.copy(status = "Available", lastVerifiedTimestamp = System.currentTimeMillis())
                                    onUpdateBackupTool(updated)
                                    Toast.makeText(context, "✓ Tool ${it.toolId} Verified!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onNotOkClick = { verifyingNotOkTool = it }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddBackupToolDialog(
            onDismiss = { showAddDialog = false },
            onSave = { newTool ->
                onAddBackupTool(newTool.copy(make = "Bosch"))
                showAddDialog = false
                Toast.makeText(context, "Backup Tool ${newTool.toolId} Added", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (editingTool != null) {
        EditBackupToolDialog(
            tool = editingTool!!,
            onDismiss = { editingTool = null },
            onSave = { updated ->
                onUpdateBackupTool(updated.copy(make = "Bosch"))
                editingTool = null
                Toast.makeText(context, "Backup Tool ${updated.toolId} Updated Successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (deletingTool != null) {
        val toolToDelete = deletingTool!!
        AlertDialog(
            onDismissRequest = { deletingTool = null },
            title = { Text("Delete Backup Tool (${toolToDelete.toolId})", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete backup tool '${toolToDelete.toolId}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBackupTool?.invoke(toolToDelete)
                        deletingTool = null
                        Toast.makeText(context, "Backup Tool ${toolToDelete.toolId} Deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTool = null }) { Text("Cancel") }
            }
        )
    }

    if (verifyingNotOkTool != null) {
        NotOkReasonDialog(
            tool = verifyingNotOkTool!!,
            onDismiss = { verifyingNotOkTool = null },
            onConfirmReason = { chosenReason ->
                val targetTool = verifyingNotOkTool!!
                if (firebaseManager != null) {
                    firebaseManager.verifyBackupTool(targetTool, isOk = false, verifiedBy = userName)
                } else {
                    val updated = targetTool.copy(status = chosenReason)
                    onUpdateBackupTool(updated)
                }
                verifyingNotOkTool = null
                Toast.makeText(context, "Tool ${targetTool.toolId} marked NOT OK ($chosenReason)", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun BackupToolCardItem(
    tool: BackupTool,
    canEdit: Boolean = true,
    onLongClick: (BackupTool) -> Unit,
    onEditClick: (BackupTool) -> Unit,
    onDeleteClick: (BackupTool) -> Unit,
    onOkClick: (BackupTool) -> Unit,
    onNotOkClick: (BackupTool) -> Unit
) {
    val isAvailable = ToolStatus.isAvailableBackup(tool.status)
    val isInUse = tool.status.equals("In Use", ignoreCase = true)

    val statusColor = when {
        isAvailable -> Color(0xFF10B981)
        isInUse -> Color(0xFFD97706)
        else -> Color(0xFFDC2626)
    }

    val statusBg = when {
        isAvailable -> Color(0xFFECFDF5)
        isInUse -> Color(0xFFFFFBEB)
        else -> Color(0xFFFEF2F2)
    }

    val isVerified = tool.isVerified
    val isNotOk = tool.status.contains("NOT OK", ignoreCase = true) || tool.status.contains("Breakdown", ignoreCase = true) || tool.status.contains("Repair", ignoreCase = true) || tool.status.contains("Missing", ignoreCase = true)

    val cardBgColor = when {
        isNotOk -> Color(0xFFFEF2F2)
        isVerified -> Color(0xFFF0FDF4)
        else -> Color.White
    }

    val cardBorder = when {
        isNotOk -> BorderStroke(1.5.dp, Color(0xFFDC2626))
        isVerified -> BorderStroke(1.5.dp, Color(0xFF10B981))
        else -> BorderStroke(1.dp, Color(0xFFE2E8F0))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(tool) {
                detectTapGestures(
                    onLongPress = { if (canEdit) onLongClick(tool) }
                )
            },
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isVerified || isNotOk) 4.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isVerified) {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "✓ Verified Today (24h Lock Active)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D)
                        )
                        Text(
                            text = "Next Check in ${tool.hoursRemainingUntilReset}h",
                            fontSize = 10.sp,
                            color = Color(0xFF166534)
                        )
                    }
                }
            } else if (isNotOk) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ NOT OK / Maintenance Required — Auto Breakdown Logged",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFB91C1C),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tool.toolId,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Bosch",
                                color = Color(0xFF2563EB),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = tool.model.ifEmpty { "Bosch Cordless Tool" },
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = tool.status,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp
                        )
                    }

                    if (canEdit) {
                        IconButton(onClick = { onEditClick(tool) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Tool", tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onDeleteClick(tool) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Tool", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Location: ${tool.location.ifEmpty { "Tool Room" }}", fontSize = 12.sp, color = Color(0xFF475569))
                Text(text = "S/N: ${tool.serialNumber.ifEmpty { "N/A" }}", fontSize = 12.sp, color = Color(0xFF64748B))
                Text(text = "${tool.line} (${tool.station})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Verification Check:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val okBgColor = when {
                        tool.isVerified -> Color(0xFF059669)
                        isAvailable -> Color(0xFF10B981)
                        else -> Color(0xFFECFDF5)
                    }
                    val okTxtColor = when {
                        tool.isVerified || isAvailable -> Color.White
                        else -> Color(0xFF047857)
                    }
                    val okText = when {
                        tool.isVerified -> "✓ Verified Today"
                        isAvailable -> "OK (Available)"
                        else -> "OK (Verify)"
                    }

                    Button(
                        onClick = { onOkClick(tool) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = okBgColor,
                            contentColor = okTxtColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(okText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onNotOkClick(tool) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isAvailable) Color(0xFFDC2626) else Color(0xFFFEF2F2),
                            contentColor = if (!isAvailable) Color.White else Color(0xFFB91C1C)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("NOT OK", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tool.isVerified) {
                    Surface(color = Color(0xFFECFDF5), shape = RoundedCornerShape(4.dp)) {
                        Text(
                            text = "🔒 Locked for 24h (${tool.hoursRemainingUntilReset}h left)",
                            fontSize = 10.sp,
                            color = Color(0xFF047857),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    Text(
                        text = "⚡ Requires daily verification check",
                        fontSize = 10.sp,
                        color = Color(0xFFD97706),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (canEdit) {
                    Text(
                        text = "💡 Press & hold card to edit details",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupKpiCard(
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

@Composable
fun AddBackupToolDialog(
    onDismiss: () -> Unit,
    onSave: (BackupTool) -> Unit
) {
    var toolId by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var line by remember { mutableStateOf("Assembly-2") }
    var station by remember { mutableStateOf("ST-14") }
    var location by remember { mutableStateOf("Tool Room") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Backup Tool", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = toolId, onValueChange = { toolId = it }, label = { Text("Backup Tool ID * (e.g. BKT-001)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = "Bosch", onValueChange = {}, enabled = false, label = { Text("Make (Restricted Brand Scope)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model Name * (e.g. EXACT ION 12-700)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serialNumber, onValueChange = { serialNumber = it }, label = { Text("Serial Number") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = line, onValueChange = { line = it }, label = { Text("Line") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text("Station") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Store Location") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (toolId.isNotBlank() && model.isNotBlank()) {
                        onSave(
                            BackupTool(
                                toolId = toolId.trim(),
                                make = "Bosch",
                                model = model.trim(),
                                serialNumber = serialNumber.trim(),
                                line = line.trim(),
                                station = station.trim(),
                                location = location.trim(),
                                status = "Available"
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Save Backup Tool")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditBackupToolDialog(
    tool: BackupTool,
    onDismiss: () -> Unit,
    onSave: (BackupTool) -> Unit
) {
    var toolId by remember { mutableStateOf(tool.toolId) }
    var model by remember { mutableStateOf(tool.model) }
    var serialNumber by remember { mutableStateOf(tool.serialNumber) }
    var line by remember { mutableStateOf(tool.line) }
    var station by remember { mutableStateOf(tool.station) }
    var location by remember { mutableStateOf(tool.location) }
    var selectedStatus by remember { mutableStateOf(tool.status.ifEmpty { "Available" }) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    var torque by remember { mutableStateOf(tool.torque) }
    var rpm by remember { mutableStateOf(tool.rpm) }
    var remarks by remember { mutableStateOf(tool.remarks) }

    val statusOptions = listOf("Available", "In Use", "Missing", "Under Repair", "Breakdown")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Backup Tool (${tool.toolId})", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = toolId, onValueChange = { toolId = it }, label = { Text("Tool ID") }, modifier = Modifier.fillMaxWidth(), enabled = false)
                OutlinedTextField(value = "Bosch", onValueChange = {}, enabled = false, label = { Text("Make (Restricted Brand Scope)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = serialNumber, onValueChange = { serialNumber = it }, label = { Text("Serial Number") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = line, onValueChange = { line = it }, label = { Text("Line") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text("Station") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = torque, onValueChange = { torque = it }, label = { Text("Torque (Nm)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = rpm, onValueChange = { rpm = it }, label = { Text("RPM Speed") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedStatus,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status *") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = { statusDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Status")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = statusDropdownExpanded,
                        onDismissRequest = { statusDropdownExpanded = false }
                    ) {
                        statusOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt, fontWeight = FontWeight.SemiBold) },
                                onClick = {
                                    selectedStatus = opt
                                    statusDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        tool.copy(
                            toolId = toolId,
                            make = "Bosch",
                            model = model,
                            serialNumber = serialNumber,
                            line = line,
                            station = station,
                            location = location,
                            status = selectedStatus,
                            torque = torque,
                            rpm = rpm,
                            remarks = remarks
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Text("Update Tool Details")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun NotOkReasonDialog(
    tool: BackupTool,
    onDismiss: () -> Unit,
    onConfirmReason: (String) -> Unit
) {
    val predefinedReasons = listOf("Missing", "In Use", "Under Repair", "Calibration Overdue", "Damaged")
    var selectedReason by remember { mutableStateOf("Missing") }
    var customReason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Reason / Status for ${tool.toolId}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose exact status reason:", fontSize = 12.sp, color = Color.Gray)

                predefinedReasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = reason, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = "Custom" }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = (selectedReason == "Custom"),
                        onClick = { selectedReason = "Custom" }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Other / Custom Reason", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                if (selectedReason == "Custom") {
                    OutlinedTextField(
                        value = customReason,
                        onValueChange = { customReason = it },
                        label = { Text("Specify Custom Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReason = if (selectedReason == "Custom") customReason.ifEmpty { "Not Available" } else selectedReason
                    onConfirmReason(finalReason)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Set Status")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
