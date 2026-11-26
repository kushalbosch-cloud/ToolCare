package com.example.toolcare

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockReportScreen(
    products: List<Product>,
    onBackClick: () -> Unit
) {
    val totalProducts = products.size
    val totalValue = products.sumOf { it.currentStock * it.unitPrice }
    val lowStockCount = products.count { it.currentStock > 0 && it.currentStock <= it.minStockLevel }
    val outOfStockCount = products.count { it.currentStock <= 0 }
    
    val locale = Locale.getDefault()
    val context = LocalContext.current

    BackHandler { onBackClick() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Stock Report") },
                navigationIcon = {
                    TextButton(onClick = onBackClick) { Text("Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StockMetricCard("Total Items", totalProducts.toString(), Modifier.weight(1f), Color(0xFF2563EB))
                StockMetricCard("Stock Value", "₹${String.format(locale, "%.0f", totalValue)}", Modifier.weight(1f), Color(0xFF059669))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StockMetricCard("Low Stock", lowStockCount.toString(), Modifier.weight(1f), Color(0xFFD97706))
                StockMetricCard("Out of Stock", outOfStockCount.toString(), Modifier.weight(1f), Color.Red)
            }

            Spacer(modifier = Modifier.height(16.dp))
            val scope = rememberCoroutineScope()
            Button(
                onClick = {
                    scope.launch {
                        MISReportHelper.generateAndShareStockReport(context, products)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Export Stock Report (CSV)", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Detailed Stock List", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products.sortedBy { it.currentStock }, key = { it.productCode }) { product ->
                    StockRow(product)
                }
            }
        }
    }
}

@Composable
fun StockMetricCard(label: String, value: String, modifier: Modifier, color: Color) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun StockRow(product: Product) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(product.productName, fontWeight = FontWeight.SemiBold)
                Text("Code: ${product.productCode}", fontSize = 11.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${product.currentStock} ${product.unitOfMeasure}", 
                    fontWeight = FontWeight.Bold,
                    color = if(product.currentStock <= product.minStockLevel) Color.Red else Color(0xFF059669)
                )
                Text("₹${product.unitPrice}/Nos", fontSize = 11.sp)
            }
        }
    }
}
