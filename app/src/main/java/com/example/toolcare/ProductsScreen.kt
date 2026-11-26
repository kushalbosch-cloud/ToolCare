package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(
    firebaseManager: FirebaseManager,
    onBackClick: () -> Unit
) {
    val products by firebaseManager.getProducts().collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Products Master") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) { Text("Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                editingProduct = null
                showDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(products, key = { it.productCode }) { product ->
                ProductCard(
                    product = product,
                    onEdit = { 
                        editingProduct = it
                        showDialog = true 
                    },
                    onDelete = { firebaseManager.deleteProduct(it.productCode) }
                )
            }
        }
    }

    if (showDialog) {
        ProductDialog(
            product = editingProduct,
            onDismiss = { showDialog = false },
            onSave = { 
                firebaseManager.saveProduct(it)
                showDialog = false 
            }
        )
    }
}

@Composable
fun ProductCard(
    product: Product,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.productName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Code: ${product.productCode} | Cat: ${product.category}", fontSize = 12.sp)
                Text("Stock: ${product.currentStock} ${product.unitOfMeasure}", 
                    color = if(product.currentStock <= product.minStockLevel) Color.Red else Color.Unspecified)
                
                Surface(
                    color = when(product.stockStatus) {
                        "In Stock" -> Color(0xFF059669).copy(alpha = 0.1f)
                        "Low Stock" -> Color(0xFFD97706).copy(alpha = 0.1f)
                        else -> Color.Red.copy(alpha = 0.1f)
                    },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        product.stockStatus,
                        color = when(product.stockStatus) {
                            "In Stock" -> Color(0xFF059669)
                            "Low Stock" -> Color(0xFFD97706)
                            else -> Color.Red
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = { onEdit(product) }) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = { onDelete(product) }) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red) }
        }
    }
}

@Composable
fun ProductDialog(
    product: Product?,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit
) {
    var code by remember { mutableStateOf(product?.productCode ?: "") }
    var name by remember { mutableStateOf(product?.productName ?: "") }
    var toolId by remember { mutableStateOf(product?.toolId ?: "T-025") }
    var category by remember { mutableStateOf(product?.category ?: "") }
    var price by remember { mutableStateOf(product?.unitPrice?.toString() ?: "0.0") }
    var minStock by remember { mutableStateOf(product?.minStockLevel?.toString() ?: "5") }
    var currentStock by remember { mutableStateOf(product?.currentStock?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if(product == null) "Add Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text("Product Code") }, enabled = product == null)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") })
                OutlinedTextField(value = toolId, onValueChange = { toolId = it }, label = { Text("Tool ID") })
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") })
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Unit Price") })
                OutlinedTextField(value = minStock, onValueChange = { minStock = it }, label = { Text("Min Stock Level") })
                if(product == null) {
                    OutlinedTextField(value = currentStock, onValueChange = { currentStock = it }, label = { Text("Opening Stock") })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(Product(
                    productCode = code,
                    productName = name,
                    toolId = toolId,
                    category = category,
                    unitPrice = price.toDoubleOrNull() ?: 0.0,
                    minStockLevel = minStock.toIntOrNull() ?: 5,
                    currentStock = currentStock.toIntOrNull() ?: 0,
                    openingStock = if(product == null) (currentStock.toIntOrNull() ?: 0) else product.openingStock
                ))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
