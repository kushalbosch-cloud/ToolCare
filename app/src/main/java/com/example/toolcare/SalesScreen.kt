package com.example.toolcare

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(
    firebaseManager: FirebaseManager,
    products: List<Product>,
    onBackClick: () -> Unit
) {
    val sales by firebaseManager.getSalesEntries().collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sales / Outwards") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) { Text("Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Sale")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sales.reversed(), key = { it.id }) { entry ->
                SalesCard(entry)
            }
        }
    }

    if (showDialog) {
        SalesDialog(
            products = products,
            onDismiss = { showDialog = false },
            onSave = { 
                firebaseManager.recordSale(it)
                showDialog = false 
                Toast.makeText(context, "Sale recorded and stock updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun SalesCard(entry: SalesEntry) {
    val locale = Locale.getDefault()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.productName, fontWeight = FontWeight.Bold)
                Text(entry.timestamp, fontSize = 11.sp)
            }
            Text("Code: ${entry.productCode} | Tool ID: ${entry.toolId}", fontSize = 12.sp)
            Text("Invoice: ${entry.invoiceNo}", fontSize = 12.sp)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Qty: ${entry.qtySold}", fontWeight = FontWeight.SemiBold)
                Text("Revenue: ₹${String.format(locale, "%.2f", entry.totalRevenue)}", color = Color(0xFF059669), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SalesDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (SalesEntry) -> Unit
) {
    var productCode by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var toolId by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var salePrice by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scannerOptions = remember { QrScannerHelper.allBarcodeScannerOptions }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }
    var isScanningProduct by remember { mutableStateOf(false) }
    var isScanningTool by remember { mutableStateOf(false) }

    LaunchedEffect(productCode) {
        val found = QrScannerHelper.findMatchingProduct(productCode, products)
        if (found != null) {
            productName = found.productName
            salePrice = found.unitPrice.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Sales / Dispatch Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isScanningProduct) return@Button
                        isScanningProduct = true

                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                isScanningProduct = false
                                val rawScanned = barcode.rawValue.orEmpty()
                                val cleanCode = QrScannerHelper.extractCleanToolId(rawScanned)
                                val targetCode = if (cleanCode.isNotBlank()) cleanCode else rawScanned.trim().uppercase(Locale.ROOT)

                                if (targetCode.isNotBlank()) {
                                    val matched = QrScannerHelper.findMatchingProduct(targetCode, products)
                                    if (matched != null) {
                                        productCode = matched.productCode
                                        productName = matched.productName
                                        salePrice = matched.unitPrice.toString()
                                        Toast.makeText(context, "Part Found: ${matched.productName} (${matched.productCode})", Toast.LENGTH_SHORT).show()
                                    } else {
                                        productCode = targetCode
                                        Toast.makeText(context, "Scanned Code: $targetCode", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .addOnCanceledListener { isScanningProduct = false }
                            .addOnFailureListener { isScanningProduct = false }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, "Scan Product")
                    Spacer(Modifier.width(8.dp))
                    Text("Scan Product Barcode / QR")
                }

                OutlinedTextField(
                    value = productCode, 
                    onValueChange = { productCode = it }, 
                    label = { Text("Product Code") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = toolId, 
                    onValueChange = { toolId = it }, 
                    label = { Text("Tool ID (Receiver)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            if (isScanningTool) return@IconButton
                            isScanningTool = true

                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    isScanningTool = false
                                    val rawScanned = barcode.rawValue.orEmpty()
                                    val cleanCode = QrScannerHelper.extractCleanToolId(rawScanned)
                                    val targetCode = if (cleanCode.isNotBlank()) cleanCode else rawScanned.trim().uppercase(Locale.ROOT)
                                    if (targetCode.isNotBlank()) {
                                        toolId = targetCode
                                        Toast.makeText(context, "Tool ID Scanned: $targetCode", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .addOnCanceledListener { isScanningTool = false }
                                .addOnFailureListener { isScanningTool = false }
                        }) {
                            Icon(Icons.Default.Search, "Scan Tool")
                        }
                    }
                )

                OutlinedTextField(value = productName, onValueChange = { productName = it }, label = { Text("Product Name") }, enabled = false)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = salePrice, onValueChange = { salePrice = it }, label = { Text("Sale Price") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = invoice, onValueChange = { invoice = it }, label = { Text("Invoice No") })
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.toIntOrNull() ?: 0
                val p = salePrice.toDoubleOrNull() ?: 0.0
                onSave(SalesEntry(
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    productCode = productCode,
                    productName = productName,
                    toolId = toolId,
                    qtySold = q,
                    salePrice = p,
                    totalRevenue = q * p,
                    invoiceNo = invoice,
                    remarks = remarks
                ))
            }) { Text("Record Dispatch") }
        }
    )
}
