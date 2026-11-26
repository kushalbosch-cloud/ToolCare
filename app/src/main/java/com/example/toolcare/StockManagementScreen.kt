package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class PartInOutLog(
    val id: String = "",
    val timestamp: String = "",
    val partNo: String = "",
    val partName: String = "",
    val actionType: String = "", // "IN" or "OUT"
    val qty: Int = 0,
    val toolId: String = "",
    val toolModel: String = "",
    val mechanicName: String = "",
    val reason: String = "",
    val remarks: String = ""
)

private fun extractTxMonthYear(dateStr: String?): String {
    if (dateStr.isNullOrBlank()) return ""
    val clean = dateStr.trim()
    val formats = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm", "yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            sdf.isLenient = false
            val date = sdf.parse(clean)
            if (date != null) {
                return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {}
    }
    return ""
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockManagementScreen(
    firebaseManager: FirebaseManager,
    productList: List<Product> = emptyList(),
    originalToolList: List<OriginalTool> = emptyList(),
    tool: OriginalTool? = null,
    userRole: String = "technician",
    onBackClick: () -> Unit
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current

    // Scanner Setup
    val scannerOptions = remember { QrScannerHelper.allBarcodeScannerOptions }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }
    var isScanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(0) } // 0: Inventory List, 1: Transaction History
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableStateOf("All") } // "All", "In Stock", "Low Stock", "Out of Stock"
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedSortOption by remember { mutableStateOf("Part Name") } // "Part Name", "Stock Quantity", "Low Stock First"
    var activeContextTool by remember(tool) { mutableStateOf(tool) }

    // Transaction History Filters & Search
    var selectedTxMonthFilter by remember { mutableStateOf("All Months") }
    var txSearchQuery by remember { mutableStateOf("") }

    // Duplicate Part Dialog State
    var duplicateMatchedPart by remember { mutableStateOf<Product?>(null) }
    var showDuplicateWarningDialog by remember { mutableStateOf(false) }

    // Dialog states
    var selectedProductForDetails by remember { mutableStateOf<Product?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    var selectedProductForInOut by remember { mutableStateOf<Product?>(null) }
    var defaultActionTypeForInOut by remember { mutableStateOf("OUT") }
    var showInOutDialog by remember { mutableStateOf(false) }

    var showAddNewPartDialog by remember { mutableStateOf(false) }

    // Fetch Purchase and Sales entries for Transaction History Log
    val purchaseEntries by firebaseManager.getPurchaseEntries().collectAsState(initial = emptyList())
    val salesEntries by firebaseManager.getSalesEntries().collectAsState(initial = emptyList())

    // Combine transaction history into audit logs
    val auditLogs = remember(purchaseEntries, salesEntries) {
        val list = mutableListOf<PartInOutLog>()
        purchaseEntries.forEach { p ->
            list.add(
                PartInOutLog(
                    id = p.id,
                    timestamp = p.timestamp,
                    partNo = p.productCode,
                    partName = p.productName,
                    actionType = "STOCK IN",
                    qty = p.qtyPurchased,
                    toolId = "-",
                    toolModel = "-",
                    mechanicName = p.supplierName.ifEmpty { "Store Refill" },
                    reason = "Store Refill / Purchase",
                    remarks = "Invoice: ${p.invoiceNo.ifEmpty { "N/A" }}"
                )
            )
        }
        salesEntries.forEach { s ->
            list.add(
                PartInOutLog(
                    id = s.id,
                    timestamp = s.timestamp,
                    partNo = s.productCode,
                    partName = s.productName,
                    actionType = "STOCK OUT",
                    qty = s.qtySold,
                    toolId = s.toolId.ifEmpty { "General" },
                    toolModel = "-",
                    mechanicName = s.remarks.ifEmpty { "Technician Issue" },
                    reason = s.paymentMode.ifEmpty { "Replacement / Maintenance" },
                    remarks = "Tool: ${s.toolId}"
                )
            )
        }
        list.sortedByDescending { it.timestamp }
    }

    val currentTxMonthStr = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()) }

    val availableTxMonths = remember(auditLogs, currentTxMonthStr) {
        val extracted = auditLogs.map { extractTxMonthYear(it.timestamp) }.filter { it.isNotBlank() }.distinct()
        val list = mutableListOf("All Months")
        if (!extracted.contains(currentTxMonthStr)) {
            list.add(currentTxMonthStr)
        }
        list.addAll(extracted)
        list.distinct()
    }

    val filteredAuditLogs = remember(auditLogs, selectedTxMonthFilter, txSearchQuery) {
        auditLogs.filter { log ->
            val matchesSearch = txSearchQuery.isBlank() ||
                    log.partName.contains(txSearchQuery, ignoreCase = true) ||
                    log.partNo.contains(txSearchQuery, ignoreCase = true) ||
                    log.mechanicName.contains(txSearchQuery, ignoreCase = true) ||
                    log.reason.contains(txSearchQuery, ignoreCase = true) ||
                    log.toolId.contains(txSearchQuery, ignoreCase = true)

            val monthStr = extractTxMonthYear(log.timestamp)
            val matchesMonth = selectedTxMonthFilter == "All Months" || monthStr.equals(selectedTxMonthFilter, ignoreCase = true)

            matchesSearch && matchesMonth
        }
    }

    val uniqueToolModels = remember(originalToolList) {
        val models = originalToolList.map { it.model.trim() }.filter { it.isNotBlank() && it != "Bosch " }.distinct().sorted()
        listOf("All Models") + models
    }

    val baseProducts = remember(productList) {
        if (productList.isNotEmpty()) productList
        else listOf(
            Product("1607234015", "Micro Switch", "T-025", "Electrical", "Nos", 250.0, 10, 5, 8),
            Product("1607224012", "Gearbox Assembly", "V-B 15-191", "Mechanical", "Nos", 1200.0, 5, 5, 3),
            Product("1607300001", "Bosch DC Motor", "T-026", "Electrical", "Nos", 3500.0, 15, 5, 12),
            Product("1607002003", "Battery 12V Pack", "V-B 15-192", "Battery", "Nos", 1800.0, 20, 5, 15),
            Product("1601190003", "Heavy Connector", "T-010", "Electrical", "Nos", 150.0, 5, 5, 0)
        )
    }

    val defaultProducts = remember(baseProducts, activeContextTool) {
        if (activeContextTool != null) {
            SmartSparePartMatcher.filterProductsForTool(baseProducts, activeContextTool)
        } else {
            baseProducts
        }
    }

    // Dynamic Categories
    val availableCategories = remember(defaultProducts) {
        listOf("All") + defaultProducts.map { it.category.ifEmpty { "General" } }.distinct().sorted()
    }

    fun handlePartQrScan() {
        if (isScanning) return
        isScanning = true

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                isScanning = false
                val rawScanned = barcode.rawValue.orEmpty()
                val cleanCode = QrScannerHelper.extractCleanToolId(rawScanned)
                val targetCode = if (cleanCode.isNotBlank()) cleanCode else rawScanned.trim().uppercase(Locale.ROOT)

                if (targetCode.isNotBlank()) {
                    val match = QrScannerHelper.findMatchingProduct(targetCode, defaultProducts)

                    if (match != null) {
                        selectedProductForInOut = match
                        defaultActionTypeForInOut = "OUT"
                        showInOutDialog = true
                        Toast.makeText(context, "Part Found: ${match.productName} (${match.productCode})", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(
                            context,
                            "⚠️ Part '$targetCode' Not Found in Inventory. Please add it first using 'Add New Spare Part'.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(context, "Barcode scanned but no code text detected.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnCanceledListener {
                isScanning = false
            }
            .addOnFailureListener { e ->
                isScanning = false
                Toast.makeText(context, "Scan Cancelled or Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    // Overview Counts
    val totalCount = defaultProducts.size
    val inStockCount = defaultProducts.count { it.currentStock > it.minStockLevel }
    val lowStockCount = defaultProducts.count { it.currentStock in 1..it.minStockLevel }
    val outOfStockCount = defaultProducts.count { it.currentStock <= 0 }

    // Filter Logic
    val filteredProducts = remember(searchQuery, selectedFilterTab, selectedCategoryFilter, selectedSortOption, defaultProducts) {
        var list = defaultProducts.filter { part ->
            val matchesSearch = searchQuery.isBlank() ||
                    part.productName.contains(searchQuery, ignoreCase = true) ||
                    part.productCode.contains(searchQuery, ignoreCase = true) ||
                    part.toolId.contains(searchQuery, ignoreCase = true) ||
                    part.category.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilterTab) {
                "In Stock" -> part.currentStock > part.minStockLevel
                "Low Stock" -> part.currentStock in 1..part.minStockLevel
                "Out of Stock" -> part.currentStock <= 0
                else -> true
            }

            val matchesCategory = if (selectedCategoryFilter == "All") true
            else part.category.equals(selectedCategoryFilter, ignoreCase = true)

            matchesSearch && matchesFilter && matchesCategory
        }

        list = when (selectedSortOption) {
            "Stock Quantity" -> list.sortedBy { it.currentStock }
            "Low Stock First" -> list.sortedBy { if (it.currentStock <= 0) -1 else if (it.currentStock <= it.minStockLevel) 0 else 1 }
            else -> list.sortedBy { it.productName.lowercase(Locale.ROOT) }
        }

        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stock Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                actions = {
                    IconButton(onClick = { handlePartQrScan() }) {
                        Icon(Icons.Default.Search, contentDescription = "Scan QR", tint = Color.White)
                    }
                    IconButton(onClick = {
                        Toast.makeText(context, "Stock Inventory Refreshed", Toast.LENGTH_SHORT).show()
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
        },
        floatingActionButton = {
            if (activeTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { showAddNewPartDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add New Part") },
                    text = { Text("Add New Part", fontWeight = FontWeight.Bold) },
                    containerColor = Color(0xFF2563EB),
                    contentColor = Color.White
                )
            }
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
            // Prominent Action Button: SCAN PART QR / BARCODE
            Button(
                onClick = { handlePartQrScan() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(Icons.Default.Search, contentDescription = "Scan QR", tint = Color.White)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("SCAN PART QR / BARCODE", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Text("Stock In / Stock Out Traceability", fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }

            // Tabs Row: Spare Parts Inventory vs Transaction Audit Log
            TabRow(selectedTabIndex = activeTab) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                    Text("Parts Inventory (${defaultProducts.size})", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                    Text("Transaction History (${auditLogs.size})", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            if (activeTab == 0) {
                if (activeContextTool != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered for Tool: ${activeContextTool!!.toolId} (${activeContextTool!!.model.ifEmpty { activeContextTool!!.toolName }})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2563EB)
                            )
                            TextButton(onClick = { activeContextTool = null }) {
                                Text("Clear Filter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 4 Industrial Stock Overview Summary Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StockSummaryCardNew("TOTAL PARTS", "$totalCount", Icons.Default.Build, Color(0xFF2563EB), Color(0xFFEFF6FF), Modifier.weight(1f)) {
                        selectedFilterTab = "All"
                    }
                    StockSummaryCardNew("AVAILABLE", "$inStockCount", Icons.Default.CheckCircle, Color(0xFF10B981), Color(0xFFECFDF5), Modifier.weight(1f)) {
                        selectedFilterTab = "In Stock"
                    }
                    StockSummaryCardNew("LOW STOCK", "$lowStockCount", Icons.Default.Warning, Color(0xFFD97706), Color(0xFFFFFBEB), Modifier.weight(1f)) {
                        selectedFilterTab = "Low Stock"
                    }
                    StockSummaryCardNew("OUT OF STOCK", "$outOfStockCount", Icons.Default.Clear, Color(0xFFDC2626), Color(0xFFFEF2F2), Modifier.weight(1f)) {
                        selectedFilterTab = "Out of Stock"
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by Part Name or Part Number...", fontSize = 13.sp) },
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

                // Filter Options & Export Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilterTab == "All",
                            onClick = { selectedFilterTab = "All" },
                            label = { Text("ALL ($totalCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = selectedFilterTab == "In Stock",
                            onClick = { selectedFilterTab = "In Stock" },
                            label = { Text("AVAILABLE ($inStockCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = selectedFilterTab == "Low Stock",
                            onClick = { selectedFilterTab = "Low Stock" },
                            label = { Text("LOW STOCK ($lowStockCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                        FilterChip(
                            selected = selectedFilterTab == "Out of Stock",
                            onClick = { selectedFilterTab = "Out of Stock" },
                            label = { Text("OUT OF STOCK ($outOfStockCount)", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                MISReportHelper.generateAndSharePartsInventoryExcelReport(
                                    context = context,
                                    productList = filteredProducts,
                                    filterLabel = selectedFilterTab
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Export Excel", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Spare Part Cards List
                if (filteredProducts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No spare parts found matching current filters.", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredProducts, key = { it.productCode }) { prod ->
                            val statusText = when {
                                prod.currentStock <= 0 -> "OUT OF STOCK"
                                prod.currentStock <= prod.minStockLevel -> "LOW STOCK"
                                else -> "IN STOCK"
                            }

                            val statusBg = when (statusText) {
                                "IN STOCK" -> Color(0xFFECFDF5)
                                "LOW STOCK" -> Color(0xFFFFFBEB)
                                else -> Color(0xFFFEF2F2)
                            }

                            val statusTxtColor = when (statusText) {
                                "IN STOCK" -> Color(0xFF10B981)
                                "LOW STOCK" -> Color(0xFFD97706)
                                else -> Color(0xFFDC2626)
                            }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: Part Name & Automatic Status Badge
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = prod.productName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Text(
                                                text = "Part Number: ${prod.productCode}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                                            Text(
                                                text = statusText,
                                                color = statusTxtColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = Color(0xFFF1F5F9))

                                    // Row 2: Model, Current Stock, Minimum Stock
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Model: ${prod.toolId.ifEmpty { "All Models" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                        Text("Current Stock: ${prod.currentStock} ${prod.unitOfMeasure}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                                        Text("Min Stock: ${prod.minStockLevel} Nos", fontSize = 12.sp, color = Color(0xFF64748B))
                                    }

                                    // Row 3: Dedicated Action Buttons [ DETAILS ], [ + STOCK IN ], and [ - STOCK OUT ]
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                selectedProductForDetails = prod
                                                showDetailsDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("DETAILS", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                selectedProductForInOut = prod
                                                defaultActionTypeForInOut = "IN"
                                                showInOutDialog = true
                                            },
                                            modifier = Modifier.weight(1.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Stock In", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("STOCK IN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }

                                        Button(
                                            onClick = {
                                                selectedProductForInOut = prod
                                                defaultActionTypeForInOut = "OUT"
                                                showInOutDialog = true
                                            },
                                            modifier = Modifier.weight(1.1f),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Stock Out", modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("STOCK OUT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Transaction History List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = txSearchQuery,
                            onValueChange = { txSearchQuery = it },
                            placeholder = { Text("Search part code, name, reason, tool...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                scope.launch {
                                    MISReportHelper.generateAndShareStockTransactionExcelReport(
                                        context = context,
                                        transactionLogs = filteredAuditLogs,
                                        filterLabel = if (selectedTxMonthFilter == "All Months") "All Transactions" else selectedTxMonthFilter
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Export Excel", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (availableTxMonths.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(availableTxMonths, key = { it }) { monthName ->
                                FilterChip(
                                    selected = selectedTxMonthFilter == monthName,
                                    onClick = { selectedTxMonthFilter = monthName },
                                    label = { Text("📅 $monthName", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }

                    if (filteredAuditLogs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No stock transactions matching filter.", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredAuditLogs, key = { it.id.ifEmpty { "${it.timestamp}_${it.partNo}_${it.hashCode()}" } }) { log ->
                                val isOut = log.actionType.startsWith("STOCK OUT")
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${log.partName} (${log.partNo})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                                            Surface(
                                                color = if (isOut) Color(0xFFFEF2F2) else Color(0xFFECFDF5),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "${log.actionType} (${if (isOut) "-" else "+"}${log.qty})",
                                                    color = if (isOut) Color(0xFFDC2626) else Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        if (isOut && log.toolId.isNotBlank() && log.toolId != "-") {
                                            Text("Linked Tool D.No.: ${log.toolId}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                        }

                                        Text("Reason / Used For: ${log.reason}", fontSize = 12.sp, color = Color(0xFF475569))
                                        Text("Issued/Performed By: ${log.mechanicName} • ${log.timestamp}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // 1. Add New Part Dialog
    if (showAddNewPartDialog) {
        var newPartName by remember { mutableStateOf("") }
        var newPartNumber by remember { mutableStateOf("") }
        var newCategory by remember { mutableStateOf("Electrical") }
        var newCompatibleModel by remember { mutableStateOf("All Models") }
        var modelDropdownExpanded by remember { mutableStateOf(false) }
        var newCurrentStock by remember { mutableStateOf("0") }
        var newMinStock by remember { mutableStateOf("5") }
        var newUnitPrice by remember { mutableStateOf("250") }

        AlertDialog(
            onDismissRequest = { showAddNewPartDialog = false },
            title = { Text("Add New Spare Part", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPartName,
                        onValueChange = { newPartName = it },
                        label = { Text("Part Name *") },
                        placeholder = { Text("e.g. Micro Switch") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = newPartNumber,
                        onValueChange = { newPartNumber = it },
                        label = { Text("Part Number / Code *") },
                        placeholder = { Text("e.g. 1607234015 or scan barcode") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                if (isScanning) return@IconButton
                                isScanning = true
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        isScanning = false
                                        val rawScanned = barcode.rawValue.orEmpty()
                                        val cleanCode = QrScannerHelper.extractCleanToolId(rawScanned)
                                        val targetCode = if (cleanCode.isNotBlank()) cleanCode else rawScanned.trim().uppercase(Locale.ROOT)
                                        if (targetCode.isNotBlank()) {
                                            newPartNumber = targetCode
                                            Toast.makeText(context, "Scanned Code: $targetCode", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .addOnCanceledListener { isScanning = false }
                                    .addOnFailureListener { isScanning = false }
                            }) {
                                Icon(Icons.Default.Search, contentDescription = "Scan Barcode")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = newCategory,
                        onValueChange = { newCategory = it },
                        label = { Text("Category / Assembly") },
                        placeholder = { Text("e.g. Electrical / Gearbox") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newCompatibleModel,
                            onValueChange = { 
                                newCompatibleModel = it
                                modelDropdownExpanded = true
                            },
                            label = { Text("Compatible Tool Model") },
                            placeholder = { Text("Select or type tool model...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = { modelDropdownExpanded = !modelDropdownExpanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Model")
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = modelDropdownExpanded,
                            onDismissRequest = { modelDropdownExpanded = false },
                            properties = PopupProperties(focusable = false),
                            modifier = Modifier.width(280.dp).heightIn(max = 240.dp)
                        ) {
                            uniqueToolModels.forEach { modelName ->
                                DropdownMenuItem(
                                    text = { Text(modelName, fontWeight = if (modelName == "All Models") FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        newCompatibleModel = modelName
                                        modelDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newCurrentStock,
                            onValueChange = { newCurrentStock = it },
                            label = { Text("Initial Stock") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newMinStock,
                            onValueChange = { newMinStock = it },
                            label = { Text("Min Stock Limit") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = newUnitPrice,
                        onValueChange = { newUnitPrice = it },
                        label = { Text("Unit Price (₹)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPartName.isBlank() || newPartNumber.isBlank()) {
                            Toast.makeText(context, "Part Name and Part Number are required!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val normalizedInputCode = QrScannerHelper.normalizePartCode(newPartNumber)
                        val duplicateMatch = defaultProducts.find {
                            QrScannerHelper.normalizePartCode(it.productCode) == normalizedInputCode
                        }

                        if (duplicateMatch != null) {
                            duplicateMatchedPart = duplicateMatch
                            showDuplicateWarningDialog = true
                            return@Button
                        }

                        val curStockVal = newCurrentStock.toIntOrNull() ?: 0
                        val minStockVal = newMinStock.toIntOrNull() ?: 5
                        val priceVal = newUnitPrice.toDoubleOrNull() ?: 0.0

                        val newProduct = Product(
                            productCode = newPartNumber.trim(),
                            productName = newPartName.trim(),
                            toolId = newCompatibleModel.ifEmpty { "All Models" },
                            category = newCategory.ifEmpty { "General Spares" },
                            unitOfMeasure = "Nos",
                            unitPrice = priceVal,
                            openingStock = curStockVal,
                            minStockLevel = minStockVal,
                            currentStock = curStockVal
                        )

                        firebaseManager.saveProduct(newProduct)
                        showAddNewPartDialog = false
                        Toast.makeText(context, "New Spare Part '${newPartName}' Added to Stock", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Save Spare Part", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNewPartDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDuplicateWarningDialog && duplicateMatchedPart != null) {
        val existing = duplicateMatchedPart!!
        AlertDialog(
            onDismissRequest = { showDuplicateWarningDialog = false },
            title = { Text("⚠️ Duplicate Part Code Detected", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Part Number '${existing.productCode}' already exists in inventory as '${existing.productName}' (Current Stock: ${existing.currentStock} Nos).",
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Duplicate creation is blocked to maintain inventory accuracy. Would you like to update stock via Stock In instead?",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDuplicateWarningDialog = false
                        showAddNewPartDialog = false
                        selectedProductForInOut = existing
                        defaultActionTypeForInOut = "IN"
                        showInOutDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Update Stock In")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDuplicateWarningDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 2. Part Details Dialog (VIEW DETAILS)
    if (showDetailsDialog && selectedProductForDetails != null) {
        val prod = selectedProductForDetails!!
        val statusText = when {
            prod.currentStock <= 0 -> "OUT OF STOCK"
            prod.currentStock <= prod.minStockLevel -> "LOW STOCK"
            else -> "IN STOCK"
        }

        val statusBg = when (statusText) {
            "IN STOCK" -> Color(0xFFECFDF5)
            "LOW STOCK" -> Color(0xFFFFFBEB)
            else -> Color(0xFFFEF2F2)
        }

        val statusTxtColor = when (statusText) {
            "IN STOCK" -> Color(0xFF10B981)
            "LOW STOCK" -> Color(0xFFD97706)
            else -> Color(0xFFDC2626)
        }

        val partLogs = auditLogs.filter { it.partNo.equals(prod.productCode, ignoreCase = true) }
        val totalIn = partLogs.filter { !it.actionType.contains("OUT") }.sumOf { it.qty }
        val totalOut = partLogs.filter { it.actionType.contains("OUT") }.sumOf { it.qty }

        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text("Part Details: ${prod.productName}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Part No: ${prod.productCode}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                        Surface(color = statusBg, shape = RoundedCornerShape(6.dp)) {
                            Text(statusText, color = statusTxtColor, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }

                    HorizontalDivider()

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Category / Assembly:", fontSize = 12.sp, color = Color.Gray)
                        Text(prod.category.ifEmpty { "General Assembly" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Compatible Tool Model:", fontSize = 12.sp, color = Color.Gray)
                        Text(prod.toolId.ifEmpty { "All Models" }, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Stock Overview Table
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Current", fontSize = 11.sp, color = Color.Gray)
                                Text("${prod.currentStock}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0F172A))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Minimum", fontSize = 11.sp, color = Color.Gray)
                                Text("${prod.minStockLevel}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFD97706))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total In", fontSize = 11.sp, color = Color.Gray)
                                Text("+$totalIn", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF10B981))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Out", fontSize = 11.sp, color = Color.Gray)
                                Text("-$totalOut", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent Transaction History", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))

                    if (partLogs.isEmpty()) {
                        Text("No transaction history recorded for this part yet.", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        partLogs.take(5).forEach { log ->
                            val isOut = log.actionType.contains("OUT")
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${log.actionType} (${if (isOut) "-" else "+"}${log.qty})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (isOut) Color(0xFFDC2626) else Color(0xFF10B981))
                                        Text(log.timestamp, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    if (isOut && log.toolId.isNotBlank()) {
                                        Text("Used for Tool: ${log.toolId}", fontSize = 11.sp, color = Color(0xFF2563EB))
                                    }
                                    Text("By: ${log.mechanicName} • Reason: ${log.reason}", fontSize = 11.sp, color = Color.DarkGray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDetailsDialog = false
                        selectedProductForInOut = prod
                        showInOutDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("STOCK IN / OUT", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetailsDialog = false }) { Text("Close") }
            }
        )
    }

    // 3. Stock In / Stock Out Transaction Dialog with MANDATORY Tool D.No Traceability
    if (showInOutDialog && selectedProductForInOut != null) {
        val prod = selectedProductForInOut!!
        var actionType by remember { mutableStateOf(defaultActionTypeForInOut) } // "OUT" or "IN"
        var qtyText by remember { mutableStateOf("1") }
        var mechanicName by remember { mutableStateOf("") }
        var targetToolId by remember { mutableStateOf(prod.toolId.ifEmpty { originalToolList.firstOrNull()?.toolId ?: "T-025" }) }
        var selectedUsedForReason by remember { mutableStateOf("Replacement") } // Replacement, Repair, Preventive Maintenance, Breakdown
        var remarksText by remember { mutableStateOf("") }
        var supplierNameText by remember { mutableStateOf("Supplier / Central Store") }

        val selectedToolObj = remember(targetToolId, originalToolList) {
            originalToolList.find { it.toolId.equals(targetToolId, ignoreCase = true) }
        }

        val autoToolModel = selectedToolObj?.model?.ifEmpty { selectedToolObj?.toolName } ?: "Bosch EXACT ION 12-700"
        val autoLine = selectedToolObj?.line?.ifEmpty { "Line-1" } ?: "Line-1"
        val autoStation = selectedToolObj?.station?.ifEmpty { "ST-12" } ?: "ST-12"

        AlertDialog(
            onDismissRequest = { showInOutDialog = false },
            title = {
                Text(
                    if (actionType == "OUT") "Stock Out Transaction (Issue)" else "Stock In Transaction (Refill)",
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
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(prod.productName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Part No: ${prod.productCode} • Available Stock: ${prod.currentStock} ${prod.unitOfMeasure}", fontSize = 12.sp, color = Color(0xFF475569))
                        }
                    }

                    // Toggle Button Row (Stock In vs Stock Out)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { actionType = "OUT" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (actionType == "OUT") Color(0xFFDC2626) else Color(0xFFF1F5F9),
                                contentColor = if (actionType == "OUT") Color.White else Color(0xFF0F172A)
                            )
                        ) {
                            Text("STOCK OUT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { actionType = "IN" },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (actionType == "IN") Color(0xFF10B981) else Color(0xFFF1F5F9),
                                contentColor = if (actionType == "IN") Color.White else Color(0xFF0F172A)
                            )
                        ) {
                            Text("STOCK IN", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Quantity *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (actionType == "OUT") {
                        Text("Mandatory Traceability Field:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))

                        OutlinedTextField(
                            value = targetToolId,
                            onValueChange = { targetToolId = it },
                            label = { Text("Tool D.No. / Tool ID * (Mandatory)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            isError = targetToolId.isBlank()
                        )

                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Fetched Tool Details:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                                Text("Tool Model: $autoToolModel", fontSize = 12.sp, color = Color(0xFF0F172A))
                                Text("Line & Station: $autoLine - $autoStation", fontSize = 12.sp, color = Color(0xFF475569))
                            }
                        }

                        OutlinedTextField(
                            value = selectedUsedForReason,
                            onValueChange = { selectedUsedForReason = it },
                            label = { Text("Used For (e.g. Repair / Replacement / PM)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        OutlinedTextField(
                            value = supplierNameText,
                            onValueChange = { supplierNameText = it },
                            label = { Text("Received From / Supplier") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = mechanicName,
                        onValueChange = { mechanicName = it },
                        label = { Text("Performed / Issued By") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = remarksText,
                        onValueChange = { remarksText = it },
                        label = { Text("Remarks (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qtyVal = qtyText.toIntOrNull() ?: 1
                        if (qtyVal <= 0) {
                            Toast.makeText(context, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val currentTimeStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())

                        if (actionType == "OUT") {
                            if (targetToolId.isBlank()) {
                                Toast.makeText(context, "Tool D.No / Tool ID is MANDATORY for Stock Out!", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            if (qtyVal > prod.currentStock) {
                                Toast.makeText(context, "Stock Out quantity ($qtyVal) exceeds available stock (${prod.currentStock})!", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            val sale = SalesEntry(
                                timestamp = currentTimeStr,
                                productCode = prod.productCode,
                                productName = prod.productName,
                                toolId = targetToolId.trim(),
                                qtySold = qtyVal,
                                salePrice = prod.unitPrice,
                                totalRevenue = qtyVal * prod.unitPrice,
                                paymentMode = selectedUsedForReason,
                                remarks = mechanicName.ifEmpty { "Technician Issue" }
                            )
                            firebaseManager.recordSale(sale)

                            val newStock = (prod.currentStock - qtyVal).coerceAtLeast(0)
                            val updatedProd = prod.copy(currentStock = newStock, toolId = targetToolId)
                            firebaseManager.saveProduct(updatedProd)

                            Toast.makeText(context, "Stock Out Confirmed: Issued $qtyVal x ${prod.productName} for Tool $targetToolId", Toast.LENGTH_SHORT).show()
                        } else {
                            val purchase = PurchaseEntry(
                                timestamp = currentTimeStr,
                                productCode = prod.productCode,
                                productName = prod.productName,
                                supplierName = supplierNameText.ifEmpty { "Store Refill" },
                                qtyPurchased = qtyVal,
                                unitPrice = prod.unitPrice,
                                totalAmount = qtyVal * prod.unitPrice,
                                invoiceNo = "Refill-$currentTimeStr"
                            )
                            firebaseManager.recordPurchase(purchase)

                            val newStock = prod.currentStock + qtyVal
                            val updatedProd = prod.copy(currentStock = newStock)
                            firebaseManager.saveProduct(updatedProd)

                            Toast.makeText(context, "Stock In Confirmed: Added $qtyVal x ${prod.productName}", Toast.LENGTH_SHORT).show()
                        }

                        showInOutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (actionType == "OUT") Color(0xFFDC2626) else Color(0xFF10B981))
                ) {
                    Text(if (actionType == "OUT") "CONFIRM STOCK OUT" else "CONFIRM STOCK IN", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showInOutDialog = false }) { Text("Cancel") }
            }
        )
    }
}
}

@Composable
private fun StockSummaryCardNew(
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
