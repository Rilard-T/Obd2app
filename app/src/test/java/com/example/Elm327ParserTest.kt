package com.example

import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.obd.model.SensorCategory
import com.example.obd.model.TelemetrySensor
import com.example.obd.protocol.Elm327Benchmark
import com.example.obd.protocol.Elm327Parser
import com.example.obd.protocol.TorqueEquationEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Elm327ParserTest {

    @Test
    fun parseRpm_validResponse_returnsCorrectValue() {
        // 41 0C 1A F8 -> ((0x1A * 256) + 0xF8) / 4 = 6904 / 4 = 1726 RPM
        val rpm = Elm327Parser.parseMode01Pid(ObdPid.ENGINE_RPM, "41 0C 1A F8\r>")
        assertNotNull(rpm)
        assertEquals(1726.0f, rpm!!, 0.1f)
    }

    @Test
    fun parseCoolantTemp_compactResponse_returnsCelsius() {
        // 41 05 82 -> 0x82 (130) - 40 = 90 C
        val temp = Elm327Parser.parseMode01Pid(ObdPid.COOLANT_TEMP, "SEARCHING...\r410582\r>")
        assertNotNull(temp)
        assertEquals(90.0f, temp!!, 0.1f)
    }

    @Test
    fun parseAtrvVoltage_returnsFloat() {
        val voltage = Elm327Parser.parseAtrvVoltage("14.3V\r>")
        assertNotNull(voltage)
        assertEquals(14.3f, voltage!!, 0.01f)
    }

    @Test
    fun parseMode03Dtcs_decodesStandardCodes() {
        // 43 01 33 03 01 -> P0133, P0301
        val codes = Elm327Parser.parseMode03Dtcs("43 01 33 03 01\r>")
        assertEquals(2, codes.size)
        assertTrue(codes.any { it.code == "P0133" })
        assertTrue(codes.any { it.code == "P0301" })

        val enCodes = Elm327Parser.parseMode03Dtcs("43 01 33 03 01\r>", isEnglish = true)
        assertEquals(2, enCodes.size)
        assertTrue(enCodes.first { it.code == "P0301" }.description.contains("Misfire", ignoreCase = true))
    }

    @Test
    fun parseSupportedPidBitmap_decodesBitmaskCorrectly() {
        // 41 00 BE 3E B8 11 -> classic bitmask supporting 0101, 0103, 0104, 0105, 0106, 0107, 010B, 010C, 010D, 010E, 010F, 0110, 0111, ..., 0120
        val supported = Elm327Parser.parseSupportedPidBitmap(0x00, "41 00 BE 3E B8 11\r>")
        assertTrue("010C" in supported)
        assertTrue("010D" in supported)
        assertTrue("0105" in supported)
        assertTrue("0120" in supported)
        assertFalse("0102" in supported)
    }

    @Test
    fun extractIsoTpPayload_reassemblesIndexedAndRawCanMultiframeReplies() {
        val elmIndexed = """
            0: 62 1E 1C 11 22 33
            1: 44 55 66 77 88 99 AA
            >
        """.trimIndent()
        val indexedBytes = Elm327Parser.extractIsoTpPayload("621E1C", elmIndexed)
        assertNotNull(indexedBytes)
        assertEquals(10, indexedBytes!!.size)
        assertEquals(0x11, indexedBytes[0])
        assertEquals(0xAA, indexedBytes[9])

        // Raw CAN ISO-TP with 7E8 CAN ID: 10 0C 62 21 01 A1 B2 C3 + 21 D4 E5 F6 01 02 03 04
        // Total length 0x0C = 12 bytes (3 header bytes 62 21 01 + 9 payload bytes)
        val rawCan = """
            7E8 10 0C 62 21 01 A1 B2 C3
            7E8 21 D4 E5 F6 01 02 03 04
        """.trimIndent()
        val canBytes = Elm327Parser.extractIsoTpPayload("622101", rawCan)
        assertNotNull(canBytes)
        assertEquals(9, canBytes!!.size)
        assertEquals(0xA1, canBytes[0])
        assertEquals(0xF6, canBytes[5])
        assertEquals(0x03, canBytes[8])
    }

    @Test
    fun evaluateAlert_respectsCustomHighAndLowThresholds() {
        val sensor = TelemetrySensor(
            id = "CUSTOM_1",
            modeAndPid = "221E1C",
            displayName = "Темп. АКПП",
            shortName = "АКПП",
            unit = "°C",
            minRange = 0f,
            maxRange = 150f,
            category = SensorCategory.CUSTOM,
            warningThreshold = 100f,
            criticalThreshold = 115f,
            lowWarningThreshold = 10f,
            lowCriticalThreshold = 0f,
            alertsEnabled = true
        )
        assertEquals(AlertLevel.NORMAL, sensor.evaluateAlert(85f))
        assertEquals(AlertLevel.WARNING, sensor.evaluateAlert(102f))
        assertEquals(AlertLevel.CRITICAL, sensor.evaluateAlert(118f))
        assertEquals(AlertLevel.WARNING, sensor.evaluateAlert(5f))
        assertEquals(AlertLevel.CRITICAL, sensor.evaluateAlert(-2f))
        assertEquals(AlertLevel.NORMAL, sensor.copy(alertsEnabled = false).evaluateAlert(130f))
    }

    @Test
    fun benchmarkClassify_detectsStrippedV21CloneVsGenuineFastAdapter() {
        val clone = Elm327Benchmark.classifyAdapter(
            version = "ELM327 v2.1",
            avgLatencyMs = 195L,
            supportsAdaptiveTiming = false,
            supportsTimeoutCmd = false
        )
        assertTrue(clone.first.contains("клон", ignoreCase = true))

        val fast = Elm327Benchmark.classifyAdapter(
            version = "ELM327 v1.5",
            avgLatencyMs = 38L,
            supportsAdaptiveTiming = true,
            supportsTimeoutCmd = true
        )
        assertTrue(fast.first.contains("Отлич", ignoreCase = true))

        val cloneEn = Elm327Benchmark.classifyAdapter(
            version = "ELM327 v2.1",
            avgLatencyMs = 195L,
            supportsAdaptiveTiming = false,
            supportsTimeoutCmd = false,
            isEnglish = true
        )
        assertTrue(cloneEn.first.contains("Clone", ignoreCase = true))

        val fastEn = Elm327Benchmark.classifyAdapter(
            version = "ELM327 v1.5",
            avgLatencyMs = 38L,
            supportsAdaptiveTiming = true,
            supportsTimeoutCmd = true,
            isEnglish = true
        )
        assertTrue(fastEn.first.contains("Excellent", ignoreCase = true))
    }

    @Test
    fun localizedPidAndEquationValidation_returnsEnglishMessages() {
        val coolantEn = ObdPid.COOLANT_TEMP.toTelemetrySensor(isEnglish = true)
        assertEquals("Coolant Temp", coolantEn.displayName)
        assertEquals("Coolant", coolantEn.shortName)

        val err = TorqueEquationEvaluator.validateEquation("", isEnglish = true)
        assertTrue(err.isFailure)
        assertTrue(err.exceptionOrNull()?.message?.contains("empty", ignoreCase = true) == true)
    }
}
