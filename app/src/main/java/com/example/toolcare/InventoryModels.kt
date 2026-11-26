package com.example.toolcare

data class Product(
    val productCode: String = "",
    val productName: String = "",
    val toolId: String = "T-025",
    val category: String = "",
    val unitOfMeasure: String = "Nos",
    val unitPrice: Double = 0.0,
    val openingStock: Int = 0,
    val minStockLevel: Int = 5,
    val currentStock: Int = 0,
    val company: String = "",
    val block: String = ""
) {
    val stockStatus: String
        get() = when {
            currentStock <= 0 -> "Out of Stock"
            currentStock <= minStockLevel -> "Low Stock"
            else -> "In Stock"
        }
}

data class PurchaseEntry(
    val id: String = "",
    val timestamp: String = "",
    val productCode: String = "",
    val productName: String = "",
    val supplierName: String = "",
    val qtyPurchased: Int = 0,
    val unitPrice: Double = 0.0,
    val totalAmount: Double = 0.0,
    val invoiceNo: String = "",
    val paymentMode: String = "",
    val company: String = "",
    val block: String = ""
)

data class SalesEntry(
    val id: String = "",
    val timestamp: String = "",
    val productCode: String = "",
    val productName: String = "",
    val toolId: String = "",
    val qtySold: Int = 0,
    val salePrice: Double = 0.0,
    val totalRevenue: Double = 0.0,
    val invoiceNo: String = "",
    val paymentMode: String = "",
    val remarks: String = "",
    val company: String = "",
    val block: String = ""
)
