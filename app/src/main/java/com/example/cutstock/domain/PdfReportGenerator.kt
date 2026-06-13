package com.example.cutstock.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import com.example.cutstock.data.DemandInput
import com.example.cutstock.data.ProjectSettings
import com.example.cutstock.nativecore.Bin
import com.example.cutstock.nativecore.CuttingPlan
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.max

class PdfReportGenerator(private val context: Context) {
    private val decimalFormat = DecimalFormat("#,###")

    fun generate(
        projectName: String,
        settings: ProjectSettings,
        demands: List<DemandInput>,
        plan: CuttingPlan,
        sales: SalesSummary,
    ): File {
        val document = PdfDocument()
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN_PT.toFloat()

        fun finishPage(isLastPage: Boolean = false) {
            if (isLastPage) {
                drawQrCode(canvas)
            }
            drawFooter(canvas)
            document.finishPage(page)
        }

        fun newPage() {
            finishPage()
            pageNumber += 1
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN_PT.toFloat()
        }

        fun ensureSpace(requiredHeight: Float) {
            val contentBottom = PAGE_HEIGHT_PT - MARGIN_PT - RESERVED_BOTTOM_PT
            if (y + requiredHeight > contentBottom) {
                newPage()
            }
        }

        y = drawTitle(canvas, MARGIN_PT.toFloat(), y, projectName)
        y += 6f

        ensureSpace(96f)
        y = drawMarketingBox(canvas, y, sales)
        y += 10f

        ensureSpace(130f)
        y = drawProjectSummary(canvas, y, settings, sales)
        y += 10f

        ensureSpace(150f)
        y = drawComparisonTable(canvas, y, demands, plan, sales, settings)
        y += 10f

        ensureSpace(40f)
        y = drawSectionTitle(canvas, MARGIN_PT.toFloat(), y, "الگوهای برش")
        y += 6f

        val groupedPatterns = groupBins(plan.bins)
        groupedPatterns.forEach { grouped ->
            ensureSpace(110f)
            y = drawGroupedPattern(canvas, y, grouped)
            y += 8f
        }

        ensureSpace(120f)
        y = drawSectionTitle(canvas, MARGIN_PT.toFloat(), y, "جزئیات فنی")
        y += 6f

        ensureSpace(60f)
        y = drawLine(canvas, MARGIN_PT.toFloat(), y, contentWidth(), "میلگردهای استفاده‌شده: ${sales.barsNeeded}")
        y = drawLine(canvas, MARGIN_PT.toFloat(), y, contentWidth(), "درصد ضایعات: ${formatPercent(sales.wastePercent)}")
        y = drawLine(canvas, MARGIN_PT.toFloat(), y, contentWidth(), "ضایعات ساده: ${formatKg(sales.naiveWasteKg)}")
        y = drawLine(canvas, MARGIN_PT.toFloat(), y, contentWidth(), "ضایعات واقعی: ${formatKg(sales.actualWasteKg)}")
        y = drawLine(canvas, MARGIN_PT.toFloat(), y, contentWidth(), "صرفه‌جویی وزن: ${formatKg(sales.savedWasteKg)}")

        finishPage(isLastPage = true)

        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val output = File(reportsDir, "cutstock-${System.currentTimeMillis()}.pdf")
        FileOutputStream(output).use { document.writeTo(it) }
        document.close()
        return output
    }

    fun groupBins(bins: List<Bin>): List<GroupedPattern> {
        val grouped = linkedMapOf<String, MutableList<Bin>>()
        bins.forEach { bin ->
            val normalizedPieces = bin.lengthsMm.sortedArray()
            val key = "${bin.stockLengthMm}|${normalizedPieces.joinToString(",")}"
            grouped.getOrPut(key) { mutableListOf() }.add(bin)
        }

        return grouped.values.map { group ->
            val first = group.first()
            GroupedPattern(
                pieces = first.lengthsMm.sortedArray(),
                stockLengthMm = first.stockLengthMm,
                count = group.size,
                usedMm = first.usedMm,
                wasteMm = first.wasteMm,
            )
        }
    }

    private fun drawTitle(canvas: Canvas, x: Float, y: Float, title: String): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        return drawRtlBlock(canvas, x, y, contentWidth(), title, paint) + 2f
    }

    private fun drawSectionTitle(canvas: Canvas, x: Float, y: Float, title: String): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        return drawRtlBlock(canvas, x, y, contentWidth(), title, paint) + 2f
    }

    private fun drawLine(canvas: Canvas, x: Float, y: Float, width: Float, text: String): Float {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        return drawRtlBlock(canvas, x, y, width, text, paint) + 1f
    }

    private fun drawMarketingBox(canvas: Canvas, y: Float, sales: SalesSummary): Float {
        val boxX = MARGIN_PT.toFloat()
        val boxWidth = contentWidth()
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.parseColor("#1B5E20")
        }
        val text = "این پروژه ${formatKg(sales.savedWasteKg)} کیلوگرم فولاد صرفه‌جویی کرد (معادل ${decimalFormat.format(sales.moneySavedTomans)} تومان)"
        val textHeight = measureRtlTextHeight(text, boxWidth - 24f, textPaint)
        val boxHeight = textHeight + 24f

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E8F5E9")
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#388E3C")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        val rect = RectF(boxX, y, boxX + boxWidth, y + boxHeight)
        canvas.drawRoundRect(rect, 12f, 12f, fillPaint)
        canvas.drawRoundRect(rect, 12f, 12f, strokePaint)
        drawRtlBlock(canvas, boxX + 12f, y + 12f, boxWidth - 24f, text, textPaint)
        return y + boxHeight
    }

    private fun drawProjectSummary(
        canvas: Canvas,
        y: Float,
        settings: ProjectSettings,
        sales: SalesSummary,
    ): Float {
        var currentY = drawSectionTitle(canvas, MARGIN_PT.toFloat(), y, "خلاصه پروژه")
        currentY = drawLine(canvas, MARGIN_PT.toFloat(), currentY, contentWidth(), "طول‌های stock: ${settings.stockLengthsMm.joinToString(", ")} mm")
        currentY = drawLine(canvas, MARGIN_PT.toFloat(), currentY, contentWidth(), "kerf: ${settings.kerfMm} mm")
        currentY = drawLine(canvas, MARGIN_PT.toFloat(), currentY, contentWidth(), "قطر: ${settings.diameterMm} mm")
        currentY = drawLine(canvas, MARGIN_PT.toFloat(), currentY, contentWidth(), "تعداد میلگرد: ${sales.barsNeeded}")
        currentY = drawLine(canvas, MARGIN_PT.toFloat(), currentY, contentWidth(), "میانگین بهره‌وری: ${formatPercent(sales.averageUtilizationPercent)}")
        return currentY
    }

    private fun drawComparisonTable(
        canvas: Canvas,
        y: Float,
        demands: List<DemandInput>,
        plan: CuttingPlan,
        sales: SalesSummary,
        settings: ProjectSettings,
    ): Float {
        val tableX = MARGIN_PT.toFloat()
        val tableWidth = contentWidth()
        val col1 = tableWidth * 0.38f
        val col2 = tableWidth * 0.31f
        val col3 = tableWidth - col1 - col2
        val rowHeight = 32f
        val headerHeight = 30f
        val pricePerKgTomans = if (sales.savedWasteKg > 0.0) sales.moneySavedTomans.toDouble() / sales.savedWasteKg else 0.0
        val rows = listOf(
            listOf("ضایعات (کیلوگرم)", formatKg(sales.naiveWasteKg), formatKg(sales.actualWasteKg)),
            listOf(
                "هزینه ضایعات (تومان)",
                decimalFormat.format((sales.naiveWasteKg * pricePerKgTomans).toLong()),
                decimalFormat.format((sales.actualWasteKg * pricePerKgTomans).toLong()),
            ),
            listOf("تعداد میلگرد", NaiveCuttingEstimator.estimateBarsNeeded(demands, settings.stockLengthsMm.maxOrNull() ?: 0, settings.kerfMm).toString(), plan.binCount.toString()),
        )

        val totalHeight = headerHeight + rowHeight * rows.size
        val tableTitleBottom = drawRtlBlock(canvas, tableX, y, tableWidth, "مقایسه قبل/بعد", TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) })

        val startY = tableTitleBottom + 4f
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11.5f }
        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        var cy = startY
        drawCell(canvas, tableX, cy, col1, headerHeight, "شاخص", headerPaint, fill = true)
        drawCell(canvas, tableX + col1, cy, col2, headerHeight, "روش معمول کارگاه", headerPaint, fill = true)
        drawCell(canvas, tableX + col1 + col2, cy, col3, headerHeight, "کات‌میز", headerPaint, fill = true)
        cy += headerHeight

        rows.forEach { row ->
            drawCell(canvas, tableX, cy, col1, rowHeight, row[0], textPaint)
            drawCell(canvas, tableX + col1, cy, col2, rowHeight, row[1], textPaint)
            drawCell(canvas, tableX + col1 + col2, cy, col3, rowHeight, row[2], textPaint)
            cy += rowHeight
        }

        // Draw borders explicitly using drawLine as requested.
        val bottomY = startY + totalHeight
        val xs = listOf(tableX, tableX + col1, tableX + col1 + col2, tableX + tableWidth)
        xs.forEach { xPos ->
            canvas.drawLine(xPos, startY, xPos, bottomY, borderPaint)
        }
        val ys = listOf(startY, startY + headerHeight, startY + headerHeight + rowHeight, startY + headerHeight + rowHeight * 2, bottomY)
        ys.forEach { yPos ->
            canvas.drawLine(tableX, yPos, tableX + tableWidth, yPos, borderPaint)
        }

        return bottomY
    }

    private fun drawGroupedPattern(canvas: Canvas, y: Float, pattern: GroupedPattern): Float {
        val tableX = MARGIN_PT.toFloat()
        val width = contentWidth()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val header = "${pattern.pieces.joinToString(" + ")}  |  شاخه ${pattern.stockLengthMm}mm  |  استفاده ${pattern.usedMm}  |  ضایعات ${pattern.wasteMm}"
        var currentY = drawRtlBlock(canvas, tableX, y, width, header, titlePaint) + 6f

        val barHeight = 18f
        val barWidth = width
        val barRect = RectF(tableX, currentY, tableX + barWidth, currentY + barHeight)
        val segments = pattern.pieces.map { it.toFloat() } + pattern.wasteMm.toFloat()
        val colors = SEGMENT_COLORS + WASTE_COLOR
        var startX = barRect.left
        val total = segments.sumOf { it.toDouble() }.toFloat().coerceAtLeast(1f)
        segments.forEachIndexed { index, segmentMm ->
            val isLast = index == segments.lastIndex
            val segmentWidth = if (isLast) {
                max(0f, barRect.right - startX)
            } else {
                barWidth * (segmentMm / total)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colors[index % colors.size]
                style = Paint.Style.FILL
            }
            canvas.drawRect(startX, barRect.top, startX + segmentWidth, barRect.bottom, paint)
            startX += segmentWidth
        }
        canvas.drawRect(barRect, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        })

        currentY += barHeight + 6f
        currentY = drawRtlBlock(canvas, tableX, currentY, width, "×${pattern.count} میلگرد", TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        return currentY
    }

    private fun drawQrCode(canvas: Canvas) {
        val qrSizePx = 320
        val matrix = generateQrMatrix("https://takl.ink/CutMize_team/", qrSizePx, qrSizePx) ?: return
        val bitmap = bitmapFromMatrix(matrix) ?: return

        val qrSizePt = 80f
        val x = PAGE_WIDTH_PT - MARGIN_PT - qrSizePt
        val y = PAGE_HEIGHT_PT - 140f
        val bitmapRect = RectF(x, y, x + qrSizePt, y + qrSizePt)
        canvas.drawBitmap(bitmap, null, bitmapRect, null)

        val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        drawRtlBlock(canvas, x - 8f, y + qrSizePt + 4f, qrSizePt + 16f, "دانلود کات‌میز", labelPaint)
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.DKGRAY
        }
        drawRtlBlock(
            canvas,
            MARGIN_PT.toFloat(),
            PAGE_HEIGHT_PT - 22f,
            contentWidth(),
            "کات‌میز — بهینه‌سازی هوشمند برش میلگرد",
            paint,
        )
    }

    private fun drawRtlBlock(canvas: Canvas, x: Float, y: Float, width: Float, text: String, paint: TextPaint): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, max(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height + 4f
    }

    private fun measureRtlTextHeight(text: String, width: Float, paint: TextPaint): Float {
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, max(1, width.toInt()))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        return layout.height.toFloat()
    }

    private fun drawCell(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        text: String,
        paint: TextPaint,
        fill: Boolean = false,
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (fill) Color.parseColor("#F5F5F5") else Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRect(rect, fillPaint)
        canvas.drawRect(rect, borderPaint)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, max(1, (width - 12f).toInt()))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setIncludePad(false)
            .build()
        val tx = left + 6f
        val ty = top + max(0f, (height - layout.height) / 2f)
        canvas.save()
        canvas.translate(tx, ty)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun generateQrMatrix(text: String, width: Int, height: Int): BitMatrix? {
        return try {
            MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
        } catch (_: Exception) {
            null
        }
    }

    private fun bitmapFromMatrix(matrix: BitMatrix): Bitmap? {
        return try {
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatPercent(value: Double): String = String.format(Locale.US, "%.2f%%", value)

    private fun formatKg(value: Double): String = String.format(Locale.US, "%.2f kg", value)

    private fun contentWidth(): Float = (PAGE_WIDTH_PT - MARGIN_PT * 2).toFloat()

    companion object {
        private const val PAGE_WIDTH_PT = 595
        private const val PAGE_HEIGHT_PT = 842
        private const val MARGIN_PT = 40
        private const val RESERVED_BOTTOM_PT = 110f
        private val SEGMENT_COLORS = intArrayOf(
            Color.parseColor("#1976D2"),
            Color.parseColor("#388E3C"),
            Color.parseColor("#F57C00"),
            Color.parseColor("#7B1FA2"),
            Color.parseColor("#C2185B"),
            Color.parseColor("#00796B"),
        )
        private val WASTE_COLOR = Color.parseColor("#BDBDBD")
    }
}

data class GroupedPattern(
    val pieces: IntArray,
    val stockLengthMm: Int,
    val count: Int,
    val usedMm: Int,
    val wasteMm: Int,
)
