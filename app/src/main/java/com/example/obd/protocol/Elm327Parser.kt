package com.example.obd.protocol

import com.example.obd.model.DtcCode
import com.example.obd.model.DtcSeverity
import com.example.obd.model.ImMonitorStatus
import com.example.obd.model.ImReadinessResult
import com.example.obd.model.Mode06TestResult
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
     * Parses a 2-byte unsigned Mode 01 PID (`41 <PID> AA BB` -> `AA*256 + BB`),
     * used for PIDs like 0121 (Distance with MIL), 0131 (Distance since DTC cleared), 014E (Time since DTC cleared).
     */
    fun parseMode01UInt16(pidHex: String, rawResponse: String): Int? {
        if (isErrorResponse(rawResponse)) return null
        val pid = pidHex.uppercase().let { if (it.length == 4 && it.startsWith("01")) it.substring(2) else it }
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "41$pid")
            ?: return null
        val a = bytes.getOrNull(0) ?: return null
        val b = bytes.getOrNull(1) ?: return null
        return (a * 256) + b
    }

    /**
     * Parses a 1-byte unsigned Mode 01 PID (`41 <PID> AA` -> `AA`),
     * used for PIDs like 0130 (Warm-ups since codes cleared).
     */
    fun parseMode01UInt8(pidHex: String, rawResponse: String): Int? {
        if (isErrorResponse(rawResponse)) return null
        val pid = pidHex.uppercase().let { if (it.length == 4 && it.startsWith("01")) it.substring(2) else it }
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "41$pid")
            ?: return null
        return bytes.firstOrNull()
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
     * Parses full Mode 01 PID 01 (`41 01 AA BB CC DD`) into MIL, DTC count, and I/M readiness monitors.
     * In SAE J1979, Byte B/C availability bits are 1=supported, and B/D incomplete bits are 0=complete (ready).
     */
    fun parseImReadiness(rawResponse: String): ImReadinessResult? {
        if (isErrorResponse(rawResponse)) return null
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "4101") ?: return null
        if (bytes.size < 4) return null
        val a = bytes[0]
        val b = bytes[1]
        val c = bytes[2]
        val d = bytes[3]

        val milOn = (a and 0x80) != 0
        val dtcCount = a and 0x7F
        val isCompression = (b and 0x08) != 0

        val monitors = mutableListOf(
            ImMonitorStatus(
                id = "MIS",
                nameRu = "Пропуски зажигания",
                nameEn = "Misfire Monitor",
                supported = (b and 0x01) != 0,
                completed = (b and 0x10) == 0
            ),
            ImMonitorStatus(
                id = "FUEL",
                nameRu = "Топливная система",
                nameEn = "Fuel System",
                supported = (b and 0x02) != 0,
                completed = (b and 0x20) == 0
            ),
            ImMonitorStatus(
                id = "COMP",
                nameRu = "Компоненты ЭБУ",
                nameEn = "Components",
                supported = (b and 0x04) != 0,
                completed = (b and 0x40) == 0
            )
        )

        if (!isCompression) {
            monitors += listOf(
                ImMonitorStatus("CAT", "Катализатор", "Catalyst", (c and 0x01) != 0, (d and 0x01) == 0),
                ImMonitorStatus("HCAT", "Нагрев катализатора", "Heated Catalyst", (c and 0x02) != 0, (d and 0x02) == 0),
                ImMonitorStatus("EVAP", "Система EVAP (пары)", "EVAP System", (c and 0x04) != 0, (d and 0x04) == 0),
                ImMonitorStatus("AIR", "Подача втор. воздуха", "Secondary Air", (c and 0x08) != 0, (d and 0x08) == 0),
                ImMonitorStatus("O2", "Датчики кислорода (O2)", "O2 Sensor", (c and 0x20) != 0, (d and 0x20) == 0),
                ImMonitorStatus("O2H", "Подогрев O2-датчиков", "O2 Heater", (c and 0x40) != 0, (d and 0x40) == 0),
                ImMonitorStatus("EGR", "Клапан EGR / VVT", "EGR / VVT", (c and 0x80) != 0, (d and 0x80) == 0)
            )
        } else {
            monitors += listOf(
                ImMonitorStatus("NMHC", "Катализатор NMHC", "NMHC Catalyst", (c and 0x01) != 0, (d and 0x01) == 0),
                ImMonitorStatus("NOX", "Контроль NOx / SCR", "NOx / SCR", (c and 0x02) != 0, (d and 0x02) == 0),
                ImMonitorStatus("BOOST", "Давление наддува", "Boost Pressure", (c and 0x08) != 0, (d and 0x08) == 0),
                ImMonitorStatus("EGS", "Датчики выхлопа (EGS)", "Exhaust Gas Sensor", (c and 0x20) != 0, (d and 0x20) == 0),
                ImMonitorStatus("DPF", "Сажевый фильтр (DPF)", "PM Filter (DPF)", (c and 0x40) != 0, (d and 0x40) == 0),
                ImMonitorStatus("EGR", "Клапан EGR / VVT", "EGR / VVT", (c and 0x80) != 0, (d and 0x80) == 0)
            )
        }

        val supportedOnly = monitors.filter { it.supported }
        val allSupportedReady = supportedOnly.isNotEmpty() && supportedOnly.all { it.completed }
        return ImReadinessResult(
            milOn = milOn,
            dtcCount = dtcCount,
            isCompressionIgnition = isCompression,
            monitors = monitors,
            allSupportedReady = allSupportedReady
        )
    }

    /**
     * Parses Mode 02 PID 02 (`0202` -> `42 02 00 HH LL`) trigger DTC stored with the freeze frame.
     */
    fun parseFreezeFrameDtc(rawResponse: String): String? {
        if (isErrorResponse(rawResponse)) return null
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "4202") ?: return null
        val dataBytes = if (bytes.size >= 3 && bytes[0] == 0x00) bytes.drop(1) else bytes
        val b1 = dataBytes.getOrNull(0) ?: return null
        val b2 = dataBytes.getOrNull(1) ?: return null
        if (b1 == 0 && b2 == 0) return null
        val prefixChar = when ((b1 and 0xC0) shr 6) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val d2 = (b1 and 0x30) shr 4
        val d3 = b1 and 0x0F
        val d4 = (b2 and 0xF0) shr 4
        val d5 = b2 and 0x0F
        return String.format("%c%X%X%X%X", prefixChar, d2, d3, d4, d5)
    }

    /**
     * Parses a Mode 02 Freeze Frame PID (`02<PID>00` -> `42 <PID> 00 <A> [B]`).
     */
    fun parseMode02Pid(pid: ObdPid, rawResponse: String): Float? {
        if (isErrorResponse(rawResponse)) return null
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "42${pid.pidHex}") ?: return null
        val expectedDataBytes = when (pid) {
            ObdPid.ENGINE_RPM, ObdPid.CONTROL_MODULE_VOLTAGE, ObdPid.MAF_FLOW -> 2
            else -> 1
        }
        val dataBytes = if (bytes.size > expectedDataBytes && bytes[0] == 0x00) {
            bytes.drop(1)
        } else {
            bytes
        }
        val a = dataBytes.getOrNull(0) ?: return null
        val b = dataBytes.getOrNull(1)
        return when (pid) {
            ObdPid.ENGINE_RPM -> if (b == null) null else ((a * 256) + b) / 4.0f
            ObdPid.VEHICLE_SPEED -> a.toFloat()
            ObdPid.COOLANT_TEMP -> (a - 40).toFloat()
            ObdPid.ENGINE_LOAD -> (a * 100.0f) / 255.0f
            ObdPid.INTAKE_MAP -> a.toFloat()
            ObdPid.SHORT_FUEL_TRIM -> ((a - 128) * 100.0f) / 128.0f
            ObdPid.LONG_FUEL_TRIM -> ((a - 128) * 100.0f) / 128.0f
            else -> parseMode01Pid(
                pid,
                "41${pid.pidHex}" + dataBytes.joinToString("") { String.format("%02X", it) }
            )
        }
    }

    /**
     * Parses Mode 09 PID 02 (`0902` -> `49 02 [01] <17 ASCII bytes>`) VIN string.
     */
    fun parseMode09Vin(rawResponse: String): String? {
        if (isErrorResponse(rawResponse)) return null
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "4902") ?: return null
        val cleanedBytes = if (bytes.size > 17 && bytes.first() < 0x20) bytes.drop(1) else bytes
        val ascii = cleanedBytes
            .filter { it in 0x30..0x5A }
            .map { it.toChar() }
            .joinToString("")
            .uppercase()
        val match = Regex("[A-HJ-NPR-Z0-9]{17}").find(ascii)
        return match?.value ?: ascii.takeIf { it.length == 17 }
    }

    /**
     * Parses Mode 09 PID 04 (`0904` -> `49 04 [01] <ASCII calibration ID>`) string.
     */
    fun parseMode09CalId(rawResponse: String): String? {
        if (isErrorResponse(rawResponse)) return null
        val bytes = extractHexBytesAfterHeader(rawResponse, expectedHeader = "4904") ?: return null
        val payload = if (bytes.isNotEmpty() && bytes.first() < 0x20) bytes.drop(1) else bytes
        val ascii = payload
            .filter { it in 0x20..0x7E }
            .map { it.toChar() }
            .joinToString("")
            .trim()
        return ascii.takeIf { it.length >= 4 }
    }

    /**
     * Parses SAE J1979 Mode 01 supported PID bitmaps (`0100`, `0120`, `0140`, `0160`).
     * Returns normalized modeAndPid strings like `"010C"`, `"010D"`, `"0120"`.
     */
    fun parseSupportedPidBitmap(basePid: Int, rawResponse: String): Set<String> {
        if (isErrorResponse(rawResponse)) return emptySet()
        val expectedHeader = String.format("41%02X", basePid and 0xFF)
        val lines = rawResponse
            .replace("\r", "\n")
            .lines()
            .map { it.uppercase().replace(Regex("[^0-9A-F]"), "") }
            .filter { it.isNotEmpty() }

        val supported = mutableSetOf<String>()
        for (line in lines) {
            val idx = line.indexOf(expectedHeader)
            if (idx < 0) continue
            val payload = line.substring(idx + expectedHeader.length)
            if (payload.length < 8) continue
            val b0 = payload.substring(0, 2).toIntOrNull(16) ?: continue
            val b1 = payload.substring(2, 4).toIntOrNull(16) ?: continue
            val b2 = payload.substring(4, 6).toIntOrNull(16) ?: continue
            val b3 = payload.substring(6, 8).toIntOrNull(16) ?: continue
            val mask = ((b0.toLong() and 0xFFL) shl 24) or
                ((b1.toLong() and 0xFFL) shl 16) or
                ((b2.toLong() and 0xFFL) shl 8) or
                (b3.toLong() and 0xFFL)

            for (bitOffset in 0 until 32) {
                val bitIndex = 31 - bitOffset
                if (((mask ushr bitIndex) and 1L) == 1L) {
                    val pidNum = basePid + bitOffset + 1
                    supported.add(String.format("01%02X", pidNum and 0xFF))
                }
            }
        }
        return supported
    }

    /**
     * Parses Mode 03 response (`43 ...`) into decoded SAE J2012 stored DTC codes.
     */
    fun parseMode03Dtcs(rawResponse: String, isEnglish: Boolean = false): List<DtcCode> {
        return parseDtcResponse(serviceHeader = "43", rawResponse = rawResponse, isEnglish = isEnglish)
    }

    /**
     * Parses Mode 07 response (`47 ...`) into decoded SAE J2012 pending DTC codes.
     */
    fun parseMode07Dtcs(rawResponse: String, isEnglish: Boolean = false): List<DtcCode> {
        return parseDtcResponse(serviceHeader = "47", rawResponse = rawResponse, isEnglish = isEnglish)
    }

    private fun parseDtcResponse(
        serviceHeader: String,
        rawResponse: String,
        isEnglish: Boolean
    ): List<DtcCode> {
        if (isErrorResponse(rawResponse)) return emptyList()
        val lines = rawResponse.replace("\r", "\n").lines()
        val collectedBytes = mutableListOf<Int>()

        for (line in lines) {
            val hexOnly = line.uppercase().replace(Regex("[^0-9A-F]"), "")
            val idx = hexOnly.indexOf(serviceHeader)
            if (idx >= 0) {
                var payload = hexOnly.substring(idx + serviceHeader.length)
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

        return codes.map { decodeDtc(it, isEnglish) }
    }

    /**
     * Parses CAN Mode 06 (On-Board Monitoring Tests: `46 <MID> <TID> <UAS> <V_H> <V_L> <MIN_H> <MIN_L> <MAX_H> <MAX_L>`).
     * Handles both single-frame and concatenated multi-line 46 responses.
     */
    fun parseMode06Tests(rawResponse: String): List<Mode06TestResult> {
        if (isErrorResponse(rawResponse)) return emptyList()
        val lines = rawResponse.replace("\r", "\n").lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("SEARCHING", ignoreCase = true) }

        val results = mutableListOf<Mode06TestResult>()
        val recordBytes = mutableListOf<Int>()

        for (line in lines) {
            val stripped = stripOptionalCanIdPrefix(line)
            val idx = stripped.indexOf("46")
            if (idx >= 0) {
                val payloadHex = stripped.substring(idx + 2)
                recordBytes += parseHexPairs(payloadHex)
            }
        }

        var cursor = 0
        while (cursor + 8 < recordBytes.size) {
            val mid = recordBytes[cursor]
            val tid = recordBytes[cursor + 1]
            val uas = recordBytes[cursor + 2]
            val value = (recordBytes[cursor + 3] shl 8) or recordBytes[cursor + 4]
            val minLimit = (recordBytes[cursor + 5] shl 8) or recordBytes[cursor + 6]
            val maxLimit = (recordBytes[cursor + 7] shl 8) or recordBytes[cursor + 8]
            cursor += 9

            // Skip empty padding frames or bitmask MID 00/20/40/...
            if (mid == 0 && tid == 0 && uas == 0) continue
            if (mid % 0x20 == 0 && tid == 0) continue

            val (nameRu, nameEn) = describeMode06Mid(mid, tid)
            val passed = value in minLimit..maxLimit
            results += Mode06TestResult(
                midHex = String.format("%02X", mid),
                tidHex = String.format("%02X", tid),
                nameRu = nameRu,
                nameEn = nameEn,
                value = value,
                minLimit = minLimit,
                maxLimit = maxLimit,
                passed = passed,
                unitLabel = describeUasUnit(uas)
            )
        }
        return results
    }

    private fun describeMode06Mid(mid: Int, tid: Int): Pair<String, String> {
        val midHex = String.format("%02X", mid)
        return when (mid) {
            0x01 -> "Датчик O2 (Банк 1, Д1) TID $tid" to "O2 Sensor B1S1 (TID $tid)"
            0x02 -> "Датчик O2 (Банк 1, Д2) TID $tid" to "O2 Sensor B1S2 (TID $tid)"
            0x05 -> "Датчик O2 (Банк 2, Д1) TID $tid" to "O2 Sensor B2S1 (TID $tid)"
            0x21 -> "Эффективность катализатора B1" to "Catalyst Monitor Bank 1"
            0x22 -> "Эффективность катализатора B2" to "Catalyst Monitor Bank 2"
            0x3A -> "Утечка EVAP (0.040\")" to "EVAP Monitor (0.040\" leak)"
            0x3B -> "Утечка EVAP (0.020\")" to "EVAP Monitor (0.020\" leak)"
            0x3C -> "Малая утечка EVAP (0.020\")" to "EVAP Micro-Leak Check"
            0xA1 -> "Общий счётчик пропусков (EWMA)" to "Misfire General Monitor (EWMA)"
            0xA2 -> "Пропуски зажигания — Цилиндр 1" to "Cylinder 1 Misfire Counts"
            0xA3 -> "Пропуски зажигания — Цилиндр 2" to "Cylinder 2 Misfire Counts"
            0xA4 -> "Пропуски зажигания — Цилиндр 3" to "Cylinder 3 Misfire Counts"
            0xA5 -> "Пропуски зажигания — Цилиндр 4" to "Cylinder 4 Misfire Counts"
            0xA6 -> "Пропуски зажигания — Цилиндр 5" to "Cylinder 5 Misfire Counts"
            0xA7 -> "Пропуски зажигания — Цилиндр 6" to "Cylinder 6 Misfire Counts"
            else -> "Монитор ЭБУ MID $midHex (TID $tid)" to "ECU Monitor MID $midHex (TID $tid)"
        }
    }

    private fun describeUasUnit(uas: Int): String = when (uas) {
        0x01, 0x0A -> "mV"
        0x10 -> "ms"
        0x24 -> "counts"
        0x27 -> "ratio"
        else -> ""
    }

    fun decodeDtc(code: String, isEnglish: Boolean = false): DtcCode {
        val system = when (code.firstOrNull()) {
            'P' -> if (isEnglish) "Powertrain (Engine / Transmission)" else "Силовая установка (ДВС / КПП)"
            'C' -> if (isEnglish) "Chassis (ABS / ESP / Suspension)" else "Шасси (ABS / ESP / Подвеска)"
            'B' -> if (isEnglish) "Body Electronics" else "Кузовная электроника"
            'U' -> if (isEnglish) "Network / CAN Bus" else "Шина данных (CAN / Сеть)"
            else -> if (isEnglish) "Electronic Control Unit" else "Электронный блок управления"
        }
        val entry = if (isEnglish) knownDtcsEn[code] else knownDtcs[code]
        return if (entry != null) {
            DtcCode(
                code = code,
                system = system,
                description = entry.first,
                severity = entry.second
            )
        } else {
            val fallbackDesc = if (isEnglish) {
                "Diagnostic trouble code $code ($system). ECU inspection required."
            } else {
                "Код неисправности $code ($system). Требуется проверка блока управления."
            }
            DtcCode(
                code = code,
                system = system,
                description = fallbackDesc,
                severity = if (code.startsWith("P03") || code.startsWith("P01")) {
                    DtcSeverity.MODERATE
                } else {
                    DtcSeverity.INFO
                }
            )
        }
    }

    private fun extractHexBytesAfterHeader(rawResponse: String, expectedHeader: String): List<Int>? {
        return extractIsoTpPayload(expectedHeader, rawResponse)
    }

    /**
     * Extracts payload bytes following [expectedHeader] across:
     * 1) Standard single-frame replies (with or without CAN ID / PCI length byte)
     * 2) ELM327 formatted ISO-TP multi-frame lines (`0: 62 1E 1C ...`, `1: ...`)
     * 3) Raw CAN ISO-TP frames (`10 0E 62 1E 1C ...`, `21 ...`, `22 ...`)
     */
    fun extractIsoTpPayload(expectedHeader: String, rawResponse: String): List<Int>? {
        if (isErrorResponse(rawResponse)) return null
        val targetHeader = expectedHeader.uppercase().replace(Regex("[^0-9A-F]"), "")
        if (targetHeader.length < 4) return null

        val rawLines = rawResponse
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.equals(">", ignoreCase = true) &&
                    !it.equals("SEARCHING...", ignoreCase = true) &&
                    !it.startsWith("BUS INIT", ignoreCase = true)
            }

        // Case A: ELM327 multi-frame with explicit indices `0: ...`, `1: ...`
        val indexedRegex = Regex("""^([0-9A-Fa-f]{1,4})?:\s*([0-9A-Fa-f\s]+)$""")
        val indexedLines = rawLines.mapNotNull { line ->
            val match = indexedRegex.matchEntire(line) ?: return@mapNotNull null
            val idxStr = match.groupValues[1]
            val hex = match.groupValues[2].uppercase().replace(Regex("[^0-9A-F]"), "")
            val idx = idxStr.toIntOrNull(16) ?: return@mapNotNull null
            idx to hex
        }
        if (indexedLines.isNotEmpty()) {
            val combined = indexedLines
                .sortedBy { it.first }
                .joinToString("") { it.second }
            val hIdx = combined.indexOf(targetHeader)
            if (hIdx >= 0) {
                val payloadHex = combined.substring(hIdx + targetHeader.length)
                val bytes = parseHexPairs(payloadHex)
                if (bytes.isNotEmpty()) return bytes
            }
        }

        // Case B: Raw CAN ISO-TP First Frame + Consecutive Frames (`1x xx ...` / `2y ...`)
        val cleanedHexLines = rawLines.map { stripOptionalCanIdPrefix(it) }.filter { it.isNotEmpty() }
        val firstFrameIndex = cleanedHexLines.indexOfFirst { line ->
            line.length >= 8 && line.startsWith("1") && line.contains(targetHeader)
        }
        if (firstFrameIndex >= 0) {
            val firstLine = cleanedHexLines[firstFrameIndex]
            val hIdx = firstLine.indexOf(targetHeader)
            val declaredTotalBytes = if (hIdx >= 4) {
                firstLine.substring(0, 4).toIntOrNull(16)?.and(0x0FFF)
            } else null

            val assembled = StringBuilder(firstLine.substring(hIdx + targetHeader.length))
            var expectedSeq = 1
            for (lineIdx in (firstFrameIndex + 1) until cleanedHexLines.size) {
                val nextLine = cleanedHexLines[lineIdx]
                val seqChar = nextLine.firstOrNull()
                if (seqChar == '2' && nextLine.length >= 4) {
                    val seqNibble = nextLine.substring(1, 2).toIntOrNull(16)
                    if (seqNibble == (expectedSeq and 0x0F)) {
                        assembled.append(nextLine.substring(2))
                        expectedSeq++
                        continue
                    }
                }
                break
            }
            val headerByteLen = targetHeader.length / 2
            val maxPayloadBytes = declaredTotalBytes?.let { (it - headerByteLen).coerceAtLeast(1) }
            val bytes = parseHexPairs(assembled.toString(), maxBytes = maxPayloadBytes)
            if (bytes.isNotEmpty()) return bytes
        }

        // Case C: Single-frame line matching [expectedHeader] (optionally preceded by PCI byte)
        for (hexLine in cleanedHexLines) {
            val idx = hexLine.indexOf(targetHeader)
            if (idx >= 0) {
                val payload = hexLine.substring(idx + targetHeader.length)
                val bytes = parseHexPairs(payload)
                if (bytes.isNotEmpty()) return bytes
            }
        }
        return null
    }

    private fun stripOptionalCanIdPrefix(line: String): String {
        val tokens = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            val first = tokens[0].uppercase()
            // 11-bit CAN ID (3 hex chars, e.g. 7E8) or 29-bit CAN ID (8 hex chars, e.g. 18DAF110)
            if (first.matches(Regex("^[0-9A-F]{3}$|^[0-9A-F]{8}$"))) {
                return tokens.drop(1).joinToString("").uppercase().replace(Regex("[^0-9A-F]"), "")
            }
        }
        return line.uppercase().replace(Regex("[^0-9A-F]"), "")
    }

    private fun parseHexPairs(hexPayload: String, maxBytes: Int? = null): List<Int> {
        val bytes = mutableListOf<Int>()
        var i = 0
        while (i + 1 < hexPayload.length) {
            if (maxBytes != null && bytes.size >= maxBytes) break
            val b = hexPayload.substring(i, i + 2).toIntOrNull(16) ?: break
            bytes.add(b)
            i += 2
        }
        return bytes
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

    private val knownDtcsEn: Map<String, Pair<String, DtcSeverity>> = mapOf(
        "P0100" to ("Mass or Volume Air Flow (MAF) Circuit Malfunction" to DtcSeverity.MODERATE),
        "P0101" to ("Mass Air Flow (MAF) Sensor Range/Performance Problem" to DtcSeverity.MODERATE),
        "P0106" to ("Manifold Absolute Pressure (MAP) Sensor Range/Performance" to DtcSeverity.MODERATE),
        "P0113" to ("Intake Air Temperature (IAT) Sensor High Input" to DtcSeverity.INFO),
        "P0118" to ("Engine Coolant Temperature (ECT) Sensor High Input" to DtcSeverity.CRITICAL),
        "P0128" to ("Coolant Thermostat Below Thermostat Regulating Temperature" to DtcSeverity.INFO),
        "P0130" to ("O2 Sensor Circuit Malfunction (Bank 1 Sensor 1)" to DtcSeverity.MODERATE),
        "P0135" to ("O2 Sensor Heater Circuit Malfunction (Bank 1 Sensor 1)" to DtcSeverity.INFO),
        "P0171" to ("System Too Lean (Bank 1)" to DtcSeverity.MODERATE),
        "P0172" to ("System Too Rich (Bank 1)" to DtcSeverity.MODERATE),
        "P0300" to ("Random/Multiple Cylinder Misfire Detected" to DtcSeverity.CRITICAL),
        "P0301" to ("Cylinder 1 Misfire Detected" to DtcSeverity.CRITICAL),
        "P0302" to ("Cylinder 2 Misfire Detected" to DtcSeverity.CRITICAL),
        "P0303" to ("Cylinder 3 Misfire Detected" to DtcSeverity.CRITICAL),
        "P0304" to ("Cylinder 4 Misfire Detected" to DtcSeverity.CRITICAL),
        "P0335" to ("Crankshaft Position (CKP) Sensor A Circuit Malfunction" to DtcSeverity.CRITICAL),
        "P0340" to ("Camshaft Position (CMP) Sensor A Circuit Malfunction" to DtcSeverity.MODERATE),
        "P0420" to ("Catalyst System Efficiency Below Threshold (Bank 1)" to DtcSeverity.INFO),
        "P0442" to ("Evaporative Emission (EVAP) System Small Leak Detected" to DtcSeverity.INFO),
        "P0500" to ("Vehicle Speed Sensor (VSS) Malfunction" to DtcSeverity.MODERATE),
        "P0505" to ("Idle Air Control System Malfunction" to DtcSeverity.MODERATE),
        "P0562" to ("System Voltage Low" to DtcSeverity.MODERATE),
        "U0100" to ("Lost Communication With ECM/PCM" to DtcSeverity.CRITICAL),
        "U0121" to ("Lost Communication With Anti-Lock Brake System (ABS) Module" to DtcSeverity.CRITICAL),
        "C0035" to ("Left Front Wheel Speed Sensor Circuit Malfunction" to DtcSeverity.MODERATE)
    )
}
