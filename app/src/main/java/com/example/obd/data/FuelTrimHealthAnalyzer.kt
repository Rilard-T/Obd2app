package com.example.obd.data

import com.example.obd.model.ObdPid
import com.example.obd.model.SensorReading
import kotlin.math.abs

enum class FuelTrimHealthStatus {
    NO_DATA,
    OPTIMAL,
    SLIGHT_LEAN,
    LEAN_VACUUM_LEAK,
    SLIGHT_RICH,
    RICH_OVERFUEL
}

data class FuelTrimHealthSummary(
    val stftPercent: Float? = null,
    val ltftPercent: Float? = null,
    val totalTrimPercent: Float? = null,
    val status: FuelTrimHealthStatus = FuelTrimHealthStatus.NO_DATA
) {
    fun localizedTitle(isEnglish: Boolean): String = when (status) {
        FuelTrimHealthStatus.NO_DATA -> if (isEnglish) "Waiting for STFT / LTFT data" else "Ожидание данных STFT / LTFT"
        FuelTrimHealthStatus.OPTIMAL -> if (isEnglish) "Mixture Optimal (±6%)" else "Смесь в норме (±6%)"
        FuelTrimHealthStatus.SLIGHT_LEAN -> if (isEnglish) "Slight Lean Trend (+6..+12%)" else "Лёгкое обеднение (+6..+12%)"
        FuelTrimHealthStatus.LEAN_VACUUM_LEAK -> if (isEnglish) "Lean Mixture / Vacuum Leak (> +12%)" else "Бедная смесь / подсос (> +12%)"
        FuelTrimHealthStatus.SLIGHT_RICH -> if (isEnglish) "Slight Rich Trend (-6..-12%)" else "Лёгкое обогащение (-6..-12%)"
        FuelTrimHealthStatus.RICH_OVERFUEL -> if (isEnglish) "Rich Mixture / Overfuel (< -12%)" else "Богатая смесь / перелив (< -12%)"
    }

    fun localizedHint(isEnglish: Boolean): String = when (status) {
        FuelTrimHealthStatus.NO_DATA -> if (isEnglish) {
            "Connect adapter or run Simulation to check Short/Long Term Fuel Trims (0106, 0107)."
        } else {
            "Подключите адаптер или Тест-режим для оценки коррекций топлива (0106, 0107)."
        }
        FuelTrimHealthStatus.OPTIMAL -> if (isEnglish) {
            "Air-fuel ratio is balanced. No vacuum leaks or injector drift detected."
        } else {
            "Топливно-воздушная смесь сбалансирована. Подсоса воздуха и перелива не выявлено."
        }
        FuelTrimHealthStatus.SLIGHT_LEAN -> if (isEnglish) {
            "Monitor LTFT at idle: early sign of intake gasket seepage, dirty MAF, or weak fuel pump."
        } else {
            "Проверьте на холостом ходу: возможен небольшой подсос, загрязнение ДМРВ или падение давления топлива."
        }
        FuelTrimHealthStatus.LEAN_VACUUM_LEAK -> if (isEnglish) {
            "ECU adds significant fuel (+${"%.1f".format(totalTrimPercent ?: 0f)}%). Check intake hoses, PCV valve, exhaust before O2 sensor, and fuel pressure."
        } else {
            "ЭБУ сильно доливает топливо (+${"%.1f".format(totalTrimPercent ?: 0f)}%). Проверьте впускные патрубки, клапан PCV, герметичность до 1-й лямбды и давление в рампе."
        }
        FuelTrimHealthStatus.SLIGHT_RICH -> if (isEnglish) {
            "ECU reduces fuel slightly. Inspect clogged air filter, EVAP purge valve, or aging O2 sensor."
        } else {
            "ЭБУ слегка убирает топливо. Проверьте воздушный фильтр, клапан адсорбера EVAP и датчик ОЖ/кислорода."
        }
        FuelTrimHealthStatus.RICH_OVERFUEL -> if (isEnglish) {
            "ECU cuts fuel heavily (${"%.1f".format(totalTrimPercent ?: 0f)}%). Check leaking injectors, stuck EVAP valve, boosted pipe leak, or faulty MAF/MAP."
        } else {
            "ЭБУ сильно урезает подачу (${"%.1f".format(totalTrimPercent ?: 0f)}%). Возможна течь форсунок, заклинивший EVAP, негерметичный интеркулер или сбой ДМРВ/MAP."
        }
    }
}

object FuelTrimHealthAnalyzer {
    fun analyze(readings: Map<String, SensorReading>): FuelTrimHealthSummary {
        val stft = readings[ObdPid.SHORT_FUEL_TRIM.modeAndPid]?.value
        val ltft = readings[ObdPid.LONG_FUEL_TRIM.modeAndPid]?.value
        if (stft == null && ltft == null) {
            return FuelTrimHealthSummary()
        }
        val total = (stft ?: 0f) + (ltft ?: 0f)
        val status = when {
            total > 12f -> FuelTrimHealthStatus.LEAN_VACUUM_LEAK
            total > 6f -> FuelTrimHealthStatus.SLIGHT_LEAN
            total < -12f -> FuelTrimHealthStatus.RICH_OVERFUEL
            total < -6f -> FuelTrimHealthStatus.SLIGHT_RICH
            abs(total) <= 6f -> FuelTrimHealthStatus.OPTIMAL
            else -> FuelTrimHealthStatus.OPTIMAL
        }
        return FuelTrimHealthSummary(
            stftPercent = stft,
            ltftPercent = ltft,
            totalTrimPercent = total,
            status = status
        )
    }
}
