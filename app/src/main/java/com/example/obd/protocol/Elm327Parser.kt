package com.example.obd.protocol

import com.example.obd.model.DtcCode
import com.example.obd.model.DtcSeverity
import com.example.obd.model.ObdPid

object Elm327Parser {

    fun cleanResponse(raw: String): String {
        return raw
            .replace("\r", "\n")
            .replace(">", "")
            .lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !line.equals("SEARCHING...", ignoreCase = true) &&
                    !line.startsWith("BUS INIT", ignoreCase = true) &&
                    !line.startsWith("AT", ignoreCase = true)
            }
            .joinToString(" ")
            .trim()
    }

    fun isErrorResponse(raw: String): Boolean {
        val upper = raw.uppercase()
        return upper.contains("NO DATA") ||
            upper.contains("UNABLE TO CONNECT") ||
            upper.contains("ERROR") ||
            upper.contains("?") ||
            upper.contains("STOPPED") ||
            upper.contains("CAN ERROR")
    }

    /**
     * Parses a Mode 01 response for a given [pid].
     * Supports raw responses with or without spaces and multi-ECU lines (takes first valid 41 <PID> match).
     */
    fun parseMode01Pid(pid: ObdPid, rawResponse: String): Float? {
        if (isErrorResponse(rawResponse)) return null
        val hexTokens = extractHexBytesAfterHeader(rawResponse, expectedHeader = "41${pid.pidHex}")
            ?: return null

        val a = hexTokens.getOrNull(0) ?: return null
        val b = hexTokens.getOrNull(1)

        return when (pid) {
            ObdPid.ENGINE_RPM -> {
                if (b == null) null else ((a * 256) + b) / 4.0f
            }
            ObdPid.VEHICLE_SPEED -> a.toFloat()
            ObdPid.COOLANT_TEMP -> (a - 40).toFloat()
            ObdPid.CONTROL_MODULE_VOLTAGE -> {
                if (b == null) null else ((a * 256) + b) / 1000.0f
            }
            ObdPid.ENGINE_LOAD -> (a * 100.0f) / 255.0f
            ObdPid.THROTTLE_POS -> (a * 100.0f) / 255.0f
            ObdPid.INTAKE_AIR_TEMP -> (a - 40).toFloat()
            ObdPid.INTAKE_MAP -> a.toFloat()
            ObdPid.MAF_FLOW -> {
                if (b == null) null else ((a * 256) + b) / 100.0f
            }
            ObdPid.FUEL_LEVEL -> (a * 100.0f) / 255.0f
            ObdPid.OIL_TEMP -> (a - 40).toFloat()
            ObdPid.TIMING_ADVANCE -> (a / 2.0f) - 64.0f
            ObdPid.SHORT_FUEL_TRIM -> ((a - 128) * 100.0f) / 128.0f
            ObdPid.LONG_FUEL_TRIM -> ((a - 128) * 100.0f) / 128.0f
        }
    }

    /**
     * Parses `ATRV` battery voltage response like "14.2V" or "12.5".
     */
    fun parseAtrvVoltage(rawResponse: String): Float? {
        val cleaned = cleanResponse(rawResponse)
        val match = Regex("""(\d{1,2}\.\d{1,2})""").find(cleaned) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    /**
     * Parses Mode 01 PID 01 (Monitor status since DTCs cleared) for MIL flag & DTC count.
     * Response: 41 01 AA BB CC DD -> bit 7 of AA is MIL, bits 0..6 of AA is DTC count.
     */
    fun parseMilStatus(rawResponse: String): Pair<Boolean, Int>? {
        if (isErrorResponse(rawResponse)) return null
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "4101") ?: return null
        val statusByte = bytes.firstOrNull() ?: return null
        val milOn = (statusByte and 0x80) != 0
        val count = statusByte and 0x7F
        return milOn to count
    }

    /**
     * Parses Mode 03 response (`43 ...`) into decoded SAE J2012 DTC codes.
     */
    fun parseMode03Dtcs(rawResponse: String): List<DtcCode> {
        if (isErrorResponse(rawResponse)) return emptyList()
        val lines = rawResponse.replace("\r", "\n").lines()
        val collectedBytes = mutableListOf<Int>()

        for (line in lines) {
            val hexOnly = line.uppercase().replace(Regex("[^0-9A-F]"), "")
            val idx = hexOnly.indexOf("43")
            if (idx >= 0) {
                var payload = hexOnly.substring(idx + 2)
                // Handle ISO-TP / ELM327 byte count prefix if present on CAN responses when odd pairs occur
                if (payload.length % 4 == 2 && payload.length > 2) {
                    payload = payload.substring(2)
                }
                var i = 0
                while (i + 1 < payload.length) {
                    val byteVal = payload.substring(i, i + 2).toIntOrNull(16)
                    if (byteVal != null) collectedBytes.add(byteVal)
                    i += 2
                }
            }
        }

        val codes = mutableListOf<String>()
        var i = 0
        while (i + 1 < collectedBytes.size) {
            val b1 = collectedBytes[i]
            val b2 = collectedBytes[i + 1]
            i += 2
            if (b1 == 0 && b2 == 0) continue

            val prefixChar = when ((b1 and 0xC0) shr 6) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val digit2 = (b1 and 0x30) shr 4
            val digit3 = b1 and 0x0F
            val digit4 = (b2 and 0xF0) shr 4
            val digit5 = b2 and 0x0F
            val code = String.format("%c%X%X%X%X", prefixChar, digit2, digit3, digit4, digit5)
            if (code !in codes) {
                codes.add(code)
            }
        }

        return codes.map { decodeDtc(it) }
    }

    fun decodeDtc(code: String): DtcCode {
        val system = when (code.firstOrNull()) {
            'P' -> "Силовая установка (ДВС / КПП)"
            'C' -> "Шасси (ABS / ESP / Подвеска)"
            'B' -> "Кузовная электроника"
            'U' -> "Шина данных (CAN / Сеть)"
            else -> "Электронный блок управления"
        }
        val entry = knownDtcs[code]
        return if (entry != null) {
            DtcCode(
                code = code,
                system = system,
                description = entry.first,
                severity = entry.second
            )
        } else {
            DtcCode(
                code = code,
                system = system,
                description = "Код неисправности $code ($system). Требуется проверка блока управления.",
                severity = if (code.startsWith("P03") || code.startsWith("P01")) {
                    DtcSeverity.MODERATE
                } else {
                    DtcSeverity.INFO
                }
            )
        }
    }

    private fun extractHexBytesAfterHeader(rawResponse: String, expectedHeader: String): List<Int>? {
        val normalizedLines = rawResponse
            .replace("\r", "\n")
            .lines()
            .map { it.uppercase().replace(Regex("[^0-9A-F]"), "") }
            .filter { it.isNotEmpty() }

        for (line in normalizedLines) {
            val headerIdx = line.indexOf(expectedHeader.uppercase())
            if (headerIdx >= 0) {
                val payload = line.substring(headerIdx + expectedHeader.length)
                if (payload.length < 2) continue
                val bytes = mutableListOf<Int>()
                var i = 0
                while (i + 1 < payload.length) {
                    val b = payload.substring(i, i + 2).toIntOrNull(16) ?: break
                    bytes.add(b)
                    i += 2
                }
                if (bytes.isNotEmpty()) return bytes
            }
        }
        return null
    }

    private val knownDtcs: Map<String, Pair<String, DtcSeverity>> = mapOf(
        "P0100" to ("Неисправность цепи датчика массового расхода воздуха (ДМРВ)" to DtcSeverity.MODERATE),
        "P0101" to ("Выход сигнала ДМРВ за допустимый диапазон" to DtcSeverity.MODERATE),
        "P0106" to ("Выход сигнала датчика абсолютного давления (ДАД) за диапазон" to DtcSeverity.MODERATE),
        "P0113" to ("Высокий уровень сигнала датчика температуры впускаемого воздуха" to DtcSeverity.INFO),
        "P0118" to ("Высокий уровень сигнала датчика температуры охлаждающей жидкости" to DtcSeverity.CRITICAL),
        "P0128" to ("Температура ОЖ ниже температуры открытия термостата" to DtcSeverity.INFO),
        "P0130" to ("Неисправность цепи датчика кислорода (Банк 1, Датчик 1)" to DtcSeverity.MODERATE),
        "P0135" to ("Неисправность цепи подогрева датчика кислорода (B1S1)" to DtcSeverity.INFO),
        "P0171" to ("Слишком бедная топливовоздушная смесь (Банк 1)" to DtcSeverity.MODERATE),
        "P0172" to ("Слишком богатая топливовоздушная смесь (Банк 1)" to DtcSeverity.MODERATE),
        "P0300" to ("Обнаружены множественные/случайные пропуски зажигания" to DtcSeverity.CRITICAL),
        "P0301" to ("Пропуски зажигания в цилиндре 1" to DtcSeverity.CRITICAL),
        "P0302" to ("Пропуски зажигания в цилиндре 2" to DtcSeverity.CRITICAL),
        "P0303" to ("Пропуски зажигания в цилиндре 3" to DtcSeverity.CRITICAL),
        "P0304" to ("Пропуски зажигания в цилиндре 4" to DtcSeverity.CRITICAL),
        "P0335" to ("Неисправность датчика положения коленчатого вала (ДПКВ)" to DtcSeverity.CRITICAL),
        "P0340" to ("Неисправность цепи датчика положения распредвала (ДПРВ)" to DtcSeverity.MODERATE),
        "P0420" to ("Эффективность катализатора ниже порога (Банк 1)" to DtcSeverity.INFO),
        "P0442" to ("Небольшая утечка в системе улавливания паров топлива (EVAP)" to DtcSeverity.INFO),
        "P0500" to ("Неисправность датчика скорости автомобиля (ДСА)" to DtcSeverity.MODERATE),
        "P0505" to ("Неисправность регулятора холостого хода" to DtcSeverity.MODERATE),
        "P0562" to ("Низкое напряжение бортовой сети автомобиля" to DtcSeverity.MODERATE),
        "U0100" to ("Потеря связи с блоком управления двигателем (ECM/PCM)" to DtcSeverity.CRITICAL),
        "U0121" to ("Потеря связи с модулем ABS" to DtcSeverity.CRITICAL),
        "C0035" to ("Неисправность датчика скорости левого переднего колеса" to DtcSeverity.MODERATE)
    )
}
