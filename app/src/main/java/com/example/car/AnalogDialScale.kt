package com.example.car

import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

data class DialTick(
    val fraction: Float,
    val isMajor: Boolean,
    val label: String?
)

data class AnalogDialSpec(
    val title: String,
    val centerValueText: String,
    val unitText: String,
    val needleFraction: Float,
    val warningStartFraction: Float?,
    val alertLevel: AlertLevel,
    val ticks: List<DialTick>
)

object AnalogDialScale {

    private val ruLocale: Locale = Locale.forLanguageTag("ru-RU")

    fun buildSpec(
        sensor: TelemetrySensor,
        reading: SensorReading?,
        useFahrenheit: Boolean,
        pressureUnit: PressureUnit,
        isEnglish: Boolean = false
    ): AnalogDialSpec {
        val rawMin = sensor.builtInPid?.convertValue(sensor.minRange, useFahrenheit, pressureUnit)
            ?: sensor.minRange
        val rawMax = sensor.builtInPid?.convertValue(sensor.maxRange, useFahrenheit, pressureUnit)
            ?: sensor.maxRange

        val isRpm = sensor.builtInPid == ObdPid.ENGINE_RPM
        val divisor = if (isRpm) 1000f else 1f

        val scaleMin = if (isRpm) 0f else rawMin
        val scaleMax = if (isRpm) (rawMax / divisor).coerceAtLeast(6f) else {
            if (rawMax > scaleMinFor(rawMin)) rawMax else rawMin + 10f
        }

        // Round COOLANT_TEMP slightly to clean automotive bounds (-20..120 C instead of -20..130 odd tick spacing)
        val (alignedMin, alignedMax) = if (sensor.builtInPid == ObdPid.COOLANT_TEMP && !useFahrenheit) {
            -20f to 120f
        } else {
            scaleMin to scaleMax
        }

        val span = (alignedMax - alignedMin).coerceAtLeast(0.1f)
        val step = chooseNiceStep(span, targetIntervals = 8)
        val niceMin = floor((alignedMin / step).toDouble()).toFloat() * step
        val niceMax = ceil((alignedMax / step).toDouble()).toFloat() * step
        val totalSpan = (niceMax - niceMin).coerceAtLeast(step)

        val majorCount = ((totalSpan / step).roundToInt()).coerceIn(2, 14)
        val minorPerMajor = when {
            isRpm -> 5
            majorCount <= 6 -> 5
            majorCount <= 9 -> 4
            else -> 2
        }
        val totalMinorTicks = majorCount * minorPerMajor

        val ticks = ArrayList<DialTick>(totalMinorTicks + 1)
        for (i in 0..totalMinorTicks) {
            val frac = i.toFloat() / totalMinorTicks.toFloat()
            val isMajor = i % minorPerMajor == 0
            val tickVal = niceMin + totalSpan * frac
            val label = if (isMajor) formatTickLabel(tickVal, step, isEnglish) else null
            ticks.add(DialTick(fraction = frac, isMajor = isMajor, label = label))
        }

        val warningStartFraction = computeWarningFraction(
            sensor = sensor,
            niceMin = niceMin,
            totalSpan = totalSpan,
            divisor = divisor,
            useFahrenheit = useFahrenheit,
            pressureUnit = pressureUnit
        )

        val displayValue = reading?.let { r ->
            val converted = sensor.builtInPid?.convertValue(r.value, useFahrenheit, pressureUnit) ?: r.value
            converted / divisor
        }

        val needleFraction = if (displayValue != null) {
            ((displayValue - niceMin) / totalSpan).coerceIn(0f, 1f)
        } else {
            0f
        }

        val activeLocale = if (isEnglish) Locale.US else ruLocale
        val centerValueText = when {
            reading == null || displayValue == null -> "--"
            isRpm -> String.format(activeLocale, "%.1f", displayValue)
            else -> sensor.formatValue(reading.value, useFahrenheit, pressureUnit, isEnglish)
        }

        val unitText = if (isRpm) {
            "x1000"
        } else {
            sensor.displayUnit(useFahrenheit, pressureUnit, isEnglish)
        }

        return AnalogDialSpec(
            title = sensor.localizedShortName(isEnglish),
            centerValueText = centerValueText,
            unitText = unitText,
            needleFraction = needleFraction,
            warningStartFraction = warningStartFraction,
            alertLevel = reading?.alertLevel ?: AlertLevel.NORMAL,
            ticks = ticks
        )
    }

    fun formatStripValue(
        sensor: TelemetrySensor,
        reading: SensorReading?,
        useFahrenheit: Boolean,
        pressureUnit: PressureUnit,
        isEnglish: Boolean = false
    ): String {
        val unit = sensor.displayUnit(useFahrenheit, pressureUnit, isEnglish)
        if (reading == null) {
            return if (unit.isNotBlank()) "-- $unit" else "--"
        }
        val num = sensor.formatValue(reading.value, useFahrenheit, pressureUnit, isEnglish)
        return if (unit.isNotBlank()) "$num $unit" else num
    }

    private fun scaleMinFor(rawMin: Float): Float = rawMin

    private fun computeWarningFraction(
        sensor: TelemetrySensor,
        niceMin: Float,
        totalSpan: Float,
        divisor: Float,
        useFahrenheit: Boolean,
        pressureUnit: PressureUnit
    ): Float? {
        val pid = sensor.builtInPid ?: return null
        val warnRaw = pid.warningThreshold ?: pid.criticalThreshold ?: return null
        val warnConverted = pid.convertValue(warnRaw, useFahrenheit, pressureUnit) / divisor
        val frac = (warnConverted - niceMin) / totalSpan
        return if (frac in 0.45f..0.98f) frac else null
    }

    internal fun chooseNiceStep(span: Float, targetIntervals: Int = 8): Float {
        if (span <= 0f) return 1f
        val rough = span / targetIntervals.toFloat()
        val exp = floor(log10(rough.toDouble())).toFloat()
        val mag = 10.0.pow(exp.toDouble()).toFloat()
        val normalized = rough / mag

        val niceBase = when {
            normalized <= 1.2f -> 1f
            normalized <= 2.2f -> 2f
            normalized <= 3.2f -> 3f
            normalized <= 5.5f -> 5f
            else -> 10f
        }
        return (niceBase * mag).coerceAtLeast(0.1f)
    }

    private fun formatTickLabel(value: Float, step: Float, isEnglish: Boolean): String {
        val clean = if (kotlin.math.abs(value) < 0.001f) 0f else value
        return if (step >= 1f && kotlin.math.abs(clean - clean.roundToInt()) < 0.05f) {
            clean.roundToInt().toString()
        } else {
            val locale = if (isEnglish) Locale.US else ruLocale
            val sep = if (isEnglish) '.' else ','
            String.format(locale, "%.1f", clean).trimEnd('0').trimEnd(sep)
        }
    }
}
