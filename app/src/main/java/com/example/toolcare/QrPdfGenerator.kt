package com.example.toolcare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object QrPdfGenerator {

    fun generateQrMatrixBitmap(text: String, size: Int = 200): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint().apply { color = Color.WHITE }
        val qrPaint = Paint().apply { color = Color.parseColor("#0F172A") }

        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), bgPaint)

        // Draw outer QR position finder patterns (Top-Left, Top-Right, Bottom-Left)
        fun drawFinderPattern(x: Float, y: Float, boxSize: Float) {
            val outer = Paint().apply { color = Color.parseColor("#0F172A") }
            val innerWhite = Paint().apply { color = Color.WHITE }
            val innerBlack = Paint().apply { color = Color.parseColor("#0F172A") }

            canvas.drawRect(x, y, x + boxSize, y + boxSize, outer)
            val margin = boxSize * 0.15f
            canvas.drawRect(x + margin, y + margin, x + boxSize - margin, y + boxSize - margin, innerWhite)
            val coreMargin = boxSize * 0.30f
            canvas.drawRect(x + coreMargin, y + coreMargin, x + boxSize - coreMargin, y + boxSize - coreMargin, innerBlack)
        }

        val box = size * 0.28f
        drawFinderPattern(10f, 10f, box)
        drawFinderPattern(size - box - 10f, 10f, box)
        drawFinderPattern(10f, size - box - 10f, box)

        // Pseudo-random data modules derived from text hash code for visual barcode representation
        val gridCount = 21
        val moduleSize = size.toFloat() / gridCount
        val seed = text.hashCode()

        for (row in 0 until gridCount) {
            for (col in 0 until gridCount) {
                // Skip finder pattern zones
                if ((row < 7 && col < 7) || (row < 7 && col > 13) || (row > 13 && col < 7)) continue

                val valHash = (seed + row * 31 + col * 17) % 3
                if (valHash == 0) {
                    canvas.drawRect(
                        col * moduleSize,
                        row * moduleSize,
                        (col + 1) * moduleSize,
                        (row + 1) * moduleSize,
                        qrPaint
                    )
                }
            }
        }

        return bitmap
    }

    fun generateToolQrLabelPdf(
        context: Context,
        tools: List<OriginalTool>,
        company: String,
        block: String
    ): File? {
        return try {
            val pdfDoc = PdfDocument()
            val pageWidth = 595 // A4 Width
            val pageHeight = 842 // A4 Height

            val labelWidth = 260
            val labelHeight = 160
            val marginX = 25
            val marginY = 30
            val labelsPerRow = 2
            val labelsPerPage = 8

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = pdfDoc.startPage(pageInfo)
            var canvas = page.canvas

            val borderPaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            val bgPaint = Paint().apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }

            val headerBg = Paint().apply {
                color = Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }

            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 13f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 16f
                isFakeBoldText = true
                isAntiAlias = true
            }

            val detailPaint = Paint().apply {
                color = Color.parseColor("#475569")
                textSize = 11f
                isAntiAlias = true
            }

            val boldDetailPaint = Paint().apply {
                color = Color.parseColor("#2563EB")
                textSize = 11f
                isFakeBoldText = true
                isAntiAlias = true
            }

            tools.forEachIndexed { index, tool ->
                val pageIndex = index % labelsPerPage
                if (index > 0 && pageIndex == 0) {
                    pdfDoc.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = pdfDoc.startPage(pageInfo)
                    canvas = page.canvas
                }

                val row = (pageIndex / labelsPerRow)
                val col = (pageIndex % labelsPerRow)

                val x = marginX + col * (labelWidth + 25)
                val y = marginY + row * (labelHeight + 25)

                canvas.drawRect(x.toFloat(), y.toFloat(), (x + labelWidth).toFloat(), (y + labelHeight).toFloat(), bgPaint)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + labelWidth).toFloat(), (y + labelHeight).toFloat(), borderPaint)

                canvas.drawRect(x.toFloat(), y.toFloat(), (x + labelWidth).toFloat(), (y + 30).toFloat(), headerBg)
                canvas.drawText("TOOLCARE  •  $company ($block)", (x + 10).toFloat(), (y + 20).toFloat(), headerTextPaint)

                val qrBmp = generateQrMatrixBitmap("TOOLCARE:${tool.toolId}", 110)
                canvas.drawBitmap(qrBmp, (x + 10).toFloat(), (y + 40).toFloat(), null)

                val textX = (x + 130).toFloat()
                canvas.drawText(tool.toolId, textX, (y + 58).toFloat(), titlePaint)
                canvas.drawText(tool.toolName.take(18), textX, (y + 78).toFloat(), detailPaint)
                canvas.drawText("Line: ${tool.line.take(12)}", textX, (y + 98).toFloat(), detailPaint)
                canvas.drawText("Station: ${tool.station.take(12)}", textX, (y + 118).toFloat(), detailPaint)
                canvas.drawText("Torque: ${tool.torque.ifEmpty { "12 Nm" }}", textX, (y + 138).toFloat(), boldDetailPaint)
            }

            pdfDoc.finishPage(page)

            val pdfFile = File(context.cacheDir, "ToolCare_QR_Labels_${System.currentTimeMillis()}.pdf")
            val fos = FileOutputStream(pdfFile)
            pdfDoc.writeTo(fos)
            fos.close()
            pdfDoc.close()

            pdfFile
        } catch (e: Exception) {
            Log.e("QrPdfGenerator", "Failed to generate PDF labels", e)
            null
        }
    }
}
