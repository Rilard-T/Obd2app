package com.example.obd.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.obd.model.PrePurchaseInspectionResult
import com.example.obd.model.ResetSuspicionLevel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PrePurchasePdfGenerator {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = 515f

    fun generateReportPdf(
        context: Context,
        result: PrePurchaseInspectionResult,
        isEnglish: Boolean
    ): File {
        val dir = File(context.cacheDir, "logs")
        if (!dir.exists()) dir.mkdirs()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(result.timestampMs))
        val outFile = File(dir, "obd_inspection_$stamp.pdf")

        val doc = PdfDocument()
        var pageNum = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var y = 48f

        val headerBgPaint = Paint().apply { color = Color.rgb(12, 20, 32) }
        val accentPaint = Paint().apply {
            color = Color.rgb(0, 185, 225)
            strokeWidth = 2f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 212, 255)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(18, 30, 48)
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(35, 42, 54)
            textSize = 10.5f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 26, 36)
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val verdictPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (result.resetSuspicion) {
                ResetSuspicionLevel.HIGH -> Color.rgb(198, 40, 40)
                ResetSuspicionLevel.CAUTION -> Color.rgb(217, 119, 6)
                ResetSuspicionLevel.NONE -> Color.rgb(5, 150, 105)
            }
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                doc.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                y = MARGIN + 10f
            }
        }

        fun drawWrappedLines(text: String, paint: Paint, lineHeight: Float = 15f) {
            for (paragraph in text.split("\n")) {
                if (paragraph.isBlank()) {
                    y += lineHeight * 0.6f
                    continue
                }
                val words = paragraph.trim().split(Regex("\\s+"))
                val line = StringBuilder()
                for (word in words) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (paint.measureText(candidate) <= CONTENT_WIDTH) {
                        line.clear()
                        line.append(candidate)
                    } else {
                        ensureSpace(lineHeight)
                        canvas.drawText(line.toString(), MARGIN, y, paint)
                        y += lineHeight
                        line.clear()
                        line.append(word)
                    }
                }
                if (line.isNotEmpty()) {
                    ensureSpace(lineHeight)
                    canvas.drawText(line.toString(), MARGIN, y, paint)
                    y += lineHeight
                }
            }
        }

        // Top dark header banner
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 96f, headerBgPaint)
        canvas.drawText(
            if (isEnglish) "OBD2 AUTO — PRE-PURCHASE VEHICLE REPORT" else "OBD2 АВТО — ОТЧЁТ ПРОВЕРКИ ПЕРЕД ПОКУПКОЙ",
            MARGIN,
            40f,
            titlePaint
        )
        val dateStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(result.timestampMs))
        canvas.drawText(
            if (isEnglish) "Generated: $dateStr • Vehicle: ${result.vehicleName}"
            else "Дата: $dateStr • Авто: ${result.vehicleName}",
            MARGIN,
            66f,
            subHeaderPaint
        )
        y = 120f

        // Identity section
        canvas.drawText(
            if (isEnglish) "1. Vehicle Identity & ECU Status" else "1. Идентификация авто и статус ЭБУ",
            MARGIN,
            y,
            sectionPaint
        )
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, accentPaint)
        y += 18f

        val rows = listOf(
            (if (isEnglish) "VIN:" else "VIN-код:") to (result.vin ?: "—"),
            (if (isEnglish) "Calibration ID:" else "ID калибровки ЭБУ:") to (result.calId ?: "—"),
            (if (isEnglish) "Check Engine (MIL):" else "Индикатор Check Engine (MIL):") to
                if (result.milOn) (if (isEnglish) "ON (Warning)" else "ГОРИТ (Ошибка)") else "OFF (OK)",
            (if (isEnglish) "Active / Pending DTCs:" else "Активные / Ожидающие ошибки:") to
                "${result.activeDtcCount} / ${result.pendingDtcCount}",
            (if (isEnglish) "Mode 06 Failed Tests:" else "Провалено тестов Mode 06:") to
                result.mode06FailedCount.toString(),
            (if (isEnglish) "Battery / Coolant:" else "Напряжение АКБ / ОЖ:") to
                "${result.batteryVoltage?.let { String.format(Locale.US, "%.1f V", it) } ?: "—"} / ${result.coolantTempC?.let { "${it.toInt()} °C" } ?: "—"}"
        )

        for ((label, value) in rows) {
            ensureSpace(16f)
            canvas.drawText(label, MARGIN, y, bodyPaint)
            canvas.drawText(value, MARGIN + 220f, y, boldBodyPaint)
            y += 16f
        }

        y += 12f
        ensureSpace(90f)
        canvas.drawText(
            if (isEnglish) "2. Anti-Fraud & Code Reset Tamper Check" else "2. Проверка на недавний сброс ошибок",
            MARGIN,
            y,
            sectionPaint
        )
        y += 8f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, accentPaint)
        y += 18f

        val suspicionLabel = when (result.resetSuspicion) {
            ResetSuspicionLevel.HIGH -> if (isEnglish) "HIGH RISK: DTCs likely cleared very recently!" else "ВЫСОКИЙ РИСК: ошибки сбрасывались недавно!"
            ResetSuspicionLevel.CAUTION -> if (isEnglish) "CAUTION: Monitors incomplete or short trip since clear" else "ВНИМАНИЕ: не все мониторы завершены или малый пробег"
            ResetSuspicionLevel.NONE -> if (isEnglish) "CLEAN: Readiness monitors completed normally" else "ЧИСТО: мониторы пройдены штатно, следов сброса нет"
        }
        canvas.drawText(suspicionLabel, MARGIN, y, verdictPaint)
        y += 20f

        val tamperRows = listOf(
            (if (isEnglish) "Distance since DTC clear (0131):" else "Пробег после сброса ошибок (0131):") to
                (result.distanceSinceCodesClearedKm?.let { "$it km" } ?: "N/A"),
            (if (isEnglish) "Warmups since DTC clear (0130):" else "Прогревов после сброса (0130):") to
                (result.warmupsSinceCodesCleared?.toString() ?: "N/A"),
            (if (isEnglish) "Engine run time since clear (014E):" else "Время работы после сброса (014E):") to
                (result.timeSinceCodesClearedMin?.let { "$it min" } ?: "N/A"),
            (if (isEnglish) "Distance with MIL ON (0121):" else "Пробег с горящим Check (0121):") to
                (result.distanceWithMilOnKm?.let { "$it km" } ?: "0 km"),
            (if (isEnglish) "I/M Readiness Monitors:" else "Готовность мониторов I/M:") to
                "${result.imReadyCount}/${result.imSupportedCount}"
        )

        for ((label, value) in tamperRows) {
            ensureSpace(16f)
            canvas.drawText(label, MARGIN, y, bodyPaint)
            canvas.drawText(value, MARGIN + 240f, y, boldBodyPaint)
            y += 16f
        }

        if (!result.aiVerdict.isNullOrBlank()) {
            y += 14f
            ensureSpace(40f)
            canvas.drawText(
                if (isEnglish) "3. AI Expert Summary" else "3. Заключение AI-эксперта",
                MARGIN,
                y,
                sectionPaint
            )
            y += 8f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, accentPaint)
            y += 18f
            drawWrappedLines(result.aiVerdict, bodyPaint, lineHeight = 15f)
        }

        doc.finishPage(page)
        FileOutputStream(outFile).use { fos ->
            doc.writeTo(fos)
        }
        doc.close()
        return outFile
    }
}
