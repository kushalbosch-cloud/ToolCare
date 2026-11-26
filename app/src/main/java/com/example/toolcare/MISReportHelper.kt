package com.example.toolcare

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private fun parsePmDateObj(dateStr: String?): Date? {
    if (dateStr.isNullOrBlank()) return null
    val formats = arrayOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "yyyy/MM/dd", "dd MMM yyyy")
    for (fmt in formats) {
        try {
            val sdf = SimpleDateFormat(fmt, Locale.getDefault())
            sdf.isLenient = false
            return sdf.parse(dateStr.trim())
        } catch (_: Exception) { }
    }
    return null
}

enum class ExportFormat {
    EXCEL,
    IMAGE
}

data class RequiredPartItem(
    val partName: String,
    val partNo: String,
    val quantity: Int
)

data class ProcessedPmReportRow(
    val indexNo: Int,
    val id: String,
    val toolId: String,
    val toolName: String,
    val line: String,
    val station: String,
    val scheduledDate: String,
    val completedDate: String,
    val completedBy: String,
    val status: String, // "Completed", "Pending", "Overdue"
    val partsUsed: String,
    val remarks: String
)

data class DailyToolActivity(
    val toolId: String,
    val line: String,
    val station: String,
    val statusDisplay: String,
    val problem: String,
    val timeStr: String
)

object MISReportHelper {

    private const val TAG = "MISReportHelper"

    fun isRecordInShift(
        timestampMs: Long,
        selectedDateStr: String,
        shift: ShiftType
    ): Boolean {
        if (timestampMs <= 0L) return false
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestampMs))
        return isRecordInShift(dateStr, selectedDateStr, shift)
    }

    fun isDateInMonthAndYear(timestampMs: Long, monthIndex: Int, year: Int): Boolean {
        if (timestampMs <= 0L) return false
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        return cal.get(Calendar.MONTH) == monthIndex && cal.get(Calendar.YEAR) == year
    }

    fun formatTimestampStr(timestampMs: Long): String {
        if (timestampMs <= 0L) return ""
        return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMs))
    }

    fun getDailyToolActivities(
        breakdownEntries: List<BreakdownEntry>,
        selectedDate: String,
        targetShift: ShiftType = ShiftType.ALL
    ): List<DailyToolActivity> {
        val list = mutableListOf<DailyToolActivity>()

        breakdownEntries.forEach { entry ->
            if (entry.entryId.startsWith("BD_AUTO_")) return@forEach

            val isReportedOnDate = isRecordInShift(entry.timestamp, selectedDate, targetShift)
            if (isReportedOnDate) {
                val statusDisplay = when (entry.status) {
                    "Handover Completed", "Closed" -> "OK / Repaired"
                    "IN REPAIR", "Repaired (Pending Handover)" -> "Under Repair"
                    else -> "Breakdown / Pending"
                }

                list.add(
                    DailyToolActivity(
                        toolId = entry.originalToolId,
                        line = entry.line,
                        station = entry.station,
                        statusDisplay = statusDisplay,
                        problem = entry.problem,
                        timeStr = entry.timestamp
                    )
                )
            } else {
                val isHandoverOnDate = (isRecordInShift(entry.repairedAt, selectedDate, targetShift) || isRecordInShift(entry.handoverAt, selectedDate, targetShift)) &&
                        (entry.status == "Handover Completed" || entry.status == "Repaired (Pending Handover)")
                if (isHandoverOnDate) {
                    val formattedTime = if (entry.handoverAt > 0L) formatTimestampStr(entry.handoverAt) else if (entry.repairedAt > 0L) formatTimestampStr(entry.repairedAt) else entry.timestamp
                    list.add(
                        DailyToolActivity(
                            toolId = entry.originalToolId,
                            line = entry.line,
                            station = entry.station,
                            statusDisplay = "OK / Repaired",
                            problem = entry.problem,
                            timeStr = formattedTime
                        )
                    )
                }
            }
        }
        return list.distinctBy { "${it.toolId}_${it.statusDisplay}" }
    }

    private fun extractCoreModel(modelName: String): String {
        val regex = Regex("(\\d+-\\d+)")
        val match = regex.find(modelName)
        return match?.groupValues?.get(1) ?: modelName.trim().uppercase(Locale.ROOT)
    }

    fun isDateInMonthAndYear(dateStr: String?, monthIndex: Int, year: Int): Boolean {
        if (dateStr.isNullOrBlank()) return false
        val clean = dateStr.trim()

        val monthTwoDigit = String.format(Locale.getDefault(), "%02d", monthIndex + 1)
        val yearStr = year.toString()

        if (clean.startsWith("$yearStr-$monthTwoDigit")) return true
        if (clean.contains("/$monthTwoDigit/$yearStr") || clean.contains("-$monthTwoDigit-$yearStr")) return true

        val formats = arrayOf("dd/MM/yyyy HH:mm", "yyyy-MM-dd HH:mm", "dd/MM/yyyy", "yyyy-MM-dd", "dd-MM-yyyy", "dd MMM yyyy, HH:mm", "dd MMM yyyy")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.getDefault())
                val date = sdf.parse(clean)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    if (cal.get(Calendar.MONTH) == monthIndex && cal.get(Calendar.YEAR) == year) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    private fun buildProcessedPmReportRows(
        monthIndex: Int,
        year: Int,
        pmEntries: List<PmEntry>,
        pmHistoryList: List<PmHistoryEntry>
    ): List<ProcessedPmReportRow> {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayDate = todayCal.time

        val rowsList = mutableListOf<ProcessedPmReportRow>()
        val completedToolIdsForMonth = mutableSetOf<String>()

        val historyInMonth = pmHistoryList.filter { hist ->
            isDateInMonthAndYear(hist.scheduledDate, monthIndex, year) ||
            isDateInMonthAndYear(hist.actualCompletedDate, monthIndex, year) ||
            isDateInMonthAndYear(hist.completedDate, monthIndex, year)
        }.sortedByDescending { it.timestamp }

        var counter = 1

        historyInMonth.forEach { hist ->
            completedToolIdsForMonth.add(hist.toolId.trim().uppercase(Locale.ROOT))
            rowsList.add(
                ProcessedPmReportRow(
                    indexNo = counter++,
                    id = hist.historyId.ifEmpty { "PM_HIST_${hist.toolId}" },
                    toolId = hist.toolId,
                    toolName = hist.toolName,
                    line = hist.line,
                    station = hist.station,
                    scheduledDate = hist.scheduledDate,
                    completedDate = hist.actualCompletedDate.ifEmpty { hist.completedDate.substringBefore(",") },
                    completedBy = hist.completedByName.ifEmpty { hist.completedBy.ifEmpty { "Technician" } },
                    status = "Completed",
                    partsUsed = hist.partsUsed.ifEmpty { "No Parts Used" },
                    remarks = hist.remarks.ifEmpty { "PM Completed" }
                )
            )
        }

        val activeInMonth = pmEntries.filter { pm ->
            isDateInMonthAndYear(pm.scheduledDate, monthIndex, year) &&
            !completedToolIdsForMonth.contains(pm.toolId.trim().uppercase(Locale.ROOT))
        }

        activeInMonth.forEach { pm ->
            val scheduledObj = parsePmDateObj(pm.scheduledDate)
            val isOverdue = scheduledObj != null && scheduledObj.before(todayDate) && pm.status != "Done"
            val statusText = if (isOverdue) "Overdue" else "Pending"

            rowsList.add(
                ProcessedPmReportRow(
                    indexNo = counter++,
                    id = pm.pmId.ifEmpty { "PM_${pm.toolId}" },
                    toolId = pm.toolId,
                    toolName = pm.toolName,
                    line = pm.line,
                    station = pm.station,
                    scheduledDate = pm.scheduledDate,
                    completedDate = "-",
                    completedBy = "-",
                    status = statusText,
                    partsUsed = pm.partsUsed.ifEmpty { "No Parts Used" },
                    remarks = "-"
                )
            )
        }

        return rowsList
    }

    suspend fun generateAndShareExcelReport(
        context: Context,
        selectedDate: String,
        originalTools: List<OriginalTool>,
        backupTools: List<BackupTool>,
        breakdownEntries: List<BreakdownEntry>,
        pmEntries: List<PmEntry>,
        products: List<Product> = emptyList(),
        requiredParts: List<RequiredPartItem> = emptyList(),
        targetShift: ShiftType = ShiftType.ALL
    ) = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Daily MIS Report")

            val coreModels = (originalTools.map { it.model } + backupTools.map { it.model })
                .filter { it.isNotBlank() && it != "Bosch " }
                .map { extractCoreModel(it) }
                .distinct()
                .sorted()

            val dailyToolActivities = getDailyToolActivities(breakdownEntries, selectedDate, targetShift)

            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                val font = workbook.createFont().apply {
                    color = IndexedColors.WHITE.index
                    bold = true
                }
                setFont(font)
                setBorder(this)
            }

            val bodyStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                val font = workbook.createFont()
                setFont(font)
                setBorder(this)
            }

            val boldStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
                alignment = HorizontalAlignment.CENTER
                setBorder(this)
            }

            var rowNum = 0

            val titleRow = sheet.createRow(rowNum++)
            val titleCell = titleRow.createCell(0)
            titleCell.setCellValue("DAILY TOOL MAINTENANCE MIS REPORT")
            titleCell.setCellStyle(workbook.createCellStyle().apply {
                setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 })
            })

            val dateRow = sheet.createRow(rowNum++)
            dateRow.createCell(0).setCellValue("Date:")
            dateRow.createCell(1).setCellValue(selectedDate)
            rowNum++

            val headerRow = sheet.createRow(rowNum++)
            val headers = listOf("Model", "Original Total", "Original Breakdown", "Total Backup", "Backup Available", "Use Backup")
            headers.forEachIndexed { i, h ->
                headerRow.createCell(i).apply {
                    setCellValue(h)
                    setCellStyle(headerStyle)
                }
            }

            coreModels.forEach { coreModel ->
                val row = sheet.createRow(rowNum++)
                row.createCell(0).apply { setCellValue(coreModel); setCellStyle(bodyStyle) }
                
                val totalOriginal = originalTools.count { extractCoreModel(it.model) == coreModel }
                row.createCell(1).apply { setCellValue(totalOriginal.toDouble()); setCellStyle(bodyStyle) }
                
                val breakdownCount = originalTools.count { 
                    extractCoreModel(it.model) == coreModel && (it.status.equals("Breakdown", ignoreCase = true) || it.status.equals("Under Repair", ignoreCase = true)) 
                }
                row.createCell(2).apply { 
                    if (breakdownCount > 0) {
                        setCellValue(breakdownCount.toDouble())
                    } else {
                        setCellValue("")
                    }
                    setCellStyle(bodyStyle) 
                }
                
                val totalBackup = backupTools.count { extractCoreModel(it.model) == coreModel }
                row.createCell(3).apply { setCellValue(totalBackup.toDouble()); setCellStyle(bodyStyle) }
                
                val availableBackup = backupTools.count { 
                    extractCoreModel(it.model) == coreModel && (it.status.equals("Available", ignoreCase = true) || it.status.equals("Available Backup", ignoreCase = true) || it.status.equals("OK", ignoreCase = true)) 
                }
                row.createCell(4).apply { setCellValue(availableBackup.toDouble()); setCellStyle(bodyStyle) }
                
                val inUseBackup = backupTools.count { 
                    extractCoreModel(it.model) == coreModel && (it.status.equals("In Use", ignoreCase = true) || it.status.equals("Assigned", ignoreCase = true)) 
                }
                row.createCell(5).apply { 
                    if (inUseBackup > 0) {
                        setCellValue(inUseBackup.toDouble())
                    } else {
                        setCellValue("")
                    }
                    setCellStyle(bodyStyle) 
                }
            }

            val totalRow = sheet.createRow(rowNum++)
            totalRow.createCell(0).apply { setCellValue("TOTAL"); setCellStyle(boldStyle) }
            
            val sum1 = coreModels.sumOf { m -> originalTools.count { extractCoreModel(it.model) == m }.toDouble() }
            val sum2 = coreModels.sumOf { m -> originalTools.count { extractCoreModel(it.model) == m && (it.status.equals("Breakdown", ignoreCase = true) || it.status.equals("Under Repair", ignoreCase = true)) }.toDouble() }
            val sum3 = coreModels.sumOf { m -> backupTools.count { extractCoreModel(it.model) == m }.toDouble() }
            val sum4 = coreModels.sumOf { m -> backupTools.count { extractCoreModel(it.model) == m && (it.status.equals("Available", ignoreCase = true) || it.status.equals("Available Backup", ignoreCase = true) || it.status.equals("OK", ignoreCase = true)) }.toDouble() }
            val sum5 = coreModels.sumOf { m -> backupTools.count { extractCoreModel(it.model) == m && (it.status.equals("In Use", ignoreCase = true) || it.status.equals("Assigned", ignoreCase = true)) }.toDouble() }

            totalRow.createCell(1).apply { setCellValue(sum1); setCellStyle(boldStyle) }
            totalRow.createCell(2).apply { setCellValue(sum2); setCellStyle(boldStyle) }
            totalRow.createCell(3).apply { setCellValue(sum3); setCellStyle(boldStyle) }
            totalRow.createCell(4).apply { setCellValue(sum4); setCellStyle(boldStyle) }
            totalRow.createCell(5).apply { setCellValue(sum5); setCellStyle(boldStyle) }
            rowNum++

            val missingCount = backupTools.count { 
                it.status.contains("Missing", ignoreCase = true) ||
                (!it.status.equals("Available", ignoreCase = true) &&
                 !it.status.equals("Available Backup", ignoreCase = true) &&
                 !it.status.equals("OK", ignoreCase = true) &&
                 !it.status.equals("In Use", ignoreCase = true) &&
                 !it.status.equals("Assigned", ignoreCase = true))
            }
            sheet.createRow(rowNum++).createCell(0).setCellValue("* $missingCount Backup tools are missing")
            rowNum += 2

            val repairHeaderRow = sheet.createRow(rowNum++)
            repairHeaderRow.createCell(0).apply { setCellValue("Tool ID"); cellStyle = headerStyle }
            repairHeaderRow.createCell(1).apply { setCellValue("Line"); cellStyle = headerStyle }
            repairHeaderRow.createCell(2).apply { setCellValue("Station Number"); cellStyle = headerStyle }
            repairHeaderRow.createCell(3).apply { setCellValue("Status"); cellStyle = headerStyle }

            if (dailyToolActivities.isEmpty()) {
                val emptyRow = sheet.createRow(rowNum++)
                emptyRow.createCell(0).apply { setCellValue("No tool activity recorded today"); cellStyle = bodyStyle }
                emptyRow.createCell(1).apply { setCellValue("-"); cellStyle = bodyStyle }
                emptyRow.createCell(2).apply { setCellValue("-"); cellStyle = bodyStyle }
                emptyRow.createCell(3).apply { setCellValue("-"); cellStyle = bodyStyle }
            } else {
                dailyToolActivities.forEach { act ->
                    val row = sheet.createRow(rowNum++)
                    row.createCell(0).apply { setCellValue(act.toolId); cellStyle = bodyStyle }
                    row.createCell(1).apply { setCellValue(act.line); cellStyle = bodyStyle }
                    row.createCell(2).apply { setCellValue(act.station); cellStyle = bodyStyle }
                    row.createCell(3).apply { setCellValue(act.statusDisplay); cellStyle = boldStyle }
                }
            }

            val repairTotalRow = sheet.createRow(rowNum++)
            repairTotalRow.createCell(0).apply { setCellValue("Total Today Tool Activity:"); setCellStyle(boldStyle) }
            repairTotalRow.createCell(1).apply { setCellValue(dailyToolActivities.size.toDouble()); setCellStyle(boldStyle) }
            rowNum += 2

            val partsTitleRow = sheet.createRow(rowNum++)
            partsTitleRow.createCell(0).apply { setCellValue("These Part are required"); setCellStyle(boldStyle) }

            val partsHeaderRow = sheet.createRow(rowNum++)
            partsHeaderRow.createCell(0).apply { setCellValue("Part Name"); setCellStyle(headerStyle) }
            partsHeaderRow.createCell(1).apply { setCellValue("Part no"); setCellStyle(headerStyle) }
            partsHeaderRow.createCell(2).apply { setCellValue("Qty"); setCellStyle(headerStyle) }

            // Requirement 2: If no parts selected, leave cells completely BLANK (no hardcoded text)
            if (requiredParts.isNotEmpty()) {
                requiredParts.forEach { part ->
                    val pRow = sheet.createRow(rowNum++)
                    pRow.createCell(0).apply { setCellValue(part.partName); setCellStyle(bodyStyle) }
                    pRow.createCell(1).apply { setCellValue(part.partNo); setCellStyle(bodyStyle) }
                    pRow.createCell(2).apply { setCellValue(part.quantity.toDouble()); setCellStyle(bodyStyle) }
                }
            } else {
                val pRow = sheet.createRow(rowNum++)
                pRow.createCell(0).apply { setCellValue(""); setCellStyle(bodyStyle) }
                pRow.createCell(1).apply { setCellValue(""); setCellStyle(bodyStyle) }
                pRow.createCell(2).apply { setCellValue(""); setCellStyle(bodyStyle) }
            }

            sheet.setColumnWidth(0, 30 * 256)
            sheet.setColumnWidth(1, 15 * 256)
            sheet.setColumnWidth(2, 15 * 256)
            sheet.setColumnWidth(3, 15 * 256)
            sheet.setColumnWidth(4, 15 * 256)
            sheet.setColumnWidth(5, 15 * 256)

            val cleanDate = selectedDate.replace("/", "")
            val shiftTag = if (targetShift == ShiftType.ALL) "AllShifts" else targetShift.name.replace("SHIFT_", "Shift")
            val fileName = "ToolCare_Report_${shiftTag}_${cleanDate}.xlsx"
            val file = File(context.cacheDir, fileName)
            val out = FileOutputStream(file)
            workbook.write(out)
            out.close()
            workbook.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "Share MIS Report ($selectedDate - ${targetShift.chipText})").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Chooser failed", e)
                    Toast.makeText(context, "Cannot share report: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Error generating Excel report", t)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Excel Export Failed: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Requirement 3: Renders Daily MIS Report into a crisp Bitmap Photo Image (.png)
     * allowing the user to view, save, or share directly as a photo via WhatsApp/Apps.
     */
    suspend fun generateAndShareReportImage(
        context: Context,
        selectedDate: String,
        originalTools: List<OriginalTool>,
        backupTools: List<BackupTool>,
        breakdownEntries: List<BreakdownEntry>,
        pmEntries: List<PmEntry>,
        requiredParts: List<RequiredPartItem> = emptyList(),
        targetShift: ShiftType = ShiftType.ALL
    ) = withContext(Dispatchers.IO) {
        try {
            val coreModels = (originalTools.map { it.model } + backupTools.map { it.model })
                .filter { it.isNotBlank() && it != "Bosch " }
                .map { extractCoreModel(it) }
                .distinct()
                .sorted()

            val repairedToday = breakdownEntries.filter { entry ->
                val isRepairedOrHandedOver = entry.status == "Handover Completed" || entry.status == "Repaired (Pending Handover)" || entry.status == "Closed"
                val matchesDate = isRecordInShift(entry.handoverAt, selectedDate, targetShift) ||
                                  isRecordInShift(entry.repairedAt, selectedDate, targetShift) ||
                                  isRecordInShift(entry.timestamp, selectedDate, targetShift)
                isRepairedOrHandedOver && matchesDate && !entry.entryId.startsWith("BD_AUTO_")
            }

            val width = 1080
            val rowHeight = 46
            val headerHeight = 120
            
            val modelRows = coreModels.size + 2
            val repairRows = if (repairedToday.isEmpty()) 2 else repairedToday.size + 1
            val partsRows = if (requiredParts.isEmpty()) 2 else requiredParts.size + 1
            
            val totalRows = modelRows + repairRows + partsRows + 10
            val height = (headerHeight + (totalRows * rowHeight)).coerceAtLeast(1400)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val bgPaint = Paint().apply { color = AndroidColor.WHITE }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Header Banner (Dark Navy)
            val headerPaint = Paint().apply { color = AndroidColor.parseColor("#0F172A") }
            canvas.drawRect(0f, 0f, width.toFloat(), headerHeight.toFloat(), headerPaint)

            val titlePaint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = 30f
                isFakeBoldText = true
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("DAILY MIS REPORT (${targetShift.label.uppercase(Locale.ROOT)})", 40f, 50f, titlePaint)

            val subTitlePaint = Paint().apply {
                color = AndroidColor.parseColor("#38BDF8")
                textSize = 20f
                isFakeBoldText = true
            }
            canvas.drawText("ToolCare Maintenance • Date: $selectedDate • ${targetShift.chipText}", 40f, 95f, subTitlePaint)

            var yPos = headerHeight + 50f

            val sectionHeaderPaint = Paint().apply {
                color = AndroidColor.parseColor("#0F172A")
                textSize = 24f
                isFakeBoldText = true
            }

            val tableHeaderBgPaint = Paint().apply {
                color = AndroidColor.parseColor("#2563EB")
            }

            val tableHeaderTxtPaint = Paint().apply {
                color = AndroidColor.WHITE
                textSize = 18f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }

            val bodyTxtPaint = Paint().apply {
                color = AndroidColor.parseColor("#1E293B")
                textSize = 17f
                textAlign = Paint.Align.CENTER
            }

            val gridLinePaint = Paint().apply {
                color = AndroidColor.parseColor("#E2E8F0")
                strokeWidth = 2f
            }

            // Table 1: Model Wise Tool Summary
            canvas.drawText("1. MODEL-WISE TOOL SUMMARY", 40f, yPos, sectionHeaderPaint)
            yPos += 30f

            val cols1 = arrayOf("Model", "Original Total", "Breakdown", "Total Backup", "Backup Avail.", "Use Backup")
            val colWidth1 = (width - 80) / cols1.size

            canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, tableHeaderBgPaint)
            cols1.forEachIndexed { idx, colTitle ->
                val x = 40f + (idx * colWidth1) + (colWidth1 / 2f)
                canvas.drawText(colTitle, x, yPos + 30f, tableHeaderTxtPaint)
            }
            yPos += rowHeight

            coreModels.forEachIndexed { rowIdx, model ->
                val rowBg = if (rowIdx % 2 == 0) AndroidColor.WHITE else AndroidColor.parseColor("#F8FAFC")
                canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, Paint().apply { color = rowBg })

                val origTotal = originalTools.count { extractCoreModel(it.model) == model }
                val origBd = originalTools.count { extractCoreModel(it.model) == model && (it.status.equals("Breakdown", ignoreCase = true) || it.status.equals("Under Repair", ignoreCase = true)) }
                val totBackup = backupTools.count { extractCoreModel(it.model) == model }
                val availBackup = backupTools.count { extractCoreModel(it.model) == model && (it.status.equals("Available", ignoreCase = true) || it.status.equals("Available Backup", ignoreCase = true) || it.status.equals("OK", ignoreCase = true)) }
                val inUseBackup = backupTools.count { extractCoreModel(it.model) == model && (it.status.equals("In Use", ignoreCase = true) || it.status.equals("Assigned", ignoreCase = true)) }

                val vals = arrayOf(
                    model,
                    origTotal.toString(),
                    if (origBd > 0) origBd.toString() else "-",
                    totBackup.toString(),
                    availBackup.toString(),
                    if (inUseBackup > 0) inUseBackup.toString() else "-"
                )

                vals.forEachIndexed { cIdx, valStr ->
                    val x = 40f + (cIdx * colWidth1) + (colWidth1 / 2f)
                    canvas.drawText(valStr, x, yPos + 30f, bodyTxtPaint)
                }

                canvas.drawLine(40f, yPos + rowHeight, (width - 40).toFloat(), yPos + rowHeight, gridLinePaint)
                yPos += rowHeight
            }

            // Total Row Table 1
            val totBgPaint = Paint().apply { color = AndroidColor.parseColor("#EEF2FF") }
            val totTxtPaint = Paint().apply { color = AndroidColor.parseColor("#1E1B4B"); textSize = 18f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, totBgPaint)

            val sumOrig = coreModels.sumOf { m -> originalTools.count { extractCoreModel(it.model) == m } }
            val sumBd = coreModels.sumOf { m -> originalTools.count { extractCoreModel(it.model) == m && (it.status.equals("Breakdown", ignoreCase = true) || it.status.equals("Under Repair", ignoreCase = true)) } }
            val sumTotBack = coreModels.sumOf { m -> backupTools.count { extractCoreModel(it.model) == m } }
            val sumAvailBack = coreModels.sumOf { m -> backupTools.count { extractCoreModel(it.model) == m && (it.status.equals("Available", ignoreCase = true) || it.status.equals("Available Backup", ignoreCase = true) || it.status.equals("OK", ignoreCase = true)) } }
            val sumInUseBack = coreModels.sumOf { m -> backupTools.count { extractCoreModel(it.model) == m && (it.status.equals("In Use", ignoreCase = true) || it.status.equals("Assigned", ignoreCase = true)) } }

            val totVals = arrayOf("TOTAL", sumOrig.toString(), sumBd.toString(), sumTotBack.toString(), sumAvailBack.toString(), sumInUseBack.toString())
            totVals.forEachIndexed { cIdx, valStr ->
                val x = 40f + (cIdx * colWidth1) + (colWidth1 / 2f)
                canvas.drawText(valStr, x, yPos + 30f, totTxtPaint)
            }
            canvas.drawLine(40f, yPos + rowHeight, (width - 40).toFloat(), yPos + rowHeight, gridLinePaint)
            yPos += rowHeight + 15f

            // Missing Note
            val missingCount = backupTools.count { 
                it.status.contains("Missing", ignoreCase = true) ||
                (!it.status.equals("Available", ignoreCase = true) &&
                 !it.status.equals("Available Backup", ignoreCase = true) &&
                 !it.status.equals("OK", ignoreCase = true) &&
                 !it.status.equals("In Use", ignoreCase = true) &&
                 !it.status.equals("Assigned", ignoreCase = true))
            }
            val notePaint = Paint().apply { color = AndroidColor.parseColor("#DC2626"); textSize = 16f; isFakeBoldText = true }
            canvas.drawText("* $missingCount Backup tools are missing", 40f, yPos, notePaint)
            yPos += 40f

            // Table 2: Today Repairing Tools
            canvas.drawText("2. TODAY REPAIRING TOOLS", 40f, yPos, sectionHeaderPaint)
            yPos += 30f

            val cols2 = arrayOf("Tool ID", "Line", "Station Number")
            val colWidth2 = (width - 80) / cols2.size

            val hdrBg2 = Paint().apply { color = AndroidColor.parseColor("#0F172A") }
            canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, hdrBg2)
            cols2.forEachIndexed { idx, colTitle ->
                val x = 40f + (idx * colWidth2) + (colWidth2 / 2f)
                canvas.drawText(colTitle, x, yPos + 30f, tableHeaderTxtPaint)
            }
            yPos += rowHeight

            if (repairedToday.isEmpty()) {
                val emptyPaint = Paint().apply { color = AndroidColor.parseColor("#64748B"); textSize = 16f; textAlign = Paint.Align.CENTER }
                canvas.drawText("No tools repaired today", width / 2f, yPos + 30f, emptyPaint)
                canvas.drawLine(40f, yPos + rowHeight, (width - 40).toFloat(), yPos + rowHeight, gridLinePaint)
                yPos += rowHeight
            } else {
                repairedToday.forEachIndexed { idx, entry ->
                    val rowBg = if (idx % 2 == 0) AndroidColor.WHITE else AndroidColor.parseColor("#F8FAFC")
                    canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, Paint().apply { color = rowBg })
                    
                    canvas.drawText(entry.originalToolId, 40f + (0 * colWidth2) + (colWidth2 / 2f), yPos + 30f, bodyTxtPaint)
                    canvas.drawText(entry.line, 40f + (1 * colWidth2) + (colWidth2 / 2f), yPos + 30f, bodyTxtPaint)
                    canvas.drawText(entry.station, 40f + (2 * colWidth2) + (colWidth2 / 2f), yPos + 30f, bodyTxtPaint)

                    canvas.drawLine(40f, yPos + rowHeight, (width - 40).toFloat(), yPos + rowHeight, gridLinePaint)
                    yPos += rowHeight
                }
            }
            yPos += 30f

            // Table 3: These Part are required
            canvas.drawText("3. THESE PART ARE REQUIRED", 40f, yPos, sectionHeaderPaint)
            yPos += 30f

            val cols3 = arrayOf("Part Name", "Part Number", "Qty")
            val colWidth3 = (width - 80) / cols3.size

            val hdrBg3 = Paint().apply { color = AndroidColor.parseColor("#0D9488") }
            canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, hdrBg3)
            cols3.forEachIndexed { idx, colTitle ->
                val x = 40f + (idx * colWidth3) + (colWidth3 / 2f)
                canvas.drawText(colTitle, x, yPos + 30f, tableHeaderTxtPaint)
            }
            yPos += rowHeight

            if (requiredParts.isNotEmpty()) {
                requiredParts.forEachIndexed { idx, part ->
                    val rowBg = if (idx % 2 == 0) AndroidColor.WHITE else AndroidColor.parseColor("#F8FAFC")
                    canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, Paint().apply { color = rowBg })

                    canvas.drawText(part.partName, 40f + (0 * colWidth3) + (colWidth3 / 2f), yPos + 30f, bodyTxtPaint)
                    canvas.drawText(part.partNo, 40f + (1 * colWidth3) + (colWidth3 / 2f), yPos + 30f, bodyTxtPaint)
                    canvas.drawText(part.quantity.toString(), 40f + (2 * colWidth3) + (colWidth3 / 2f), yPos + 30f, bodyTxtPaint)

                    canvas.drawLine(40f, yPos + rowHeight, (width - 40).toFloat(), yPos + rowHeight, gridLinePaint)
                    yPos += rowHeight
                }
            } else {
                // Requirement 2: Completely BLANK row (no hardcoded text like "All Stock In")
                canvas.drawRect(40f, yPos, (width - 40).toFloat(), yPos + rowHeight, Paint().apply { color = AndroidColor.WHITE })
                canvas.drawLine(40f, yPos + rowHeight, (width - 40).toFloat(), yPos + rowHeight, gridLinePaint)
                yPos += rowHeight
            }

            yPos += 40f
            val footerPaint = Paint().apply { color = AndroidColor.parseColor("#94A3B8"); textSize = 16f; textAlign = Paint.Align.CENTER }
            canvas.drawText("Generated via ToolCare Mobile App • ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}", width / 2f, yPos, footerPaint)

            // Save Bitmap to PNG File
            val cleanDate = selectedDate.replace("/", "")
            val shiftTag = if (targetShift == ShiftType.ALL) "AllShifts" else targetShift.name.replace("SHIFT_", "Shift")
            val fileName = "ToolCare_Report_${shiftTag}_${cleanDate}.png"
            val file = File(context.cacheDir, fileName)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share MIS Report Photo ($selectedDate - ${targetShift.chipText})").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Chooser failed", e)
                    Toast.makeText(context, "Cannot share photo: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Error generating Report Image", t)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Photo Export Failed: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun shareTextSummary(context: Context, title: String, textContent: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, textContent)
            }
            val chooser = Intent.createChooser(shareIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Share text failed", e)
            Toast.makeText(context, "Cannot share text: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setBorder(style: CellStyle) {
        style.borderTop = BorderStyle.THIN
        style.borderBottom = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
    }

    fun getFormattedTextReport(
        selectedDate: String,
        originalTools: List<OriginalTool>,
        backupTools: List<BackupTool>,
        breakdownEntries: List<BreakdownEntry>,
        pmEntries: List<PmEntry>,
        targetShift: ShiftType = ShiftType.ALL
    ): String {
        val report = StringBuilder()
        val repairedToday = breakdownEntries.filter { entry ->
            val isRepairedOrHandedOver = entry.status == "Handover Completed" || entry.status == "Repaired (Pending Handover)" || entry.status == "Closed"
            val matchesDate = isRecordInShift(entry.handoverAt, selectedDate, targetShift) ||
                              isRecordInShift(entry.repairedAt, selectedDate, targetShift) ||
                              isRecordInShift(entry.timestamp, selectedDate, targetShift)
            isRepairedOrHandedOver && matchesDate && !entry.entryId.startsWith("BD_AUTO_")
        }
        val availableBackups = backupTools.filter { it.status == "Available" || it.status == "OK" }
        val currentBreakdowns = breakdownEntries.filter { it.status != "Handover Completed" && isRecordInShift(it.timestamp, selectedDate, targetShift) }

        report.append("DAILY TOOL MAINTENANCE MIS REPORT (${targetShift.chipText.uppercase(Locale.ROOT)})\n")
        report.append("Date: $selectedDate | Shift Time: ${targetShift.timeRange}\n\n")

        report.append("1. TODAY REPAIRING TOOLS\n")
        if (repairedToday.isEmpty()) {
            report.append("- None\n")
        } else {
            repairedToday.forEach { entry ->
                report.append("- Tool ID: ${entry.originalToolId} | Line: ${entry.line} | Station Number: ${entry.station}\n")
            }
        }
        report.append("Total Today Repairing Tools: ${repairedToday.size}\n\n")

        report.append("2. BACKUP AVAILABLE TOOLS\n")
        if (availableBackups.isEmpty()) {
            report.append("- None\n")
        } else {
            availableBackups.forEach { tool ->
                report.append("- Tool ID: ${tool.toolId} | Line: ${tool.line} | Station Number: ${tool.station}\n")
            }
        }
        report.append("Total Backup Available: ${availableBackups.size} Tools\n\n")

        report.append("3. CURRENT BREAKDOWN TOOLS\n")
        if (currentBreakdowns.isEmpty()) {
            report.append("- None\n")
        } else {
            val groupedByProblem = currentBreakdowns.groupBy { it.problem }
            groupedByProblem.forEach { (problem, entries) ->
                report.append("($problem)\n")
                entries.forEach { entry ->
                    val isHighRisk = ToolHealthHelper.isHighRisk(entry.originalToolId, breakdownEntries, pmEntries)
                    val riskTag = if (isHighRisk) " [!!! HIGH RISK !!!]" else ""
                    report.append("- Tool ID: ${entry.originalToolId}$riskTag | Line: ${entry.line} | Station Number: ${entry.station}\n")
                }
            }
        }
        report.append("Total Breakdown Tools: ${currentBreakdowns.size}\n\n")

        report.append("SUMMARY\n")
        report.append("- Today's Repairing Tools: ${repairedToday.size}\n")
        report.append("- Backup Available: ${availableBackups.size}\n")
        report.append("- Current Breakdown: ${currentBreakdowns.size}\n")
        
        val mainProblem = currentBreakdowns.groupBy { it.problem }.maxByOrNull { it.value.size }?.key ?: "None"
        report.append("- Breakdown Problem: $mainProblem\n")

        return report.toString()
    }

    suspend fun generateAndSharePMReport(
        context: Context,
        selectedDate: String,
        pmEntries: List<PmEntry>
    ) = withContext(Dispatchers.IO) {
        val fileName = "PM_MIS_Report_${selectedDate.replace("/", "-")}.csv"
        val csvContent = StringBuilder()

        val dailyPMs = pmEntries.filter { it.scheduledDate == selectedDate || (it.completedDate ?: "").startsWith(selectedDate) }

        csvContent.append("DAILY PM MIS REPORT - TOOLCARE\n")
        csvContent.append("Report Date,${selectedDate}\n")
        csvContent.append("Generation Time,${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}\n\n")

        csvContent.append("DAILY PM ROTATION (3-TOOL SET)\n")
        csvContent.append("No.,Tool ID,Scheduled Date,Status,Completed Date,Parts Used\n")
        
        if (dailyPMs.isEmpty()) {
            csvContent.append("No PMs scheduled or completed for this date.\n")
        } else {
            dailyPMs.forEachIndexed { index, entry ->
                csvContent.append("${index + 1},${entry.toolId},${entry.scheduledDate},${entry.status},${entry.completedDate ?: "N/A"},\"${entry.partsUsed ?: "None"}\"\n")
            }
        }
        csvContent.append("\n")
        
        val doneCount = dailyPMs.count { it.status == "Done" }
        val pendingCount = dailyPMs.count { it.status == "Pending" }
        
        csvContent.append("DAILY SUMMARY\n")
        csvContent.append("Total Tools in Set,${dailyPMs.size}\n")
        csvContent.append("Completed,${doneCount}\n")
        csvContent.append("Pending,${pendingCount}\n")
        csvContent.append("Overall Status,${if (pendingCount == 0 && dailyPMs.isNotEmpty()) "COMPLETED" else "PENDING"}\n")

        try {
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            outputStream.write(csvContent.toString().toByteArray())
            outputStream.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "Share PM MIS Report ($selectedDate)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Chooser failed", e)
                    Toast.makeText(context, "Cannot share report: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    suspend fun generateAndShareStockReport(
        context: Context,
        products: List<Product>
    ) = withContext(Dispatchers.IO) {
        val fileName = "Stock_Report_${SimpleDateFormat("ddMMyyyy_HHmm", Locale.getDefault()).format(Date())}.csv"
        val csvContent = StringBuilder()

        csvContent.append("INDEPENDENT STOCK MANAGEMENT REPORT\n")
        csvContent.append("Date,${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}\n\n")

        csvContent.append("SUMMARY\n")
        csvContent.append("Total Items,${products.size}\n")
        csvContent.append("Total Value,₹${products.sumOf { it.currentStock * it.unitPrice }}\n")
        csvContent.append("Low Stock Items,${products.count { it.currentStock > 0 && it.currentStock <= it.minStockLevel }}\n")
        csvContent.append("Out of Stock Items,${products.count { it.currentStock <= 0 }}\n\n")

        csvContent.append("DETAILED STOCK LIST\n")
        csvContent.append("No.,Product Code,Product Name,Category,Price,Current Stock,Status\n")
        products.sortedBy { it.currentStock }.forEachIndexed { index, product ->
            csvContent.append("${index + 1},${product.productCode},${product.productName},${product.category},${product.unitPrice},${product.currentStock},${product.stockStatus}\n")
        }

        try {
            val file = File(context.cacheDir, fileName)
            val outputStream = FileOutputStream(file)
            outputStream.write(csvContent.toString().toByteArray())
            outputStream.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "Share Stock Report").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Chooser failed", e)
                    Toast.makeText(context, "Cannot share report: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Export Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getFormattedMonthlyTextReport(
        monthIndex: Int,
        year: Int,
        originalTools: List<OriginalTool>,
        backupTools: List<BackupTool>,
        breakdownEntries: List<BreakdownEntry>,
        pmEntries: List<PmEntry>
    ): String {
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthName = monthNames.getOrElse(monthIndex) { "Month" }
        val report = StringBuilder()

        val monthlyBreakdowns = breakdownEntries.filter { isDateInMonthAndYear(it.timestamp, monthIndex, year) && !it.entryId.startsWith("BD_AUTO_") }
        val monthlyRepairsCompleted = breakdownEntries.filter { entry ->
            val isHandedOver = entry.status == "Handover Completed" || entry.status == "Closed"
            val matchesMonth = isDateInMonthAndYear(entry.handoverAt, monthIndex, year) ||
                               isDateInMonthAndYear(entry.repairedAt, monthIndex, year) ||
                               isDateInMonthAndYear(entry.timestamp, monthIndex, year)
            isHandedOver && matchesMonth && !entry.entryId.startsWith("BD_AUTO_")
        }
        val monthlyPms = pmEntries.filter { isDateInMonthAndYear(it.scheduledDate, monthIndex, year) || isDateInMonthAndYear(it.completedDate, monthIndex, year) }
        val completedPms = monthlyPms.filter { it.status == "Done" }

        report.append("MONTHLY TOOL MAINTENANCE MIS REPORT\n")
        report.append("Month: $monthName $year\n\n")

        report.append("1. EXECUTIVE SUMMARY\n")
        report.append("- Total Original Tools: ${originalTools.size}\n")
        report.append("- Total Backup Tools: ${backupTools.size}\n")
        report.append("- Total Breakdowns Reported: ${monthlyBreakdowns.size}\n")
        report.append("- Total Repairs Handed Over: ${monthlyRepairsCompleted.size}\n")
        report.append("- Total PMs Scheduled: ${monthlyPms.size}\n")
        report.append("- Total PMs Completed: ${completedPms.size}\n\n")

        report.append("2. MONTHLY BREAKDOWN HIGHLIGHTS\n")
        if (monthlyBreakdowns.isEmpty()) {
            report.append("- No breakdowns recorded in $monthName $year.\n\n")
        } else {
            val problemGroup = monthlyBreakdowns.groupBy { it.problem }
            problemGroup.forEach { (problem, entries) ->
                report.append("• $problem (${entries.size} occurrences):\n")
                entries.forEach { entry ->
                    report.append("  - Tool ${entry.originalToolId} @ ${entry.line}-${entry.station} [Status: ${entry.status}]\n")
                }
            }
            report.append("\n")
        }

        report.append("3. PM MAINTENANCE ROTATION SUMMARY\n")
        report.append("- Completed PMs: ${completedPms.size} / ${monthlyPms.size}\n")
        val pendingPms = monthlyPms.filter { it.status == "Pending" }
        report.append("- Pending PMs: ${pendingPms.size}\n")

        return report.toString()
    }

    suspend fun generateAndShareMonthlyExcelReport(
        context: Context,
        monthIndex: Int,
        year: Int,
        originalTools: List<OriginalTool>,
        backupTools: List<BackupTool>,
        breakdownEntries: List<BreakdownEntry>,
        pmEntries: List<PmEntry>,
        pmHistoryList: List<PmHistoryEntry> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthName = monthNames.getOrElse(monthIndex) { "Month" }

        try {
            val workbook = XSSFWorkbook()

            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                val font = workbook.createFont().apply {
                    color = IndexedColors.WHITE.index
                    bold = true
                }
                setFont(font)
                setBorder(this)
            }

            val bodyStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                val font = workbook.createFont()
                setFont(font)
                setBorder(this)
            }

            val boldStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
                alignment = HorizontalAlignment.CENTER
                setBorder(this)
            }

            // Sheet 1: Monthly Summary & Breakdown Logs
            val sheet1 = workbook.createSheet("Monthly MIS Summary")
            var r1 = 0

            val titleRow = sheet1.createRow(r1++)
            titleRow.createCell(0).apply {
                setCellValue("TOOLCARE MONTHLY MIS REPORT - $monthName $year".uppercase(Locale.ROOT))
                setCellStyle(workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 }) })
            }

            val dateRow = sheet1.createRow(r1++)
            dateRow.createCell(0).setCellValue("Report Month:")
            dateRow.createCell(1).setCellValue("$monthName $year")
            r1++

            val summaryHeader = sheet1.createRow(r1++)
            summaryHeader.createCell(0).apply { setCellValue("Metric Category"); setCellStyle(headerStyle) }
            summaryHeader.createCell(1).apply { setCellValue("Count / Value"); setCellStyle(headerStyle) }

            val monthlyBreakdowns = breakdownEntries.filter { isDateInMonthAndYear(it.timestamp, monthIndex, year) && !it.entryId.startsWith("BD_AUTO_") }
            val monthlyRepairsCompleted = breakdownEntries.filter { entry ->
                val isHandedOver = entry.status == "Handover Completed" || entry.status == "Closed"
                val matchesMonth = isDateInMonthAndYear(entry.handoverAt, monthIndex, year) ||
                                   isDateInMonthAndYear(entry.repairedAt, monthIndex, year) ||
                                   isDateInMonthAndYear(entry.timestamp, monthIndex, year)
                isHandedOver && matchesMonth && !entry.entryId.startsWith("BD_AUTO_")
            }

            val pmRows = buildProcessedPmReportRows(monthIndex, year, pmEntries, pmHistoryList)
            val completedPmCount = pmRows.count { it.status == "Completed" }
            val highRiskCount = originalTools.count { ToolHealthHelper.isHighRisk(it.toolId, breakdownEntries, pmEntries) }

            val metrics = listOf(
                "Total Original Tools Active" to originalTools.size.toString(),
                "Total Backup Tools Available" to backupTools.count { it.status == "Available" }.toString(),
                "Total Monthly Breakdowns Reported" to monthlyBreakdowns.size.toString(),
                "Total Monthly Repairs Completed" to monthlyRepairsCompleted.size.toString(),
                "Total Monthly PMs Completed" to completedPmCount.toString(),
                "High Risk Tools (Frequent Failure)" to highRiskCount.toString()
            )

            metrics.forEach { (label, value) ->
                val row = sheet1.createRow(r1++)
                row.createCell(0).apply { setCellValue(label); setCellStyle(bodyStyle) }
                row.createCell(1).apply { setCellValue(value); setCellStyle(boldStyle) }
            }
            r1 += 2

            sheet1.createRow(r1++).createCell(0).apply { setCellValue("MONTHLY BREAKDOWN REPAIR LOGS"); setCellStyle(headerStyle) }
            val bdHeaders = listOf("No.", "Original Tool ID", "Backup Tool ID", "Line", "Station", "Problem Description", "Reported Time", "Repair Details", "Handover Person", "Shift", "Status")
            val bdHeaderRow = sheet1.createRow(r1++)
            bdHeaders.forEachIndexed { i, h ->
                bdHeaderRow.createCell(i).apply { setCellValue(h); setCellStyle(headerStyle) }
            }

            if (monthlyBreakdowns.isEmpty()) {
                val emptyRow = sheet1.createRow(r1++)
                emptyRow.createCell(0).apply { setCellValue("No breakdowns recorded in $monthName $year"); setCellStyle(bodyStyle) }
            } else {
                monthlyBreakdowns.forEachIndexed { idx, bd ->
                    val row = sheet1.createRow(r1++)
                    row.createCell(0).apply { setCellValue((idx + 1).toDouble()); setCellStyle(bodyStyle) }
                    row.createCell(1).apply { setCellValue(bd.originalToolId); setCellStyle(bodyStyle) }
                    row.createCell(2).apply { setCellValue(bd.backupToolId.ifEmpty { "None" }); setCellStyle(bodyStyle) }
                    row.createCell(3).apply { setCellValue(bd.line); setCellStyle(bodyStyle) }
                    row.createCell(4).apply { setCellValue(bd.station); setCellStyle(bodyStyle) }
                    row.createCell(5).apply { setCellValue(bd.problem); setCellStyle(bodyStyle) }
                    row.createCell(6).apply { setCellValue(bd.timestamp); setCellStyle(bodyStyle) }
                    row.createCell(7).apply { setCellValue(bd.repairDetails.ifEmpty { "N/A" }); setCellStyle(bodyStyle) }
                    row.createCell(8).apply { setCellValue(bd.handoverPerson.ifEmpty { "N/A" }); setCellStyle(bodyStyle) }
                    row.createCell(9).apply { setCellValue(bd.shift.ifEmpty { "N/A" }); setCellStyle(bodyStyle) }
                    row.createCell(10).apply { setCellValue(bd.status); setCellStyle(boldStyle) }
                }
            }

            sheet1.setColumnWidth(0, 8 * 256)
            sheet1.setColumnWidth(1, 18 * 256)
            sheet1.setColumnWidth(2, 18 * 256)
            sheet1.setColumnWidth(3, 12 * 256)
            sheet1.setColumnWidth(4, 12 * 256)
            sheet1.setColumnWidth(5, 25 * 256)
            sheet1.setColumnWidth(6, 20 * 256)
            sheet1.setColumnWidth(7, 25 * 256)
            sheet1.setColumnWidth(8, 18 * 256)
            sheet1.setColumnWidth(9, 12 * 256)
            sheet1.setColumnWidth(10, 20 * 256)

            // Sheet 2: Monthly PM Rotation Logs (12 Columns with Full Completed + Pending + Overdue Merged List)
            val sheet2 = workbook.createSheet("Monthly PM Logs")
            var r2 = 0

            val pmTitleRow = sheet2.createRow(r2++)
            pmTitleRow.createCell(0).apply {
                setCellValue("TOOLCARE PREVENTIVE MAINTENANCE REPORT - $monthName $year".uppercase(Locale.ROOT))
                setCellStyle(workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 }) })
            }

            val pendingCount = pmRows.count { it.status == "Pending" }
            val overdueCount = pmRows.count { it.status == "Overdue" }
            val totalCount = pmRows.size

            val summarySubtitleRow = sheet2.createRow(r2++)
            summarySubtitleRow.createCell(0).setCellValue("Summary: Completed = $completedPmCount | Pending = $pendingCount | Overdue = $overdueCount | Total Records = $totalCount")
            r2++

            val pmHeaders = listOf("No.", "PM / History ID", "Tool ID", "Tool Name", "Line", "Station Number", "Scheduled Date", "Completed Date", "Completed By", "Status", "Spare Parts Used", "Remarks")
            val pmHeaderRow = sheet2.createRow(r2++)
            pmHeaders.forEachIndexed { i, h ->
                pmHeaderRow.createCell(i).apply { setCellValue(h); setCellStyle(headerStyle) }
            }

            if (pmRows.isEmpty()) {
                val emptyRow = sheet2.createRow(r2++)
                emptyRow.createCell(0).apply { setCellValue("No PM entries scheduled or completed in $monthName $year"); setCellStyle(bodyStyle) }
            } else {
                pmRows.forEach { pmRow ->
                    val row = sheet2.createRow(r2++)
                    row.createCell(0).apply { setCellValue(pmRow.indexNo.toDouble()); setCellStyle(bodyStyle) }
                    row.createCell(1).apply { setCellValue(pmRow.id); setCellStyle(bodyStyle) }
                    row.createCell(2).apply { setCellValue(pmRow.toolId); setCellStyle(bodyStyle) }
                    row.createCell(3).apply { setCellValue(pmRow.toolName); setCellStyle(bodyStyle) }
                    row.createCell(4).apply { setCellValue(pmRow.line); setCellStyle(bodyStyle) }
                    row.createCell(5).apply { setCellValue(pmRow.station); setCellStyle(bodyStyle) }
                    row.createCell(6).apply { setCellValue(pmRow.scheduledDate); setCellStyle(bodyStyle) }
                    row.createCell(7).apply { setCellValue(pmRow.completedDate); setCellStyle(bodyStyle) }
                    row.createCell(8).apply { setCellValue(pmRow.completedBy); setCellStyle(bodyStyle) }
                    row.createCell(9).apply { setCellValue(pmRow.status); setCellStyle(boldStyle) }
                    row.createCell(10).apply { setCellValue(pmRow.partsUsed); setCellStyle(bodyStyle) }
                    row.createCell(11).apply { setCellValue(pmRow.remarks); setCellStyle(bodyStyle) }
                }
            }

            r2++
            val pmBottomSummaryRow = sheet2.createRow(r2++)
            pmBottomSummaryRow.createCell(0).apply { setCellValue("SUMMARY"); setCellStyle(boldStyle) }
            pmBottomSummaryRow.createCell(1).apply { setCellValue("Completed = $completedPmCount | Pending = $pendingCount | Overdue = $overdueCount | Total Records = $totalCount"); setCellStyle(boldStyle) }

            sheet2.setColumnWidth(0, 8 * 256)
            sheet2.setColumnWidth(1, 24 * 256)
            sheet2.setColumnWidth(2, 15 * 256)
            sheet2.setColumnWidth(3, 22 * 256)
            sheet2.setColumnWidth(4, 12 * 256)
            sheet2.setColumnWidth(5, 12 * 256)
            sheet2.setColumnWidth(6, 16 * 256)
            sheet2.setColumnWidth(7, 18 * 256)
            sheet2.setColumnWidth(8, 18 * 256)
            sheet2.setColumnWidth(9, 15 * 256)
            sheet2.setColumnWidth(10, 25 * 256)
            sheet2.setColumnWidth(11, 25 * 256)

            val fileName = "ToolCare_Monthly_MIS_${monthName}_${year}.xlsx"
            val file = File(context.cacheDir, fileName)
            val out = FileOutputStream(file)
            workbook.write(out)
            out.close()
            workbook.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Monthly MIS Report ($monthName $year)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Chooser failed", e)
                    Toast.makeText(context, "Cannot share file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Error generating Monthly Excel report", t)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Monthly Excel Export Failed: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun generateAndShareMonthlyPMExcelReport(
        context: Context,
        monthIndex: Int,
        year: Int,
        pmEntries: List<PmEntry> = emptyList(),
        pmHistoryList: List<PmHistoryEntry> = emptyList()
    ) = withContext(Dispatchers.IO) {
        val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        val monthName = monthNames.getOrElse(monthIndex) { "Month" }

        val pmRows = buildProcessedPmReportRows(monthIndex, year, pmEntries, pmHistoryList)
        val completedCount = pmRows.count { it.status == "Completed" }
        val pendingCount = pmRows.count { it.status == "Pending" }
        val overdueCount = pmRows.count { it.status == "Overdue" }
        val totalRecordsCount = pmRows.size

        val isValid = (completedCount + pendingCount + overdueCount) == totalRecordsCount
        Log.d("MISReportHelper", "PM Export Validation: $completedCount + $pendingCount + $overdueCount == $totalRecordsCount (Valid = $isValid)")

        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Monthly PM Report")

            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                val font = workbook.createFont().apply {
                    color = IndexedColors.WHITE.index
                    bold = true
                }
                setFont(font)
                setBorder(this)
            }

            val bodyStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                val font = workbook.createFont()
                setFont(font)
                setBorder(this)
            }

            val boldStyle = workbook.createCellStyle().apply {
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
                alignment = HorizontalAlignment.CENTER
                setBorder(this)
            }

            var r = 0

            val titleRow = sheet.createRow(r++)
            titleRow.createCell(0).apply {
                setCellValue("TOOLCARE PREVENTIVE MAINTENANCE REPORT - $monthName $year".uppercase(Locale.ROOT))
                setCellStyle(workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 }) })
            }

            val subtitleRow = sheet.createRow(r++)
            subtitleRow.createCell(0).setCellValue("Summary: Completed = $completedCount | Pending = $pendingCount | Overdue = $overdueCount | Total Records = $totalRecordsCount")
            r++

            val headers = listOf("No.", "PM / History ID", "Tool ID", "Tool Name", "Line", "Station Number", "Scheduled Date", "Completed Date", "Completed By", "Status", "Spare Parts Used", "Remarks")
            val headerRow = sheet.createRow(r++)
            headers.forEachIndexed { i, h ->
                headerRow.createCell(i).apply { setCellValue(h); setCellStyle(headerStyle) }
            }

            if (pmRows.isEmpty()) {
                val emptyRow = sheet.createRow(r++)
                emptyRow.createCell(0).apply { setCellValue("No PM records found for $monthName $year"); setCellStyle(bodyStyle) }
            } else {
                pmRows.forEach { pmRow ->
                    val row = sheet.createRow(r++)
                    row.createCell(0).apply { setCellValue(pmRow.indexNo.toDouble()); setCellStyle(bodyStyle) }
                    row.createCell(1).apply { setCellValue(pmRow.id); setCellStyle(bodyStyle) }
                    row.createCell(2).apply { setCellValue(pmRow.toolId); setCellStyle(bodyStyle) }
                    row.createCell(3).apply { setCellValue(pmRow.toolName); setCellStyle(bodyStyle) }
                    row.createCell(4).apply { setCellValue(pmRow.line); setCellStyle(bodyStyle) }
                    row.createCell(5).apply { setCellValue(pmRow.station); setCellStyle(bodyStyle) }
                    row.createCell(6).apply { setCellValue(pmRow.scheduledDate); setCellStyle(bodyStyle) }
                    row.createCell(7).apply { setCellValue(pmRow.completedDate); setCellStyle(bodyStyle) }
                    row.createCell(8).apply { setCellValue(pmRow.completedBy); setCellStyle(bodyStyle) }
                    row.createCell(9).apply { setCellValue(pmRow.status); setCellStyle(boldStyle) }
                    row.createCell(10).apply { setCellValue(pmRow.partsUsed); setCellStyle(bodyStyle) }
                    row.createCell(11).apply { setCellValue(pmRow.remarks); setCellStyle(bodyStyle) }
                }
            }

            r++
            val summaryRow = sheet.createRow(r++)
            summaryRow.createCell(0).apply { setCellValue("SUMMARY"); setCellStyle(boldStyle) }
            summaryRow.createCell(1).apply { setCellValue("Completed = $completedCount | Pending = $pendingCount | Overdue = $overdueCount | Total Records = $totalRecordsCount"); setCellStyle(boldStyle) }

            sheet.setColumnWidth(0, 8 * 256)
            sheet.setColumnWidth(1, 24 * 256)
            sheet.setColumnWidth(2, 15 * 256)
            sheet.setColumnWidth(3, 22 * 256)
            sheet.setColumnWidth(4, 12 * 256)
            sheet.setColumnWidth(5, 12 * 256)
            sheet.setColumnWidth(6, 16 * 256)
            sheet.setColumnWidth(7, 18 * 256)
            sheet.setColumnWidth(8, 18 * 256)
            sheet.setColumnWidth(9, 15 * 256)
            sheet.setColumnWidth(10, 25 * 256)
            sheet.setColumnWidth(11, 25 * 256)

            val fileName = "ToolCare_PM_Report_${monthName}_${year}.xlsx"
            val file = File(context.cacheDir, fileName)
            val out = FileOutputStream(file)
            workbook.write(out)
            out.close()
            workbook.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share PM Monthly Report ($monthName $year)").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot share report: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Excel Export Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun generateAndShareStockTransactionExcelReport(
        context: Context,
        transactionLogs: List<PartInOutLog>,
        filterLabel: String
    ) = withContext(Dispatchers.IO) {
        try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Stock Transactions")

            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                val font = workbook.createFont().apply {
                    color = IndexedColors.WHITE.index
                    bold = true
                }
                setFont(font)
                setBorder(this)
            }

            val bodyStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                val font = workbook.createFont()
                setFont(font)
                setBorder(this)
            }

            var r = 0

            val titleRow = sheet.createRow(r++)
            titleRow.createCell(0).apply {
                setCellValue("TOOLCARE STOCK TRANSACTIONS & REFILL LOG".uppercase(Locale.ROOT))
                setCellStyle(workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 }) })
            }

            val subtitleRow = sheet.createRow(r++)
            subtitleRow.createCell(0).setCellValue("Filter Range: $filterLabel | Total Records: ${transactionLogs.size}")
            r++

            val headers = listOf("No.", "Timestamp", "Action Type", "Part Number / Code", "Part Name", "Quantity", "Tool D.No.", "Issued To / Supplier", "Reason / Remarks")
            val headerRow = sheet.createRow(r++)
            headers.forEachIndexed { i, h ->
                headerRow.createCell(i).apply { setCellValue(h); setCellStyle(headerStyle) }
            }

            if (transactionLogs.isEmpty()) {
                val emptyRow = sheet.createRow(r++)
                emptyRow.createCell(0).apply { setCellValue("No stock transactions found for $filterLabel"); setCellStyle(bodyStyle) }
            } else {
                transactionLogs.forEachIndexed { idx, log ->
                    val row = sheet.createRow(r++)
                    row.createCell(0).apply { setCellValue((idx + 1).toDouble()); setCellStyle(bodyStyle) }
                    row.createCell(1).apply { setCellValue(log.timestamp); setCellStyle(bodyStyle) }
                    row.createCell(2).apply { setCellValue(log.actionType); setCellStyle(bodyStyle) }
                    row.createCell(3).apply { setCellValue(log.partNo); setCellStyle(bodyStyle) }
                    row.createCell(4).apply { setCellValue(log.partName); setCellStyle(bodyStyle) }
                    row.createCell(5).apply { setCellValue(log.qty.toDouble()); setCellStyle(bodyStyle) }
                    row.createCell(6).apply { setCellValue(log.toolId); setCellStyle(bodyStyle) }
                    row.createCell(7).apply { setCellValue(log.mechanicName); setCellStyle(bodyStyle) }
                    row.createCell(8).apply { setCellValue("${log.reason} ${log.remarks}".trim()); setCellStyle(bodyStyle) }
                }
            }

            for (i in headers.indices) {
                sheet.autoSizeColumn(i)
            }

            val fileName = "ToolCare_Stock_Transactions_${System.currentTimeMillis()}.xlsx"
            val file = File(context.cacheDir, fileName)
            val out = FileOutputStream(file)
            workbook.write(out)
            out.close()
            workbook.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Stock Transactions Excel Report").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Exception) {
                    Log.e("MISReportHelper", "Export stock report failed", e)
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MISReportHelper", "Error generating stock excel report", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Excel export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun generateAndSharePartsInventoryExcelReport(
        context: Context,
        productList: List<Product>,
        filterLabel: String
    ) = withContext(Dispatchers.IO) {
        try {
            val safeList = productList.orEmpty()
            val workbook: Workbook = XSSFWorkbook()
            val sheet: Sheet = workbook.createSheet("Parts Inventory")

            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.getIndex()
            }
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.getIndex()
                fillPattern = FillPatternType.SOLID_FOREGROUND
                setFont(headerFont)
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
            }

            val bodyStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
            }

            val boldStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.CENTER
                setFont(workbook.createFont().apply { bold = true })
            }

            var r = 0

            val titleRow = sheet.createRow(r++)
            titleRow.createCell(0).apply {
                setCellValue("TOOLCARE PARTS INVENTORY REPORT".uppercase(Locale.ROOT))
                cellStyle = workbook.createCellStyle().apply { setFont(workbook.createFont().apply { bold = true; fontHeightInPoints = 14 }) }
            }

            val totalVal = safeList.sumOf { it.currentStock * it.unitPrice }
            val subtitleRow = sheet.createRow(r++)
            subtitleRow.createCell(0).setCellValue("Filter: $filterLabel | Total Items: ${safeList.size} | Total Value: ₹$totalVal")
            r++

            val headers = listOf("No.", "Part Number / Code", "Part Name", "Compatible Model", "Category", "Current Stock", "Min Stock Limit", "Unit Price (₹)", "Total Value (₹)", "Stock Status")
            val headerRow = sheet.createRow(r++)
            headers.forEachIndexed { i, h ->
                headerRow.createCell(i).apply { setCellValue(h); cellStyle = headerStyle }
            }

            if (safeList.isEmpty()) {
                val emptyRow = sheet.createRow(r++)
                emptyRow.createCell(0).apply { setCellValue("No parts found matching filter criteria ($filterLabel)"); cellStyle = bodyStyle }
            } else {
                safeList.forEachIndexed { idx, prod ->
                    val statusStr = when {
                        prod.currentStock <= 0 -> "Out of Stock"
                        prod.currentStock <= prod.minStockLevel -> "Low Stock"
                        else -> "Available"
                    }

                    val row = sheet.createRow(r++)
                    row.createCell(0).apply { setCellValue((idx + 1).toDouble()); cellStyle = bodyStyle }
                    row.createCell(1).apply { setCellValue(prod.productCode); cellStyle = bodyStyle }
                    row.createCell(2).apply { setCellValue(prod.productName); cellStyle = bodyStyle }
                    row.createCell(3).apply { setCellValue(prod.toolId.ifEmpty { "All Models" }); cellStyle = bodyStyle }
                    row.createCell(4).apply { setCellValue(prod.category); cellStyle = bodyStyle }
                    row.createCell(5).apply { setCellValue(prod.currentStock.toDouble()); cellStyle = bodyStyle }
                    row.createCell(6).apply { setCellValue(prod.minStockLevel.toDouble()); cellStyle = bodyStyle }
                    row.createCell(7).apply { setCellValue(prod.unitPrice); cellStyle = bodyStyle }
                    row.createCell(8).apply { setCellValue(prod.currentStock * prod.unitPrice); cellStyle = bodyStyle }
                    row.createCell(9).apply { setCellValue(statusStr); cellStyle = boldStyle }
                }
            }

            sheet.setColumnWidth(0, 8 * 256)
            sheet.setColumnWidth(1, 20 * 256)
            sheet.setColumnWidth(2, 22 * 256)
            sheet.setColumnWidth(3, 18 * 256)
            sheet.setColumnWidth(4, 15 * 256)
            sheet.setColumnWidth(5, 14 * 256)
            sheet.setColumnWidth(6, 14 * 256)
            sheet.setColumnWidth(7, 14 * 256)
            sheet.setColumnWidth(8, 16 * 256)
            sheet.setColumnWidth(9, 15 * 256)

            val fileName = "ToolCare_Parts_Inventory_${filterLabel.replace(" ", "_")}_${System.currentTimeMillis()}.xlsx"
            val file = File(context.cacheDir, fileName)
            val out = FileOutputStream(file)
            workbook.write(out)
            out.close()
            workbook.close()

            val authority = "${context.applicationContext.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, file)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share Parts Inventory Excel Report").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val resInfoList = context.packageManager.queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val pkgName = resolveInfo.activityInfo.packageName
                try {
                    context.grantUriPermission(pkgName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                } catch (e: Throwable) {
                    Log.e("MISReportHelper", "Export parts inventory report failed", e)
                    Toast.makeText(context, "Cannot share report: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            Log.e("MISReportHelper", "Error generating parts inventory excel report", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Excel export error: ${e.localizedMessage ?: "Unknown Error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportMisDialog(
    productList: List<Product>,
    onDismiss: () -> Unit,
    onExport: (List<RequiredPartItem>, ExportFormat) -> Unit
) {
    val defaultStockProducts = remember {
        listOf(
            Product(productName = "Switch", productCode = "3607031884", currentStock = 12, minStockLevel = 2, unitPrice = 450.0),
            Product(productName = "Gearbox", productCode = "1600xxxx", currentStock = 8, minStockLevel = 1, unitPrice = 1200.0),
            Product(productName = "Motor", productCode = "2600xxxx", currentStock = 15, minStockLevel = 3, unitPrice = 2500.0),
            Product(productName = "Capacitor", productCode = "104xxxx", currentStock = 25, minStockLevel = 5, unitPrice = 180.0),
            Product(productName = "Carbon Brush", productCode = "1607014176", currentStock = 30, minStockLevel = 10, unitPrice = 120.0),
            Product(productName = "Armature", productCode = "1604010649", currentStock = 5, minStockLevel = 2, unitPrice = 1800.0),
            Product(productName = "Power Cord", productCode = "1607000227", currentStock = 20, minStockLevel = 4, unitPrice = 350.0),
            Product(productName = "Bearing 608ZZ", productCode = "2609110152", currentStock = 40, minStockLevel = 8, unitPrice = 90.0)
        )
    }

    val availableStockList = remember(productList) {
        if (productList.isNotEmpty()) productList else defaultStockProducts
    }

    // Requirement 1: Initialize selectedParts as completely EMPTY by default
    val selectedParts = remember {
        mutableStateListOf<RequiredPartItem>()
    }

    val stagedParts = remember { mutableStateListOf<Product>() }
    val stagedQuantities = remember { mutableStateMapOf<String, Int>() }

    var stockDropdownExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
                .imePadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1D4ED8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Select Required Parts\nfor Report",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B),
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select multiple parts from stock and add their quantity. These parts will be populated into the 'These Part are required' section of the Daily MIS Excel Report.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 15.sp
                        )
                    }
                }

                // Section 1: Select Parts from Stock
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEFF6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = "Select Parts from Stock",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFEFF6FF)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF1D4ED8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Select Multiple",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1D4ED8)
                                    )
                                }
                            }
                        }

                        // Multi-Select Box showing Chips
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedCard(
                                onClick = { stockDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FlowRow(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (stagedParts.isEmpty()) {
                                            Text(
                                                text = "Tap to choose stock parts...",
                                                fontSize = 12.sp,
                                                color = Color(0xFF94A3B8),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            stagedParts.forEach { part ->
                                                Surface(
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = Color(0xFFEFF6FF),
                                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Text(
                                                            text = part.productName,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF1E40AF)
                                                        )
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Remove",
                                                            tint = Color(0xFF1E40AF),
                                                            modifier = Modifier
                                                                .size(14.dp)
                                                                .clickable {
                                                                    stagedParts.remove(part)
                                                                    stagedQuantities.remove(part.productCode)
                                                                }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Multi-Select Dropdown Menu
                            DropdownMenu(
                                expanded = stockDropdownExpanded,
                                onDismissRequest = { stockDropdownExpanded = false },
                                properties = PopupProperties(focusable = false),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .heightIn(max = 280.dp)
                                    .background(Color.White)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search inventory parts...", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )

                                val filteredStock = availableStockList.filter {
                                    it.productName.contains(searchQuery, ignoreCase = true) ||
                                    it.productCode.contains(searchQuery, ignoreCase = true)
                                }

                                if (filteredStock.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No matching parts found", fontSize = 12.sp, color = Color.Gray) },
                                        onClick = { stockDropdownExpanded = false }
                                    )
                                } else {
                                    filteredStock.forEach { prod ->
                                        val isAlreadyInFinalList = selectedParts.any {
                                            it.partName.equals(prod.productName, ignoreCase = true) || it.partNo == prod.productCode
                                        }
                                        val isStaged = stagedParts.any { it.productCode == prod.productCode }

                                        DropdownMenuItem(
                                            enabled = !isAlreadyInFinalList,
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = prod.productName,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = if (isAlreadyInFinalList) Color.Gray else Color(0xFF1E293B)
                                                        )
                                                        Text(
                                                            text = "No: ${prod.productCode} | Stock: ${prod.currentStock}",
                                                            fontSize = 10.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                    if (isAlreadyInFinalList) {
                                                        Text("Already Added", fontSize = 10.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                                                    } else {
                                                        Checkbox(
                                                            checked = isStaged,
                                                            onCheckedChange = { checked ->
                                                                if (checked) {
                                                                    if (!isStaged) {
                                                                        stagedParts.add(prod)
                                                                        stagedQuantities[prod.productCode] = 1
                                                                    }
                                                                } else {
                                                                    stagedParts.removeAll { it.productCode == prod.productCode }
                                                                    stagedQuantities.remove(prod.productCode)
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                if (!isAlreadyInFinalList) {
                                                    if (isStaged) {
                                                        stagedParts.removeAll { it.productCode == prod.productCode }
                                                        stagedQuantities.remove(prod.productCode)
                                                    } else {
                                                        stagedParts.add(prod)
                                                        stagedQuantities[prod.productCode] = 1
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2: Set Quantity for Each Part
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("#", color = Color(0xFF1D4ED8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "Set Quantity for Each Part",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }

                        if (stagedParts.isEmpty()) {
                            Text(
                                text = "Select parts from stock above to set quantities.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                stagedParts.forEach { part ->
                                    val currentQty = stagedQuantities[part.productCode] ?: 1
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.width(80.dp)
                                    ) {
                                        Text(
                                            text = part.productName,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF334155),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        OutlinedTextField(
                                            value = currentQty.toString(),
                                            onValueChange = { input ->
                                                val cleanVal = input.filter { it.isDigit() }.toIntOrNull() ?: 1
                                                stagedQuantities[part.productCode] = cleanVal.coerceAtLeast(1)
                                            },
                                            modifier = Modifier
                                                .width(72.dp)
                                                .height(48.dp),
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                textAlign = TextAlign.Center,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Parts To List Button
                Button(
                    onClick = {
                        if (stagedParts.isNotEmpty()) {
                            stagedParts.forEach { part ->
                                val qty = stagedQuantities[part.productCode] ?: 1
                                if (selectedParts.none { it.partName.equals(part.productName, ignoreCase = true) }) {
                                    selectedParts.add(RequiredPartItem(part.productName, part.productCode, qty))
                                }
                            }
                            stagedParts.clear()
                            stagedQuantities.clear()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Parts To List", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Section 3: Selected Parts List / Table
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.List,
                                    contentDescription = null,
                                    tint = Color(0xFF1D4ED8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Selected Parts (${selectedParts.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }

                            if (selectedParts.isNotEmpty()) {
                                TextButton(
                                    onClick = { selectedParts.clear() },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Clear All", fontSize = 11.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Table Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Part Name", modifier = Modifier.weight(1.3f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            Text("Part Number", modifier = Modifier.weight(1.2f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                            Text("Qty", modifier = Modifier.weight(0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), textAlign = TextAlign.Center)
                            Text("", modifier = Modifier.width(28.dp))
                        }

                        HorizontalDivider(color = Color(0xFFE2E8F0))

                        if (selectedParts.isEmpty()) {
                            Text("No required parts added yet. (Will be exported blank)", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                        } else {
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                selectedParts.forEachIndexed { index, item ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1.3f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEFF6FF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Build,
                                                        contentDescription = null,
                                                        tint = Color(0xFF1D4ED8),
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                                Text(
                                                    text = item.partName,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1E293B),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = item.partNo,
                                                modifier = Modifier.weight(1.2f),
                                                fontSize = 10.sp,
                                                color = Color(0xFF475569),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center,
                                                modifier = Modifier.weight(0.7f)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        if (item.quantity > 1) {
                                                            selectedParts[index] = item.copy(quantity = item.quantity - 1)
                                                        }
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Text("-", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = Color(0xFFEFF6FF)
                                                ) {
                                                    Text(
                                                        text = "${item.quantity}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1D4ED8),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                        textAlign = TextAlign.Center
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        selectedParts[index] = item.copy(quantity = item.quantity + 1)
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Text("+", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8))
                                                }
                                            }

                                            IconButton(
                                                onClick = { selectedParts.removeAt(index) },
                                                modifier = Modifier.size(26.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dual Export Options: Excel (.xlsx) and Image (.png)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onExport(selectedParts.toList(), ExportFormat.EXCEL) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GENERATE & EXPORT EXCEL (.xlsx)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = { onExport(selectedParts.toList(), ExportFormat.IMAGE) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GENERATE & EXPORT AS PHOTO / IMAGE (.png)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Cancel Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Cancel",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
