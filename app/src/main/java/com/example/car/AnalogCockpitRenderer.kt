package com.example.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.obd.model.AlertLevel
import com.example.obd.model.CockpitDialTheme
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class CockpitRenderFrame(
    val dialSensors: List<TelemetrySensor>,
    val stripSensors: List<TelemetrySensor>,
    val readings: Map<String, SensorReading>,
    val dialNeedleOverrides: Map<String, Float> = emptyMap(),
    val connectionState: ObdConnectionState,
    val useFahrenheit: Boolean,
    val pressureUnit: PressureUnit,
    val isEnglish: Boolean = false,
    val dialTheme: CockpitDialTheme = CockpitDialTheme.SPORT_CARBON,
    val shiftLightActive: Boolean = false
)

private data class DialThemePalette(
    val background: Int,
    val faceInner: Int,
    val faceMid: Int,
    val faceOuter: Int,
    val outerRing: Int,
    val majorTick: Int,
    val minorTick: Int,
    val labelText: Int,
    val needleNormal: Int,
    val valueNormal: Int,
    val unitText: Int,
    val titleText: Int,
    val stripLabel: Int,
    val badgeText: Int,
    val warningAccent: Int,
    val shiftGlow: Int
)

object AnalogCockpitRenderer {

    private const val START_ANGLE_DEG = 135f
    private const val SWEEP_ANGLE_DEG = 270f

    private val sportCarbonPalette = DialThemePalette(
        background = Color.parseColor("#04060A"),
        faceInner = Color.parseColor("#111722"),
        faceMid = Color.parseColor("#080B10"),
        faceOuter = Color.parseColor("#04060A"),
        outerRing = Color.parseColor("#DCE3EC"),
        majorTick = Color.parseColor("#F1F5F9"),
        minorTick = Color.parseColor("#B0BAC9"),
        labelText = Color.parseColor("#E5EBF3"),
        needleNormal = Color.WHITE,
        valueNormal = Color.WHITE,
        unitText = Color.parseColor("#CBD5E1"),
        titleText = Color.parseColor("#E2E8F0"),
        stripLabel = Color.parseColor("#94A3B8"),
        badgeText = Color.parseColor("#8899AC"),
        warningAccent = Color.parseColor("#D96B27"),
        shiftGlow = Color.parseColor("#FF2E43")
    )

    private val trackRedPalette = DialThemePalette(
        background = Color.parseColor("#090406"),
        faceInner = Color.parseColor("#1E0C10"),
        faceMid = Color.parseColor("#0F0608"),
        faceOuter = Color.parseColor("#080305"),
        outerRing = Color.parseColor("#EF4444"),
        majorTick = Color.parseColor("#FEE2E2"),
        minorTick = Color.parseColor("#A16267"),
        labelText = Color.parseColor("#FECACA"),
        needleNormal = Color.parseColor("#FF3B30"),
        valueNormal = Color.WHITE,
        unitText = Color.parseColor("#FCA5A5"),
        titleText = Color.parseColor("#FECACA"),
        stripLabel = Color.parseColor("#B8898E"),
        badgeText = Color.parseColor("#FCA5A5"),
        warningAccent = Color.parseColor("#FF2D20"),
        shiftGlow = Color.parseColor("#FF3B30")
    )

    private val neonCyanPalette = DialThemePalette(
        background = Color.parseColor("#020810"),
        faceInner = Color.parseColor("#081C2E"),
        faceMid = Color.parseColor("#04101C"),
        faceOuter = Color.parseColor("#020810"),
        outerRing = Color.parseColor("#00E5FF"),
        majorTick = Color.parseColor("#7CEFFF"),
        minorTick = Color.parseColor("#387A96"),
        labelText = Color.parseColor("#BAE6FD"),
        needleNormal = Color.parseColor("#00E5FF"),
        valueNormal = Color.parseColor("#E0F7FA"),
        unitText = Color.parseColor("#7DD3FC"),
        titleText = Color.parseColor("#67E8F9"),
        stripLabel = Color.parseColor("#7CA8BF"),
        badgeText = Color.parseColor("#7DD3FC"),
        warningAccent = Color.parseColor("#F59E0B"),
        shiftGlow = Color.parseColor("#00E5FF")
    )

    private val daylightPalette = DialThemePalette(
        background = Color.parseColor("#E9EEF5"),
        faceInner = Color.parseColor("#FFFFFF"),
        faceMid = Color.parseColor("#F3F6FA"),
        faceOuter = Color.parseColor("#E2E8F0"),
        outerRing = Color.parseColor("#1E293B"),
        majorTick = Color.parseColor("#0F172A"),
        minorTick = Color.parseColor("#64748B"),
        labelText = Color.parseColor("#0F172A"),
        needleNormal = Color.parseColor("#D92B2B"),
        valueNormal = Color.parseColor("#0F172A"),
        unitText = Color.parseColor("#475569"),
        titleText = Color.parseColor("#1E293B"),
        stripLabel = Color.parseColor("#475569"),
        badgeText = Color.parseColor("#334155"),
        warningAccent = Color.parseColor("#DC2626"),
        shiftGlow = Color.parseColor("#DC2626")
    )

    private fun paletteFor(theme: CockpitDialTheme): DialThemePalette = when (theme) {
        CockpitDialTheme.SPORT_CARBON -> sportCarbonPalette
        CockpitDialTheme.TRACK_RED -> trackRedPalette
        CockpitDialTheme.NEON_CYAN -> neonCyanPalette
        CockpitDialTheme.DAYLIGHT_HIGH_CONTRAST -> daylightPalette
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val dialFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val warningArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val majorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val minorTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val warningTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val scaleLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val unitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val titleMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val stripLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val stripValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val statusBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val statusDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shiftBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun render(
        canvas: Canvas,
        width: Float,
        height: Float,
        visibleArea: RectF? = null,
        frame: CockpitRenderFrame
    ) {
        val palette = paletteFor(frame.dialTheme)
        bgPaint.color = palette.background
        outerRingPaint.color = palette.outerRing
        warningArcPaint.color = palette.warningAccent
        majorTickPaint.color = palette.majorTick
        minorTickPaint.color = palette.minorTick
        warningTickPaint.color = palette.warningAccent
        unitTextPaint.color = palette.unitText
        titleMaskPaint.color = palette.background
        titleTextPaint.color = palette.titleText
        stripLabelPaint.color = palette.stripLabel
        statusBadgePaint.color = palette.badgeText

        canvas.drawRect(0f, 0f, width, height, bgPaint)

        val safeRect = visibleArea ?: RectF(0f, 0f, width, height)
        val areaW = safeRect.width().coerceAtLeast(100f)
        val areaH = safeRect.height().coerceAtLeast(100f)

        val padX = areaW * 0.045f
        val padTop = areaH * 0.055f
        val padBottom = areaH * 0.055f

        val contentLeft = safeRect.left + padX
        val contentRight = safeRect.right - padX
        val contentW = (contentRight - contentLeft).coerceAtLeast(80f)

        if (frame.shiftLightActive) {
            drawShiftLightStrip(
                canvas = canvas,
                left = safeRect.left + areaW * 0.22f,
                right = safeRect.right - areaW * 0.22f,
                top = safeRect.top + padTop * 0.28f,
                height = (areaH * 0.028f).coerceIn(4f, 12f),
                color = palette.shiftGlow
            )
        }

        val stripHeight = (areaH * 0.19f).coerceIn(28f, areaH * 0.25f)
        val dialAreaTop = safeRect.top + padTop
        val dialAreaBottom = (safeRect.bottom - padBottom - stripHeight).coerceAtLeast(dialAreaTop + 60f)
        val dialAreaH = dialAreaBottom - dialAreaTop

        drawTopLeftConnectionIndicator(
            canvas = canvas,
            left = safeRect.left + padX * 0.65f,
            top = safeRect.top + padTop * 0.65f,
            areaH = areaH,
            connectionState = frame.connectionState,
            isEnglish = frame.isEnglish
        )

        val dialCount = frame.dialSensors.size.coerceIn(1, 3)
        val slotWidth = contentW / dialCount.toFloat()
        val dialDiameter = min(slotWidth * 0.87f, dialAreaH * 0.92f).coerceAtLeast(36f)
        val dialRadius = dialDiameter / 2f
        val dialCenterY = dialAreaTop + dialAreaH * 0.50f

        for (index in 0 until dialCount) {
            val sensor = frame.dialSensors[index]
            val reading = frame.readings[sensor.id]
            val spec = AnalogDialScale.buildSpec(
                sensor = sensor,
                reading = reading,
                useFahrenheit = frame.useFahrenheit,
                pressureUnit = frame.pressureUnit,
                isEnglish = frame.isEnglish
            )
            val needleFraction = frame.dialNeedleOverrides[sensor.id] ?: spec.needleFraction
            val cx = contentLeft + slotWidth * (index + 0.5f)
            drawAnalogDial(
                canvas = canvas,
                cx = cx,
                cy = dialCenterY,
                radius = dialRadius,
                spec = spec.copy(needleFraction = needleFraction),
                palette = palette
            )
        }

        val stripSensors = frame.stripSensors.take(4)
        if (stripSensors.isNotEmpty()) {
            val stripCount = stripSensors.size
            val stripSlotW = contentW / stripCount.toFloat()
            val stripTop = dialAreaBottom + areaH * 0.015f
            val labelSize = (stripHeight * 0.30f).coerceIn(9f, dialRadius * 0.22f)
            val valueSize = (stripHeight * 0.44f).coerceIn(12f, dialRadius * 0.30f)

            stripLabelPaint.textSize = labelSize
            stripValuePaint.textSize = valueSize

            val labelY = stripTop + labelSize * 1.05f
            val valueY = labelY + valueSize * 1.18f

            for (i in 0 until stripCount) {
                val sensor = stripSensors[i]
                val reading = frame.readings[sensor.id]
                val sx = contentLeft + stripSlotW * (i + 0.5f)

                val label = ellipsizeForWidth(
                    sensor.localizedShortName(frame.isEnglish),
                    stripLabelPaint,
                    stripSlotW * 0.88f
                )
                canvas.drawText(label, sx, labelY, stripLabelPaint)

                val valueStr = AnalogDialScale.formatStripValue(
                    sensor = sensor,
                    reading = reading,
                    useFahrenheit = frame.useFahrenheit,
                    pressureUnit = frame.pressureUnit,
                    isEnglish = frame.isEnglish
                )
                stripValuePaint.color = when (reading?.alertLevel) {
                    AlertLevel.CRITICAL -> Color.parseColor("#FF4444")
                    AlertLevel.WARNING -> Color.parseColor("#D97706")
                    else -> palette.valueNormal
                }
                val fittedVal = ellipsizeForWidth(valueStr, stripValuePaint, stripSlotW * 0.90f)
                canvas.drawText(fittedVal, sx, valueY, stripValuePaint)
            }
        }
    }

    private fun drawShiftLightStrip(
        canvas: Canvas,
        left: Float,
        right: Float,
        top: Float,
        height: Float,
        color: Int
    ) {
        val segments = 8
        val totalW = (right - left).coerceAtLeast(40f)
        val gap = totalW * 0.02f
        val segW = (totalW - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            shiftBarPaint.color = when {
                i < 3 -> Color.parseColor("#22C55E")
                i < 6 -> Color.parseColor("#F59E0B")
                else -> color
            }
            val x0 = left + i * (segW + gap)
            canvas.drawRoundRect(
                RectF(x0, top, x0 + segW, top + height),
                height * 0.5f,
                height * 0.5f,
                shiftBarPaint
            )
        }
    }

    private fun drawAnalogDial(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        spec: AnalogDialSpec,
        palette: DialThemePalette
    ) {
        dialFacePaint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(palette.faceInner, palette.faceMid, palette.faceOuter),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 0.985f, dialFacePaint)

        outerRingPaint.strokeWidth = (radius * 0.022f).coerceAtLeast(1.5f)
        canvas.drawCircle(cx, cy, radius * 0.96f, outerRingPaint)

        val warnStart = spec.warningStartFraction
        if (warnStart != null && warnStart < 0.99f) {
            val arcRadius = radius * 0.895f
            val arcRect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
            warningArcPaint.strokeWidth = (radius * 0.048f).coerceAtLeast(2.5f)
            val warnAngle = START_ANGLE_DEG + SWEEP_ANGLE_DEG * warnStart
            val warnSweep = SWEEP_ANGLE_DEG * (1f - warnStart)
            canvas.drawArc(arcRect, warnAngle, warnSweep, false, warningArcPaint)
        }

        val tickOuter = radius * 0.92f
        val majorInner = radius * 0.80f
        val minorInner = radius * 0.855f
        val labelRadius = radius * 0.675f

        majorTickPaint.strokeWidth = (radius * 0.022f).coerceAtLeast(1.4f)
        minorTickPaint.strokeWidth = (radius * 0.012f).coerceAtLeast(0.9f)
        warningTickPaint.strokeWidth = majorTickPaint.strokeWidth
        scaleLabelPaint.textSize = (radius * 0.112f).coerceAtLeast(7.5f)
        val labelVerticalOffset = (scaleLabelPaint.descent() + scaleLabelPaint.ascent()) * 0.5f

        for (tick in spec.ticks) {
            val angleDeg = START_ANGLE_DEG + SWEEP_ANGLE_DEG * tick.fraction
            val rad = angleDeg.toDouble() * PI / 180.0
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            val inWarningZone = warnStart != null && tick.fraction >= warnStart
            val innerR = if (tick.isMajor) majorInner else minorInner
            val paint = when {
                inWarningZone -> warningTickPaint
                tick.isMajor -> majorTickPaint
                else -> minorTickPaint
            }

            canvas.drawLine(
                cx + innerR * cosA,
                cy + innerR * sinA,
                cx + tickOuter * cosA,
                cy + tickOuter * sinA,
                paint
            )

            if (tick.isMajor && tick.label != null) {
                val lx = cx + labelRadius * cosA
                val ly = cy + labelRadius * sinA - labelVerticalOffset
                scaleLabelPaint.color = if (inWarningZone) palette.warningAccent else palette.labelText
                canvas.drawText(tick.label, lx, ly, scaleLabelPaint)
            }
        }

        val needleAngleDeg = START_ANGLE_DEG + SWEEP_ANGLE_DEG * spec.needleFraction.coerceIn(0f, 1f)
        val needleRad = needleAngleDeg.toDouble() * PI / 180.0
        val nCos = cos(needleRad).toFloat()
        val nSin = sin(needleRad).toFloat()
        val perpCos = -nSin
        val perpSin = nCos

        val needleInnerR = radius * 0.44f
        val needleOuterR = radius * 0.88f
        val halfBaseW = (radius * 0.024f).coerceAtLeast(1.6f)
        val halfTipW = (radius * 0.008f).coerceAtLeast(0.7f)

        val needlePath = Path().apply {
            moveTo(cx + needleInnerR * nCos + halfBaseW * perpCos, cy + needleInnerR * nSin + halfBaseW * perpSin)
            lineTo(cx + needleOuterR * nCos + halfTipW * perpCos, cy + needleOuterR * nSin + halfTipW * perpSin)
            lineTo(cx + needleOuterR * nCos - halfTipW * perpCos, cy + needleOuterR * nSin - halfTipW * perpSin)
            lineTo(cx + needleInnerR * nCos - halfBaseW * perpCos, cy + needleInnerR * nSin - halfBaseW * perpSin)
            close()
        }
        needlePaint.color = when (spec.alertLevel) {
            AlertLevel.CRITICAL -> Color.parseColor("#FF4D4D")
            AlertLevel.WARNING -> Color.parseColor("#F59E0B")
            AlertLevel.NORMAL -> palette.needleNormal
        }
        canvas.drawPath(needlePath, needlePaint)

        val valLen = spec.centerValueText.length
        valueTextPaint.textSize = when {
            valLen >= 5 -> radius * 0.25f
            valLen == 4 -> radius * 0.30f
            else -> radius * 0.36f
        }.coerceAtLeast(11f)
        valueTextPaint.color = when (spec.alertLevel) {
            AlertLevel.CRITICAL -> Color.parseColor("#FF4444")
            AlertLevel.WARNING -> Color.parseColor("#D97706")
            AlertLevel.NORMAL -> palette.valueNormal
        }
        val valBaselineY = cy + valueTextPaint.textSize * 0.16f
        canvas.drawText(spec.centerValueText, cx, valBaselineY, valueTextPaint)

        if (spec.unitText.isNotBlank()) {
            unitTextPaint.textSize = (radius * 0.145f).coerceAtLeast(8f)
            canvas.drawText(
                spec.unitText,
                cx,
                valBaselineY + unitTextPaint.textSize * 1.38f,
                unitTextPaint
            )
        }

        titleTextPaint.textSize = (radius * 0.145f).coerceAtLeast(8f)
        val titleText = ellipsizeForWidth(spec.title, titleTextPaint, radius * 1.05f)
        val measuredW = titleTextPaint.measureText(titleText)
        val labelCenterY = cy + radius * 0.945f
        val padH = radius * 0.08f
        val maskTop = labelCenterY - titleTextPaint.textSize * 0.95f
        val maskBottom = labelCenterY + titleTextPaint.textSize * 0.45f
        canvas.drawRect(cx - measuredW / 2f - padH, maskTop, cx + measuredW / 2f + padH, maskBottom, titleMaskPaint)
        canvas.drawText(titleText, cx, labelCenterY, titleTextPaint)
    }

    private fun drawTopLeftConnectionIndicator(
        canvas: Canvas,
        left: Float,
        top: Float,
        areaH: Float,
        connectionState: ObdConnectionState,
        isEnglish: Boolean
    ) {
        val text: String
        val dotColor: Int
        when (connectionState) {
            is ObdConnectionState.Connected -> {
                dotColor = Color.parseColor("#22C55E")
                text = connectionState.deviceName
            }
            is ObdConnectionState.Simulation -> {
                dotColor = Color.parseColor("#F59E0B")
                text = if (isEnglish) "SIMULATION" else "СИМУЛЯЦИЯ"
            }
            is ObdConnectionState.Connecting -> {
                dotColor = Color.parseColor("#00D4FF")
                text = if (isEnglish) "CONNECTING..." else "ПОДКЛЮЧЕНИЕ..."
            }
            is ObdConnectionState.Error -> {
                dotColor = Color.parseColor("#EF4444")
                text = if (isEnglish) "OBD2 ERROR" else "ОШИБКА OBD2"
            }
            is ObdConnectionState.Disconnected -> {
                dotColor = Color.parseColor("#64748B")
                text = if (isEnglish) "OBD2 OFFLINE" else "OBD2 ОФЛАЙН"
            }
        }
        val textSize = (areaH * 0.042f).coerceIn(10f, 22f)
        val dotRadius = textSize * 0.28f
        statusDotPaint.color = dotColor
        statusBadgePaint.textSize = textSize
        val cy = top + textSize * 0.55f
        canvas.drawCircle(left + dotRadius, cy, dotRadius, statusDotPaint)
        canvas.drawText(text, left + dotRadius * 3.2f, cy + textSize * 0.34f, statusBadgePaint)
    }

    private fun ellipsizeForWidth(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        if (maxWidth <= 0f) return ""
        var cut = text.length
        while (cut > 1 && paint.measureText(text.substring(0, cut) + "…") > maxWidth) {
            cut--
        }
        return text.substring(0, cut) + "…"
    }
}
