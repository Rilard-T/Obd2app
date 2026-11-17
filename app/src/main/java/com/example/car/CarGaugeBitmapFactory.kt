package com.example.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorReading

object CarGaugeBitmapFactory {

    fun createGaugeIcon(
        pid: ObdPid,
        reading: SensorReading?,
        useFahrenheit: Boolean,
        pressureUnit: PressureUnit,
        sizePx: Int = 220
    ): CarIcon {
        val bitmap = createGaugeBitmap(pid, reading, useFahrenheit, pressureUnit, sizePx)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    fun createGaugeBitmap(
        pid: ObdPid,
        reading: SensorReading?,
        useFahrenheit: Boolean,
        pressureUnit: PressureUnit,
        sizePx: Int = 220
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111724")
            style = Paint.Style.FILL
        }
        val center = sizePx / 2f
        val bgRadius = sizePx * 0.47f
        canvas.drawCircle(center, center, bgRadius, bgPaint)

        val strokeWidth = sizePx * 0.085f
        val arcInset = sizePx * 0.13f
        val arcRect = RectF(arcInset, arcInset, sizePx - arcInset, sizePx - arcInset)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#253047")
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(arcRect, 135f, 270f, false, trackPaint)

        val alertColor = when (reading?.alertLevel) {
            AlertLevel.CRITICAL -> Color.parseColor("#EF4444")
            AlertLevel.WARNING -> Color.parseColor("#FFB020")
            else -> Color.parseColor("#00D4FF")
        }

        val fraction = if (reading != null) {
            ((reading.value - pid.minRange) / (pid.maxRange - pid.minRange)).coerceIn(0f, 1f)
        } else {
            0f
        }

        if (fraction > 0.01f) {
            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = alertColor
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawArc(arcRect, 135f, 270f * fraction, false, progressPaint)
        }

        val valueText = reading?.let {
            pid.formatValue(it.value, useFahrenheit, pressureUnit)
        } ?: "--"

        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = when {
                valueText.length >= 5 -> sizePx * 0.20f
                valueText.length == 4 -> sizePx * 0.24f
                else -> sizePx * 0.28f
            }
        }
        val textY = center + (valuePaint.textSize * 0.22f)
        canvas.drawText(valueText, center, textY, valuePaint)

        val unitText = pid.displayUnit(useFahrenheit, pressureUnit)
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = alertColor
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textSize = sizePx * 0.11f
        }
        canvas.drawText(unitText, center, sizePx * 0.80f, unitPaint)

        return bitmap
    }
}
