package com.example.toolcare

import java.util.Locale

object SmartSparePartMatcher {

    /**
     * Smart model-based matching between Tool IDs / Models (e.g., 'V-B 15-191', '15-500', 'T-025')
     * and the Stock Management inventory products.
     */
    fun isPartMatchingTool(part: Product, tool: OriginalTool?): Boolean {
        if (tool == null) return true

        val cleanPartToolId = part.toolId.trim().uppercase(Locale.ROOT)
        val cleanToolId = tool.toolId.trim().uppercase(Locale.ROOT)
        val cleanModel = tool.model.ifEmpty { tool.toolName }.trim().uppercase(Locale.ROOT)

        // 1. Direct Tool ID match
        if (cleanPartToolId.isNotBlank() && cleanToolId.isNotBlank()) {
            if (cleanPartToolId == cleanToolId || cleanPartToolId.contains(cleanToolId) || cleanToolId.contains(cleanPartToolId)) {
                return true
            }
        }

        // 2. Extract numerical model pattern e.g. "15-191", "12-700", "15-192", "15-500"
        val regex = Regex("(\\d+-\\d+)")
        val toolIdModelToken = regex.find(cleanToolId)?.groupValues?.get(1)
        val modelNameToken = regex.find(cleanModel)?.groupValues?.get(1)

        val targetTokens = listOfNotNull(toolIdModelToken, modelNameToken).filter { it.isNotBlank() }

        if (targetTokens.isNotEmpty()) {
            for (token in targetTokens) {
                if (part.productName.contains(token, ignoreCase = true) ||
                    part.productCode.contains(token, ignoreCase = true) ||
                    part.toolId.contains(token, ignoreCase = true) ||
                    part.category.contains(token, ignoreCase = true)) {
                    return true
                }
            }
        }

        // 3. Match tool model category keywords e.g. "Spindle", "Nutrunner", "Clutch", "Motor", "Switch"
        val partBlob = "${part.productName} ${part.productCode} ${part.toolId} ${part.category}".uppercase(Locale.ROOT)
        if (cleanToolId.isNotBlank() && partBlob.contains(cleanToolId)) return true
        if (cleanModel.isNotBlank()) {
            val words = cleanModel.split(" ").filter { it.length > 3 && it != "BOSCH" && it != "TOOL" }
            if (words.any { partBlob.contains(it) }) return true
        }

        return false
    }

    /**
     * Filters list of products for a specific tool using smart model matching.
     * Falls back to general spare parts if no exact match found so list is never empty.
     */
    fun filterProductsForTool(productList: List<Product>, tool: OriginalTool?): List<Product> {
        if (tool == null) return productList
        val matched = productList.filter { isPartMatchingTool(it, tool) }
        return matched.ifEmpty { productList }
    }
}
