package com.example.toolcare

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

object CsvHelper {

    fun exportOriginalToolsCsv(context: Context, tools: List<OriginalTool>): File? {
        return try {
            val file = File(context.cacheDir, "Original_Tools_Export_${System.currentTimeMillis()}.csv")
            val fos = FileOutputStream(file)
            val sb = java.lang.StringBuilder()

            sb.append("Tool ID,Tool Name,Make,Model,Serial Number,Line,Station,Status,Torque,RPM,Company,Block\n")
            tools.forEach { t ->
                sb.append("\"${t.toolId}\",\"${t.toolName}\",\"${t.make}\",\"${t.model}\",\"${t.serialNumber}\",\"${t.line}\",\"${t.station}\",\"${t.status}\",\"${t.torque}\",\"${t.rpm}\",\"${t.company}\",\"${t.block}\"\n")
            }

            fos.write(sb.toString().toByteArray())
            fos.close()
            file
        } catch (e: Exception) {
            Log.e("CsvHelper", "Failed to export tools CSV", e)
            null
        }
    }

    fun parseOriginalToolsCsv(context: Context, uri: Uri, company: String, block: String): List<OriginalTool> {
        val list = mutableListOf<OriginalTool>()
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine() // Header row

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val tokens = line!!.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex()).map { it.replace("\"", "").trim() }
                if (tokens.isNotEmpty() && tokens[0].isNotBlank()) {
                    list.add(
                        OriginalTool(
                            toolId = tokens.getOrElse(0) { "" },
                            toolName = tokens.getOrElse(1) { "Bosch Tool" },
                            make = tokens.getOrElse(2) { "Bosch" },
                            model = tokens.getOrElse(3) { "" },
                            serialNumber = tokens.getOrElse(4) { "" },
                            line = tokens.getOrElse(5) { "Assembly-1" },
                            station = tokens.getOrElse(6) { "ST-01" },
                            status = tokens.getOrElse(7) { "OK" },
                            torque = tokens.getOrElse(8) { "12 Nm" },
                            rpm = tokens.getOrElse(9) { "2700" },
                            company = company,
                            block = block
                        )
                    )
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("CsvHelper", "Failed to parse tools CSV", e)
        }
        return list
    }
}
