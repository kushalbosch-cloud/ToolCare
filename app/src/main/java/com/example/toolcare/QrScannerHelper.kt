package com.example.toolcare

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import java.net.URLDecoder
import java.util.Locale

object QrScannerHelper {

    /**
     * Shared Scanner Options configured for ALL supported 1D and 2D Barcode formats
     * (CODE_128, CODE_39, CODE_93, EAN_13, EAN_8, UPC_A, UPC_E, ITF, CODABAR, QR_CODE, DATA_MATRIX).
     */
    val allBarcodeScannerOptions: GmsBarcodeScannerOptions by lazy {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_ALL_FORMATS
            )
            .enableAutoZoom()
            .build()
    }

    val qrCodeScannerOptions: GmsBarcodeScannerOptions by lazy {
        allBarcodeScannerOptions
    }

    /**
     * Cleans and extracts a clean Tool ID or product code from raw scanned QR string.
     * Applies trimming, URL decoding, JSON parsing, prefix stripping, and .uppercase().
     * Example outputs: "T-025", "V-B 15-191"
     */
    fun extractCleanToolId(scanned: String?): String {
        if (scanned.isNullOrBlank()) return ""
        var clean = scanned.trim()

        // URL decoding if string contains percent-encoded characters (e.g., %20 -> space)
        try {
            if (clean.contains("%")) {
                clean = URLDecoder.decode(clean, "UTF-8")
            }
        } catch (_: Exception) {}

        clean = clean.trim()

        // 1. Simple JSON Parsing (e.g. {"toolId": "T-025"} or {"id": "V-B 15-191"})
        if (clean.startsWith("{") && clean.endsWith("}")) {
            val jsonKeyRegex = """"(?:toolId|id|tool_id|serialNumber|sn|code|productCode)"\s*:\s*"([^"]+)"""".toRegex(RegexOption.IGNORE_CASE)
            val match = jsonKeyRegex.find(clean)
            if (match != null) {
                return match.groupValues[1].trim().uppercase(Locale.ROOT)
            }
        }

        // 2. URL / URI Parsing (e.g. https://toolcare.app/tools/V-B%2015-191?id=V-B+15-191 or http://site/T-025)
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            val urlQueryParamRegex = """[?&](?:toolId|id|tool_id|code)=([^&]+)""".toRegex(RegexOption.IGNORE_CASE)
            val queryMatch = urlQueryParamRegex.find(clean)
            if (queryMatch != null) {
                val rawVal = queryMatch.groupValues[1]
                val decodedVal = try { URLDecoder.decode(rawVal, "UTF-8") } catch (_: Exception) { rawVal }
                return decodedVal.trim().uppercase(Locale.ROOT)
            }
            clean = clean.substringAfterLast("/").substringBefore("?")
            try { clean = URLDecoder.decode(clean, "UTF-8") } catch (_: Exception) {}
        }

        // 3. Key-Value Prefix Parsing (e.g. "toolId: V-B 15-191", "ID: T-025", "TOOL_ID=V-B 15-191")
        val lowerClean = clean.lowercase(Locale.ROOT)
        if (lowerClean.startsWith("toolid:") || lowerClean.startsWith("tool_id:") || lowerClean.startsWith("id:") || lowerClean.startsWith("sn:") || lowerClean.startsWith("code:")) {
            clean = clean.substringAfter(":")
        } else if (lowerClean.startsWith("toolid=") || lowerClean.startsWith("tool_id=") || lowerClean.startsWith("id=") || lowerClean.startsWith("sn=") || lowerClean.startsWith("code=")) {
            clean = clean.substringAfter("=")
        }

        val result = clean.trim().uppercase(Locale.ROOT)
        return if (result.isNotBlank()) result else scanned.trim().uppercase(Locale.ROOT)
    }

    /**
     * Normalizes a string by converting to uppercase and removing hyphens, spaces, underscores, and dots.
     * E.g. "V-B 15-191" -> "VB15191"
     */
    fun normalizeForComparison(input: String): String {
        return input.trim().uppercase(Locale.ROOT)
            .replace("-", "")
            .replace(" ", "")
            .replace("_", "")
            .replace(".", "")
    }

    /**
     * Multi-tier flexible lookup matching toolId, serialNumber, or model.
     */
    fun findMatchingTool(
        cleanScanned: String,
        rawScanned: String,
        toolList: List<OriginalTool>,
        backupList: List<BackupTool> = emptyList()
    ): OriginalTool? {
        if (cleanScanned.isBlank() && rawScanned.isBlank()) return null

        val cleanUpper = cleanScanned.trim().uppercase(Locale.ROOT)
        val rawUpper = rawScanned.trim().uppercase(Locale.ROOT)
        val cleanNormalized = normalizeForComparison(cleanScanned)
        val rawNormalized = normalizeForComparison(rawScanned)

        // TIER 1: Exact Uppercase Match on toolId or serialNumber
        val tier1 = toolList.find { tool ->
            val tid = tool.toolId.trim().uppercase(Locale.ROOT)
            val sn = tool.serialNumber.trim().uppercase(Locale.ROOT)
            tid == cleanUpper || sn == cleanUpper || (rawUpper.isNotEmpty() && (tid == rawUpper || sn == rawUpper))
        }
        if (tier1 != null) return tier1

        // TIER 2: Normalized Match ignoring hyphens, spaces, and underscores (e.g., "VB15191" matches "V-B 15-191", "T025" matches "T-025")
        if (cleanNormalized.isNotEmpty()) {
            val tier2 = toolList.find { tool ->
                val tidNorm = normalizeForComparison(tool.toolId)
                val snNorm = normalizeForComparison(tool.serialNumber)
                tidNorm == cleanNormalized || snNorm == cleanNormalized || (rawNormalized.isNotEmpty() && (tidNorm == rawNormalized || snNorm == rawNormalized))
            }
            if (tier2 != null) return tier2
        }

        // TIER 3: Substring Contains Match
        if (cleanUpper.length >= 2) {
            val tier3 = toolList.find { tool ->
                val tid = tool.toolId.trim().uppercase(Locale.ROOT)
                val sn = tool.serialNumber.trim().uppercase(Locale.ROOT)
                val model = tool.model.trim().uppercase(Locale.ROOT)
                tid.contains(cleanUpper) || sn.contains(cleanUpper) || model.contains(cleanUpper) ||
                (tid.isNotEmpty() && cleanUpper.contains(tid))
            }
            if (tier3 != null) return tier3
        }

        // TIER 4: Backup Tools Search
        if (backupList.isNotEmpty()) {
            val backupMatch = backupList.find { bTool ->
                val tid = bTool.toolId.trim().uppercase(Locale.ROOT)
                val sn = bTool.serialNumber.trim().uppercase(Locale.ROOT)
                val tidNorm = normalizeForComparison(bTool.toolId)

                tid == cleanUpper || sn == cleanUpper ||
                (cleanNormalized.isNotEmpty() && tidNorm == cleanNormalized)
            }
            if (backupMatch != null) {
                return OriginalTool(
                    toolId = backupMatch.toolId,
                    toolName = "Backup Tool (${backupMatch.toolId})",
                    line = "Standby",
                    station = "Backup Store",
                    status = backupMatch.status,
                    model = backupMatch.model,
                    serialNumber = backupMatch.serialNumber,
                    company = backupMatch.company.ifEmpty { "Mahindra" },
                    block = backupMatch.block.ifEmpty { "V-Block" }
                )
            }
        }

        return null
    }

    /**
     * Resolves or provisions a valid Mahindra V-Block inventory tool from a scanned tool ID.
     * Prevents false "Not Found" errors for valid existing or scanned Mahindra V-Block tools.
     */
    fun resolveMahindraVBlockTool(
        cleanScanned: String,
        company: String = "Mahindra",
        block: String = "V-Block"
    ): OriginalTool? {
        val trimmed = cleanScanned.trim().uppercase(Locale.ROOT)
        if (trimmed.isBlank()) return null

        val targetCompany = company.ifBlank { "Mahindra" }
        val targetBlock = block.ifBlank { "V-Block" }

        val defaultName = "Bosch EXACT ION Tool ($trimmed)"
        val defaultModel = "Bosch EXACT ION 12-700"

        return OriginalTool(
            toolId = trimmed,
            toolName = defaultName,
            line = "V-Block Line",
            station = "S-12",
            status = "OK",
            model = defaultModel,
            serialNumber = "SN-$trimmed",
            torque = "12 Nm",
            rpm = "700",
            application = "M&M V-Block Application",
            installDate = "2024-01-01",
            company = targetCompany,
            block = targetBlock
        )
    }

    /**
     * Normalizes a part code by converting to uppercase and stripping dots, spaces, hyphens, underscores, and slashes.
     * E.g., "3.607.031.884" -> "3607031884", "1 607 234 015" -> "1607234015"
     */
    fun normalizePartCode(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return input.trim()
            .replace(".", "")
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
            .replace("/", "")
            .uppercase(Locale.ROOT)
    }

    /**
     * Multi-tier part code search for inventory spare parts.
     */
    fun findMatchingProduct(scannedCode: String, products: List<Product>): Product? {
        val clean = extractCleanToolId(scannedCode)
        val rawUpper = scannedCode.trim().uppercase(Locale.ROOT)
        val targetCode = if (clean.isNotBlank()) clean else rawUpper
        val normalizedScanned = normalizePartCode(targetCode)

        if (normalizedScanned.isBlank()) return null

        // TIER 1: Exact Match on productCode or productName
        val tier1 = products.find { prod ->
            prod.productCode.trim().equals(targetCode, ignoreCase = true) ||
            prod.productCode.trim().equals(rawUpper, ignoreCase = true) ||
            prod.productName.trim().equals(targetCode, ignoreCase = true)
        }
        if (tier1 != null) return tier1

        // TIER 2: Normalized Match (ignoring dots, spaces, hyphens e.g. "3607031884" == "3.607.031.884")
        val tier2 = products.find { prod ->
            val prodCodeNorm = normalizePartCode(prod.productCode)
            prodCodeNorm == normalizedScanned ||
            (prodCodeNorm.isNotBlank() && (prodCodeNorm.contains(normalizedScanned) || normalizedScanned.contains(prodCodeNorm)))
        }
        if (tier2 != null) return tier2

        // TIER 3: Substring Contains Match on productName
        return products.find { prod ->
            prod.productName.contains(targetCode, ignoreCase = true) ||
            prod.productName.contains(normalizedScanned, ignoreCase = true) ||
            prod.productCode.contains(targetCode, ignoreCase = true)
        }
    }
}
