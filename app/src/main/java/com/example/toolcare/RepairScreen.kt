package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.util.Locale

private fun extractRepairMonthYear(dateStr: String?): String {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RepairScreen(
    breakdownList: List<BreakdownEntry>,
    productList: List<Product> = emptyList(),
    onMarkRepaired: (entry: BreakdownEntry, details: String, usedParts: List<UsedSparePart>) -> Unit,
    onConfirmHandover: (entry: BreakdownEntry, personName: String, shift: String) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val nonHoldBreakdowns = remember(breakdownList) {
        breakdownList.filter { entry ->
            !entry.entryId.startsWith("BD_AUTO_") &&
            !entry.status.contains("HOLD", ignoreCase = true) &&
            !entry.problem.contains("Verification Failed", ignoreCase = true)
        }.sortedByDescending { it.createdAt }
    }

    var selectedStatusTab by remember { mutableStateOf("Pending Repairs") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMonthFilter by remember { mutableStateOf("All Months") }

    val currentMonthStr = remember {
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    }

    val availableMonths = remember(nonHoldBreakdowns, currentMonthStr) {
        val extracted = nonHoldBreakdowns.map { extractRepairMonthYear(it.timestamp) }.filter { it.isNotBlank() }.distinct()
        val list = mutableListOf("All Months")
        if (!extracted.contains(currentMonthStr)) {
            list.add(currentMonthStr)
        }
        list.addAll(extracted)
        list.distinct()
    }

    val filteredRepairs = remember(nonHoldBreakdowns, selectedStatusTab, searchQuery, selectedMonthFilter) {
        nonHoldBreakdowns.filter { entry ->
            val matchesTab = when (selectedStatusTab) {
                "Pending Repairs" -> entry.status != "Handover Completed"
                "Handover Completed" -> entry.status == "Handover Completed"
                else -> true
            }

            val matchesSearch = searchQuery.isBlank() ||
                    entry.originalToolId.contains(searchQuery, ignoreCase = true) ||
                    entry.backupToolId.contains(searchQuery, ignoreCase = true) ||
                    entry.problem.contains(searchQuery, ignoreCase = true) ||
                    entry.station.contains(searchQuery, ignoreCase = true) ||
                    entry.line.contains(searchQuery, ignoreCase = true)

            val monthStr = extractRepairMonthYear(entry.timestamp)
            val matchesMonth = selectedMonthFilter == "All Months" || monthStr.equals(selectedMonthFilter, ignoreCase = true)

            matchesTab && matchesSearch && matchesMonth
        }
    }

    var selectedEntryForHandover by remember { mutableStateOf<BreakdownEntry?>(null) }
    var selectedEntryForRepairDetails by remember { mutableStateOf<BreakdownEntry?>(null) }

    val scannerOptions = remember { QrScannerHelper.allBarcodeScannerOptions }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }
    var isScanningPart by remember { mutableStateOf(false) }

    fun scanAndAddPartToRepair(usedPartsList: MutableList<UsedSparePart>) {
        if (isScanningPart) return
        isScanningPart = true
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                isScanningPart = false
                val rawScanned = barcode.rawValue.orEmpty()
                val cleanCode = QrScannerHelper.extractCleanToolId(rawScanned)
                val targetCode = if (cleanCode.isNotBlank()) cleanCode else rawScanned.trim().uppercase(Locale.ROOT)

                if (targetCode.isNotBlank()) {
                    val match = QrScannerHelper.findMatchingProduct(targetCode, productList)
                    if (match != null) {
                        val existingIdx = usedPartsList.indexOfFirst { it.partId == match.productCode && !it.isManualEntry }
                        if (existingIdx >= 0) {
                            val curr = usedPartsList[existingIdx]
                            usedPartsList[existingIdx] = curr.copy(quantityUsed = curr.quantityUsed + 1)
                        } else {
                            usedPartsList.add(
                                UsedSparePart(
                                    partId = match.productCode,
                                    partName = match.productName,
                                    quantityUsed = 1,
                                    isManualEntry = false
                                )
                            )
                        }
                        Toast.makeText(context, "Scanned & Added: ${match.productName} (${match.productCode})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Part Code '$targetCode' not found in inventory.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnCanceledListener { isScanningPart = false }
            .addOnFailureListener { isScanningPart = false }
    }

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Repair & Handover Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
                        Text("Back", fontSize = 16.sp, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF8FAFC))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            TabRow(
                selectedTabIndex = if (selectedStatusTab == "Pending Repairs") 0 else 1,
                containerColor = Color.White,
                contentColor = Color(0xFF0F172A)
            ) {
                Tab(
                    selected = selectedStatusTab == "Pending Repairs",
                    onClick = { selectedStatusTab = "Pending Repairs" },
                    text = { Text("Pending Repairs", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedStatusTab == "Handover Completed",
                    onClick = { selectedStatusTab = "Handover Completed" },
                    text = { Text("Handover Completed Log", fontWeight = FontWeight.Bold) }
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

            if (filteredRepairs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No repair entries matching filter criteria.", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredRepairs, key = { it.entryId.ifEmpty { "repair_${it.hashCode()}" } }) { entry ->
                        val currentStatus = entry.status
                        val isRepaired = currentStatus == "Repaired (Pending Handover)" || currentStatus == "Handover Completed"

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Tool ID: ${entry.originalToolId}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                                    Text("Line: ${entry.line} | Station ${entry.station}", fontSize = 12.sp, color = Color(0xFF475569))
                                }

                                if (entry.backupToolId.isNotBlank() && entry.backupToolId != "No Backup Issued") {
                                    Text("Assigned Backup Tool: ${entry.backupToolId}", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                }

                                Text("⚠️ Reported Issue: ${entry.problem}", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)

                                if (entry.repairDetails.isNotBlank()) {
                                    Text("🛠 Work Done: ${entry.repairDetails}", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                                }

                                if (entry.usedSpareParts.isNotEmpty()) {
                                    Text("Spare Parts Replaced:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        entry.usedSpareParts.forEach { part ->
                                            Surface(
                                                color = if (part.isManualEntry) Color(0xFFFFFBEB) else Color(0xFFEFF6FF),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "${part.partName} x${part.quantityUsed} (${if (part.isManualEntry) "Manual" else part.partId})",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (part.isManualEntry) Color(0xFFD97706) else Color(0xFF1E40AF),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Status: $currentStatus",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRepaired) Color(0xFF10B981) else Color(0xFF2563EB)
                                    )
                                    Text(text = "🕒 ${entry.timestamp}", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                val isHandoverCompleted = currentStatus == "Handover Completed"

                                if (isHandoverCompleted) {
                                    Surface(
                                        color = Color(0xFFECFDF5),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "✔ Handover Completed (${entry.handoverPerson.ifEmpty { "Technician" }})",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF10B981)
                                            )
                                            if (entry.handoverAt > 0L) {
                                                val formattedTime = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.handoverAt))
                                                Text(text = "🕒 $formattedTime", fontSize = 10.sp, color = Color(0xFF059669))
                                            }
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { selectedEntryForRepairDetails = entry },
                                            enabled = !isRepaired,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF2563EB),
                                                disabledContainerColor = Color.LightGray
                                            )
                                        ) {
                                            Text(
                                                text = if (isRepaired) "✔ Repaired Done" else "1. Mark Repaired",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRepaired) Color.DarkGray else Color.White
                                            )
                                        }

                                        if (isRepaired) {
                                            val hasBackup = entry.backupToolId.isNotEmpty() && entry.backupToolId != "No Backup Issued"
                                            Button(
                                                onClick = { selectedEntryForHandover = entry },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                            ) {
                                                Text(
                                                    text = if (hasBackup) "2. Backup Deposit" else "2. Final Handover",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
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

        // 🔧 Repair Completion & Multiple Spare Parts Selection Dialog
        selectedEntryForRepairDetails?.let { entry ->
            var detailsText by remember { mutableStateOf("") }
            var partSourceTab by remember { mutableStateOf(0) } // 0: Inventory List, 1: Manual Entry

            var selectedInventoryProd by remember { mutableStateOf<Product?>(null) }
            var prodDropdownExpanded by remember { mutableStateOf(false) }
            var partSearchQuery by remember { mutableStateOf("") }
            var inventoryQtyText by remember { mutableStateOf("1") }

            var manualPartNameText by remember { mutableStateOf("") }
            var manualQtyText by remember { mutableStateOf("1") }

            val usedPartsList = remember { mutableStateListOf<UsedSparePart>() }

            AlertDialog(
                onDismissRequest = { selectedEntryForRepairDetails = null },
                title = { Text("Repair Completion & Parts Used (${entry.originalToolId})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Describe work done / action taken *", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        OutlinedTextField(
                            value = detailsText,
                            onValueChange = { detailsText = it },
                            placeholder = { Text("e.g. Switch replaced, cable soldered, tested OK") },
                            modifier = Modifier.fillMaxWidth().height(80.dp)
                        )

                        Text("Select Spare Parts Replaced:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        TabRow(selectedTabIndex = partSourceTab) {
                            Tab(
                                selected = partSourceTab == 0,
                                onClick = { partSourceTab = 0 },
                                text = { Text("Inventory Stock", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = partSourceTab == 1,
                                onClick = { partSourceTab = 1 },
                                text = { Text("Manual Entry", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }

                        if (partSourceTab == 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = selectedInventoryProd?.let { "${it.productName} (${it.productCode})" } ?: "",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select Part from Inventory") },
                                        modifier = Modifier.fillMaxWidth(),
                                        trailingIcon = {
                                            IconButton(onClick = { prodDropdownExpanded = !prodDropdownExpanded }) {
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                                            }
                                        },
                                        singleLine = true
                                    )

                                    DropdownMenu(
                                        expanded = prodDropdownExpanded,
                                        onDismissRequest = { prodDropdownExpanded = false },
                                        properties = PopupProperties(focusable = true),
                                        modifier = Modifier.width(280.dp).heightIn(max = 240.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = partSearchQuery,
                                            onValueChange = { partSearchQuery = it },
                                            placeholder = { Text("Search parts...", fontSize = 12.sp) },
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                                            singleLine = true,
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        )

                                        val filteredProds = productList.filter {
                                            it.productName.contains(partSearchQuery, ignoreCase = true) ||
                                            it.productCode.contains(partSearchQuery, ignoreCase = true)
                                        }

                                        if (filteredProds.isEmpty()) {
                                            DropdownMenuItem(text = { Text("No matching inventory products", fontSize = 12.sp) }, onClick = { prodDropdownExpanded = false })
                                        } else {
                                            filteredProds.forEach { p ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(p.productName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                            Text("Part No: ${p.productCode} | Stock: ${p.currentStock}", fontSize = 10.sp, color = Color.Gray)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedInventoryProd = p
                                                        prodDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { scanAndAddPartToRepair(usedPartsList) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Scan Part Barcode", tint = Color(0xFF2563EB))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inventoryQtyText,
                                    onValueChange = { inventoryQtyText = it.filter { char -> char.isDigit() } },
                                    label = { Text("Qty") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        val p = selectedInventoryProd
                                        if (p != null) {
                                            val q = inventoryQtyText.toIntOrNull() ?: 1
                                            val existingIdx = usedPartsList.indexOfFirst { it.partId == p.productCode && !it.isManualEntry }
                                            if (existingIdx >= 0) {
                                                val curr = usedPartsList[existingIdx]
                                                usedPartsList[existingIdx] = curr.copy(quantityUsed = curr.quantityUsed + q)
                                            } else {
                                                usedPartsList.add(
                                                    UsedSparePart(
                                                        partId = p.productCode,
                                                        partName = p.productName,
                                                        quantityUsed = q,
                                                        isManualEntry = false
                                                    )
                                                )
                                            }
                                            selectedInventoryProd = null
                                            inventoryQtyText = "1"
                                            Toast.makeText(context, "Added ${p.productName} x$q", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please select a part from inventory or scan barcode", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Part", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = manualPartNameText,
                                onValueChange = { manualPartNameText = it },
                                label = { Text("Custom Part Description *") },
                                placeholder = { Text("e.g. Switch, Cable, Gasket") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = manualQtyText,
                                    onValueChange = { manualQtyText = it.filter { char -> char.isDigit() } },
                                    label = { Text("Qty") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )

                                Button(
                                    onClick = {
                                        if (manualPartNameText.isNotBlank()) {
                                            val q = manualQtyText.toIntOrNull() ?: 1
                                            usedPartsList.add(
                                                UsedSparePart(
                                                    partId = "MANUAL",
                                                    partName = manualPartNameText.trim(),
                                                    quantityUsed = q,
                                                    isManualEntry = true
                                                )
                                            )
                                            manualPartNameText = ""
                                            manualQtyText = "1"
                                            Toast.makeText(context, "Custom Part Added", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Please enter part description", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Manual Part", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (usedPartsList.isNotEmpty()) {
                            Text("Parts List to be Recorded:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                            usedPartsList.forEachIndexed { index, item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${item.partName} x${item.quantityUsed}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Part ID: ${if (item.isManualEntry) "Custom Entry" else item.partId}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        IconButton(onClick = { usedPartsList.removeAt(index) }) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (detailsText.isBlank()) {
                                Toast.makeText(context, "Please enter work done / repair details", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onMarkRepaired(entry, detailsText.trim(), usedPartsList.toList())
                            selectedEntryForRepairDetails = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Confirm Repair")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedEntryForRepairDetails = null }) { Text("Cancel") }
                }
            )
        }

        // 🤝 Final Handover & Backup Deposit Confirmation Dialog
        selectedEntryForHandover?.let { entry ->
            val context = LocalContext.current
            var personNameText by remember { mutableStateOf("") }
            var shiftText by remember { mutableStateOf("Shift A") }
            val shiftsList = listOf("Shift A", "Shift B", "Shift C", "General Shift")
            var shiftDropdownExpanded by remember { mutableStateOf(false) }

            val hasBackup = entry.backupToolId.isNotEmpty() && entry.backupToolId != "No Backup Issued"

            AlertDialog(
                onDismissRequest = { selectedEntryForHandover = null },
                title = {
                    Text(
                        if (hasBackup) "Handover & Backup Deposit (${entry.originalToolId})" else "Final Handover (${entry.originalToolId})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (hasBackup) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Assigned Backup: ${entry.backupToolId}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2563EB))
                                    Text("Completing handover will automatically return this backup tool to Available status.", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = personNameText,
                            onValueChange = { personNameText = it },
                            label = { Text("Person Receiving Handover *") },
                            placeholder = { Text("e.g. Technician Name / Operator Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = shiftText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Handover Shift") },
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    IconButton(onClick = { shiftDropdownExpanded = true }) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Shift")
                                    }
                                },
                                singleLine = true
                            )
                            DropdownMenu(
                                expanded = shiftDropdownExpanded,
                                onDismissRequest = { shiftDropdownExpanded = false }
                            ) {
                                shiftsList.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s) },
                                        onClick = {
                                            shiftText = s
                                            shiftDropdownExpanded = false
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
                            if (personNameText.isBlank()) {
                                Toast.makeText(context, "Please enter receiving person's name", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onConfirmHandover(entry, personNameText.trim(), shiftText)
                            selectedEntryForHandover = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Text("Complete Handover")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedEntryForHandover = null }) { Text("Cancel") }
                }
            )
        }
    }
}
