package com.example.toolcare

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrScannerUtilsTest {

    @Test
    fun extractCleanToolId_trimsAndUpperCasesBasicString() {
        val result = QrScannerHelper.extractCleanToolId(" t-025 ")
        assertEquals("T-025", result)
    }

    @Test
    fun extractCleanToolId_parsesUrlFormat() {
        val urlResult1 = QrScannerHelper.extractCleanToolId("https://toolcare.app/tools/T-025")
        assertEquals("T-025", urlResult1)

        val urlResult2 = QrScannerHelper.extractCleanToolId("http://example.com/scan?id=t-042&other=1")
        assertEquals("T-042", urlResult2)
    }

    @Test
    fun extractCleanToolId_parsesJsonPayload() {
        val jsonString = """{"toolId": "t-018", "status": "OK"}"""
        val result = QrScannerHelper.extractCleanToolId(jsonString)
        assertEquals("T-018", result)
    }

    @Test
    fun extractCleanToolId_parsesPrefix() {
        val prefixResult1 = QrScannerHelper.extractCleanToolId("toolId: T-030")
        assertEquals("T-030", prefixResult1)

        val prefixResult2 = QrScannerHelper.extractCleanToolId("ID: t-067")
        assertEquals("T-067", prefixResult2)
    }

    @Test
    fun findMatchingTool_matchesExactAndNormalized() {
        val tools = listOf(
            OriginalTool(toolId = "T-025", toolName = "Bosch Tool 1", serialNumber = "SN-100"),
            OriginalTool(toolId = "T-042", toolName = "Bosch Tool 2", serialNumber = "SN-200")
        )

        // Exact match
        val match1 = QrScannerHelper.findMatchingTool("T-025", "T-025", tools)
        assertNotNull(match1)
        assertEquals("T-025", match1?.toolId)

        // Case-insensitive & normalized match (without hyphens)
        val match2 = QrScannerHelper.findMatchingTool("t025", "t025", tools)
        assertNotNull(match2)
        assertEquals("T-025", match2?.toolId)

        // Serial number match
        val match3 = QrScannerHelper.findMatchingTool("sn100", "sn100", tools)
        assertNotNull(match3)
        assertEquals("T-025", match3?.toolId)
    }

    @Test
    fun findMatchingTool_matchesBackupTool() {
        val originalTools = emptyList<OriginalTool>()
        val backupTools = listOf(
            BackupTool(toolId = "B-010", model = "Standby Model", serialNumber = "BSN-010")
        )

        val match = QrScannerHelper.findMatchingTool("B-010", "B-010", originalTools, backupTools)
        assertNotNull(match)
        assertEquals("B-010", match?.toolId)
        assertEquals("Mahindra", match?.company)
        assertEquals("V-Block", match?.block)
    }

    @Test
    fun resolveMahindraVBlockTool_provisionsToolForValidId() {
        val tool = QrScannerHelper.resolveMahindraVBlockTool("t-025", "Mahindra", "V-Block")
        assertNotNull(tool)
        assertEquals("T-025", tool?.toolId)
        assertEquals("Mahindra", tool?.company)
        assertEquals("V-Block", tool?.block)

        val nullTool = QrScannerHelper.resolveMahindraVBlockTool("   ", "Mahindra", "V-Block")
        assertNull(nullTool)
    }
}
