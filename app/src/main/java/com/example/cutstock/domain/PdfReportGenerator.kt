package com.example.cutstock.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.cutstock.data.DemandInput
import com.example.cutstock.data.ProjectSettings
import com.example.cutstock.nativecore.CuttingPlan
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.Locale

class PdfReportGenerator(private val context: Context) {

    private val decimalFormat = DecimalFormat("#,###")

    fun generate(
        projectName: String,
        settings: ProjectSettings,
        demands: List<DemandInput>,
        plan: CuttingPlan,
        sales: SalesSummary
    ): File {
        val document = PdfDocument()
        val pageWidth = PAGE_WIDTH_PT
        val pageHeight = PAGE_HEIGHT_PT
        val margin = MARGIN_PT

        var pageNumber = 1
        var y = margin.toFloat()
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun newPageIfNeeded(requiredHeight: Float) {
            if (y + requiredHeight <= pageHeight - margin) return
            document.finishPage(page)
            pageNumber += 1
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = margin.toFloat()
        }

        y = drawTitle(canvas, margin.toFloat(), y, projectName)
        y += SECTION_GAP

        y = drawSectionTitle(canvas, margin.toFloat(), y, "خلاصه پروژه")
        y = drawLine(
            canvas,
            margin.toFloat(),
            y,
            pageWidth - margin,
            "طول‌های stock: ${settings.stockLengthsMm.joinToString(", ")} mm"
        )
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "kerf: ${settings.kerfMm} mm")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "قطر: ${settings.diameterMm} mm")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "تعداد میلگرد: ${sales.barsNeeded}")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "درصد ضایعات: ${formatPercent(sales.wastePercent)}")
        y = drawLine(
            canvas,
            margin.toFloat(),
            y,
            pageWidth - margin,
            "صرفه‌جویی: ${decimalFormat.format(sales.moneySavedTomans)} تومان"
        )
        y += SECTION_GAP

        y = drawSectionTitle(canvas, margin.toFloat(), y, "لیست تقاضا")
        demands.forEach { demand ->
            newPageIfNeeded(LINE_HEIGHT)
            y = drawLine(
                canvas,
                margin.toFloat(),
                y,
                pageWidth - margin,
                "${demand.lengthMm} میلی‌متر × ${demand.quantity}"
            )
        }
        y += SECTION_GAP

        y = drawSectionTitle(canvas, margin.toFloat(), y, "گزارش پیشرفته")
        y = drawLine(
            canvas,
            margin.toFloat(),
            y,
            pageWidth - margin,
            "میانگین بهره‌وری: ${formatPercent(sales.averageUtilizationPercent)}"
        )
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "بیشترین ضایعات: ${sales.largestWasteMm} میلی‌متر")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "کمترین ضایعات: ${sales.smallestWasteMm} میلی‌متر")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "ضایعات ساده: ${formatKg(sales.naiveWasteKg)}")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "ضایعات واقعی: ${formatKg(sales.actualWasteKg)}")
        y = drawLine(canvas, margin.toFloat(), y, pageWidth - margin, "صرفه‌جویی وزن: ${formatKg(sales.savedWasteKg)}")
        y += SECTION_GAP

        y = drawSectionTitle(canvas, margin.toFloat(), y, "برنامه برش")
        plan.bins.forEachIndexed { index, bin ->
            newPageIfNeeded(LINE_HEIGHT * 2)
            val pieces = bin.lengthsMm.joinToString(" + ") { "$it" }
            y = drawLine(
                canvas,
                margin.toFloat(),
                y,
                pageWidth - margin,
                "میلگرد ${index + 1} (${bin.stockLengthMm}mm): $pieces | استفاده ${bin.usedMm} | ضایعات ${bin.wasteMm}"
            )
        }

        document.finishPage(page)

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val output = File(reportsDir, "cutstock-${System.currentTimeMillis()}.pdf")
        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
        return output
    }

    private fun drawTitle(canvas: Canvas, x: Float, y: Float, title: String): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        return drawRtlBlock(canvas, x, y, (PAGE_WIDTH_PT - MARGIN_PT * 2).toFloat(), title, paint) + 8f
    }

    private fun drawSectionTitle(canvas: Canvas, x: Float, y: Float, title: String): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        return drawRtlBlock(canvas, x, y, (PAGE_WIDTH_PT - MARGIN_PT * 2).toFloat(), title, paint) + 6f
    }

    private fun drawLine(canvas: Canvas, x: Float, y: Float, maxX: Float, text: String): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        return drawRtlBlock(canvas, x, y, maxX - x, text, paint)
    }

    private fun drawRtlBlock(canvas: Canvas, x: Float, y: Float, width: Float, text: String, paint: TextPaint): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 4f
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f%%", value)

    private fun formatKg(value: Double): String = String.format(Locale.US, "%.2f kg", value)

    companion object {
        private const val PAGE_WIDTH_PT = 595
        private const val PAGE_HEIGHT_PT = 842
        private const val MARGIN_PT = 40
        private const val LINE_HEIGHT = 18f
        private const val SECTION_GAP = 12f
    }
}