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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    firebaseManager: FirebaseManager,
    products: List<Product>,
    onBackClick: () -> Unit
) {
    val purchases by firebaseManager.getPurchaseEntries().collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchases / Inwards") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) { Text("Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New Purchase")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(purchases.reversed(), key = { it.id }) { entry ->
                PurchaseCard(entry)
            }
        }
    }

    if (showDialog) {
        PurchaseDialog(
            products = products,
            onDismiss = { showDialog = false },
            onSave = { 
                firebaseManager.recordPurchase(it)
                showDialog = false 
                Toast.makeText(context, "Purchase recorded and stock updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun PurchaseCard(entry: PurchaseEntry) {
    val locale = Locale.getDefault()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(entry.productName, fontWeight = FontWeight.Bold)
                Text(entry.timestamp, fontSize = 11.sp)
            }
            Text("Code: ${entry.productCode} | Inv: ${entry.invoiceNo}", fontSize = 12.sp)
            Text("Supplier: ${entry.supplierName}", fontSize = 12.sp)
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Qty: ${entry.qtyPurchased}", fontWeight = FontWeight.SemiBold)
                Text("Total: ₹${String.format(locale, "%.2f", entry.totalAmount)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PurchaseDialog(
    products: List<Product>,
    onDismiss: () -> Unit,
    onSave: (PurchaseEntry) -> Unit
) {
    var productCode by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var unitPrice by remember { mutableStateOf("") }
    var invoice by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("Bank Transfer") }

    val context = LocalContext.current
    val scannerOptions = remember { QrScannerHelper.allBarcodeScannerOptions }
    val scanner = remember { GmsBarcodeScanning.getClient(context, scannerOptions) }
    var isScanning by remember { mutableStateOf(false) }

    // Auto-fill product name if code matches
    LaunchedEffect(productCode) {
        val found = QrScannerHelper.findMatchingProduct(productCode, products)
        if (found != null) {
            productName = found.productName
            unitPrice = found.unitPrice.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Purchase Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isScanning) return@Button
                        isScanning = true

                        scanner.startScan()
                            .addOnSuccessListener { barcode ->
                                isScanning = false
                                val rawScanned = barcode.rawValue.orEmpty()
                                val cleanCode = QrScannerHelper.extractCleanToolId(rawScanned)
                                val targetCode = if (cleanCode.isNotBlank()) cleanCode else rawScanned.trim().uppercase(Locale.ROOT)

                                if (targetCode.isNotBlank()) {
                                    val matched = QrScannerHelper.findMatchingProduct(targetCode, products)
                                    if (matched != null) {
                                        productCode = matched.productCode
                                        productName = matched.productName
                                        unitPrice = matched.unitPrice.toString()
                                        Toast.makeText(context, "Part Found: ${matched.productName} (${matched.productCode})", Toast.LENGTH_SHORT).show()
                                    } else {
                                        productCode = targetCode
                                        Toast.makeText(context, "Scanned Code: $targetCode", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                            .addOnCanceledListener { isScanning = false }
                            .addOnFailureListener { isScanning = false }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Search, "Scan Barcode")
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
                    value = productName, 
                    onValueChange = { productName = it }, 
                    label = { Text("Product Name") },
                    enabled = false
                )
                OutlinedTextField(value = supplier, onValueChange = { supplier = it }, label = { Text("Supplier Name") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unitPrice, onValueChange = { unitPrice = it }, label = { Text("Unit Price") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = invoice, onValueChange = { invoice = it }, label = { Text("Invoice No") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val q = qty.toIntOrNull() ?: 0
                val p = unitPrice.toDoubleOrNull() ?: 0.0
                onSave(PurchaseEntry(
                    timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                    productCode = productCode,
                    productName = productName,
                    supplierName = supplier,
                    qtyPurchased = q,
                    unitPrice = p,
                    totalAmount = q * p,
                    invoiceNo = invoice,
                    paymentMode = paymentMode
                ))
            }) { Text("Record Purchase") }
        }
    )
}
