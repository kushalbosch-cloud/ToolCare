package com.example.toolcare

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object BackupRestoreHelper {

    private const val TAG = "BackupRestoreHelper"

    suspend fun exportWorkspaceBackup(
        context: Context,
        company: String,
        block: String,
        originalTools: List<OriginalTool>,
        backupTools: List<BackupTool>,
        breakdownEntries: List<BreakdownEntry>,
        pmEntries: List<PmEntry>,
        products: List<Product>
    ) = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("company", company)
            root.put("block", block)
            root.put("exportTime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            // Original Tools
            val origArray = JSONArray()
            originalTools.forEach { t ->
                val obj = JSONObject()
                obj.put("toolId", t.toolId)
                obj.put("toolName", t.toolName)
                obj.put("line", t.line)
                obj.put("station", t.station)
                obj.put("status", t.status)
                obj.put("model", t.model)
                obj.put("serialNumber", t.serialNumber)
                obj.put("torque", t.torque)
                obj.put("rpm", t.rpm)
                obj.put("application", t.application)
                obj.put("installDate", t.installDate)
                obj.put("company", t.company)
                obj.put("block", t.block)
                origArray.put(obj)
            }
            root.put("originalTools", origArray)

            // Backup Tools
            val backArray = JSONArray()
            backupTools.forEach { b ->
                val obj = JSONObject()
                obj.put("toolId", b.toolId)
                obj.put("model", b.model)
                obj.put("serialNumber", b.serialNumber)
                obj.put("status", b.status)
                obj.put("company", b.company)
                obj.put("block", b.block)
                backArray.put(obj)
            }
            root.put("backupTools", backArray)

            // Breakdown Entries
            val bdArray = JSONArray()
            breakdownEntries.forEach { bd ->
                val obj = JSONObject()
                obj.put("entryId", bd.entryId)
                obj.put("originalToolId", bd.originalToolId)
                obj.put("backupToolId", bd.backupToolId)
                obj.put("line", bd.line)
                obj.put("station", bd.station)
                obj.put("problem", bd.problem)
                obj.put("timestamp", bd.timestamp)
                obj.put("status", bd.status)
                obj.put("repairDetails", bd.repairDetails)
                obj.put("handoverPerson", bd.handoverPerson)
                obj.put("shift", bd.shift)
                obj.put("company", bd.company)
                obj.put("block", bd.block)
                bdArray.put(obj)
            }
            root.put("breakdownEntries", bdArray)

            // PM Entries
            val pmArray = JSONArray()
            pmEntries.forEach { pm ->
                val obj = JSONObject()
                obj.put("pmId", pm.pmId)
                obj.put("toolId", pm.toolId)
                obj.put("toolName", pm.toolName)
                obj.put("line", pm.line)
                obj.put("station", pm.station)
                obj.put("scheduledDate", pm.scheduledDate)
                obj.put("status", pm.status)
                obj.put("partsUsed", pm.partsUsed)
                obj.put("completedDate", pm.completedDate)
                obj.put("company", pm.company)
                obj.put("block", pm.block)
                pmArray.put(obj)
            }
            root.put("pmEntries", pmArray)

            // Products / Inventory
            val prodArray = JSONArray()
            products.forEach { p ->
                val obj = JSONObject()
                obj.put("productCode", p.productCode)
                obj.put("productName", p.productName)
                obj.put("toolId", p.toolId)
                obj.put("category", p.category)
                obj.put("unitOfMeasure", p.unitOfMeasure)
                obj.put("unitPrice", p.unitPrice)
                obj.put("openingStock", p.openingStock)
                obj.put("minStockLevel", p.minStockLevel)
                obj.put("currentStock", p.currentStock)
                prodArray.put(obj)
            }
            root.put("products", prodArray)

            val timeStr = SimpleDateFormat("ddMMyyyy_HHmm", Locale.getDefault()).format(Date())
            val fileName = "ToolCare_Backup_${company}_${block}_${timeStr}.json"
            val file = File(context.cacheDir, fileName)

            val out = FileOutputStream(file)
            out.write(root.toString(2).toByteArray())
            out.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Save/Export ToolCare Backup File").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                    Toast.makeText(context, "Backup file exported successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to share backup file", e)
                    Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Backup export failed", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Backup Export Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    suspend fun importRestoreBackup(
        context: Context,
        jsonContent: String,
        firebaseManager: FirebaseManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonContent)

            // Restore Original Tools
            val origArray = root.optJSONArray("originalTools")
            if (origArray != null) {
                for (i in 0 until origArray.length()) {
                    val obj = origArray.getJSONObject(i)
                    val tool = OriginalTool(
                        toolId = obj.optString("toolId"),
                        toolName = obj.optString("toolName"),
                        line = obj.optString("line"),
                        station = obj.optString("station"),
                        status = obj.optString("status", "OK"),
                        model = obj.optString("model"),
                        serialNumber = obj.optString("serialNumber"),
                        torque = obj.optString("torque"),
                        rpm = obj.optString("rpm"),
                        application = obj.optString("application"),
                        installDate = obj.optString("installDate"),
                        company = obj.optString("company", "Mahindra"),
                        block = obj.optString("block", "V-Block")
                    )
                    if (tool.toolId.isNotEmpty()) {
                        firebaseManager.saveOriginalTool(tool)
                    }
                }
            }

            // Restore Backup Tools
            val backArray = root.optJSONArray("backupTools")
            if (backArray != null) {
                for (i in 0 until backArray.length()) {
                    val obj = backArray.getJSONObject(i)
                    val tool = BackupTool(
                        toolId = obj.optString("toolId"),
                        model = obj.optString("model"),
                        serialNumber = obj.optString("serialNumber"),
                        status = obj.optString("status", "Available"),
                        company = obj.optString("company", "Mahindra"),
                        block = obj.optString("block", "V-Block")
                    )
                    if (tool.toolId.isNotEmpty()) {
                        firebaseManager.saveBackupTool(tool)
                    }
                }
            }

            // Restore Breakdown Entries
            val bdArray = root.optJSONArray("breakdownEntries")
            if (bdArray != null) {
                for (i in 0 until bdArray.length()) {
                    val obj = bdArray.getJSONObject(i)
                    val bd = BreakdownEntry(
                        entryId = obj.optString("entryId"),
                        originalToolId = obj.optString("originalToolId"),
                        backupToolId = obj.optString("backupToolId"),
                        line = obj.optString("line"),
                        station = obj.optString("station"),
                        problem = obj.optString("problem"),
                        timestamp = obj.optString("timestamp"),
                        status = obj.optString("status"),
                        repairDetails = obj.optString("repairDetails"),
                        handoverPerson = obj.optString("handoverPerson"),
                        shift = obj.optString("shift"),
                        company = obj.optString("company", "Mahindra"),
                        block = obj.optString("block", "V-Block")
                    )
                    if (bd.originalToolId.isNotEmpty()) {
                        firebaseManager.saveBreakdownEntry(bd)
                    }
                }
            }

            // Restore PM Entries
            val pmArray = root.optJSONArray("pmEntries")
            if (pmArray != null) {
                for (i in 0 until pmArray.length()) {
                    val obj = pmArray.getJSONObject(i)
                    val pm = PmEntry(
                        pmId = obj.optString("pmId"),
                        toolId = obj.optString("toolId"),
                        toolName = obj.optString("toolName"),
                        line = obj.optString("line"),
                        station = obj.optString("station"),
                        scheduledDate = obj.optString("scheduledDate"),
                        status = obj.optString("status"),
                        partsUsed = obj.optString("partsUsed"),
                        completedDate = obj.optString("completedDate"),
                        company = obj.optString("company", "Mahindra"),
                        block = obj.optString("block", "V-Block")
                    )
                    if (pm.pmId.isNotEmpty() || pm.toolId.isNotEmpty()) {
                        firebaseManager.savePmEntry(pm)
                    }
                }
            }

            // Restore Products / Inventory
            val prodArray = root.optJSONArray("products")
            if (prodArray != null) {
                for (i in 0 until prodArray.length()) {
                    val obj = prodArray.getJSONObject(i)
                    val prod = Product(
                        productCode = obj.optString("productCode"),
                        productName = obj.optString("productName"),
                        toolId = obj.optString("toolId", "T-025"),
                        category = obj.optString("category"),
                        unitOfMeasure = obj.optString("unitOfMeasure", "Nos"),
                        unitPrice = obj.optDouble("unitPrice", 0.0),
                        openingStock = obj.optInt("openingStock", 0),
                        minStockLevel = obj.optInt("minStockLevel", 5),
                        currentStock = obj.optInt("currentStock", 0)
                    )
                    if (prod.productCode.isNotEmpty()) {
                        firebaseManager.saveProduct(prod)
                    }
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore import error", e)
            false
        }
    }
}
