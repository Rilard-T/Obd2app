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

    @Test
    fun parseImReadiness_sparkAndDiesel_decodesMilAndMonitors() {
        // 41 01 82 07 65 00 -> MIL ON (0x80), 2 DTCs (0x02), Spark ignition (B3=0), C=0x65, D=0x00 (all ready)
        val spark = Elm327Parser.parseImReadiness("41 01 82 07 65 00\r>")
        assertNotNull(spark)
        assertTrue(spark!!.milOn)
        assertEquals(2, spark.dtcCount)
        assertFalse(spark.isCompressionIgnition)
        assertTrue(spark.allSupportedReady)

        // Compression ignition: B bit 3 = 1 -> 0x0F, D=0x40 (DPF incomplete)
        val diesel = Elm327Parser.parseImReadiness("41 01 00 0F 40 40\r>")
        assertNotNull(diesel)
        assertFalse(diesel!!.milOn)
        assertTrue(diesel.isCompressionIgnition)
        assertFalse(diesel.allSupportedReady)
        val dpf = diesel.monitors.first { it.id == "DPF" }
        assertTrue(dpf.supported)
        assertFalse(dpf.completed)
    }

    @Test
    fun parseMode02FreezeFrame_decodesTriggerAndTelemetry() {
        // 42 02 00 03 01 -> trigger DTC P0301
        val trigger = Elm327Parser.parseFreezeFrameDtc("42 02 00 03 01\r>")
        assertEquals("P0301", trigger)

        // 42 0C 00 1A F8 -> RPM = 1726.0
        val rpm = Elm327Parser.parseMode02Pid(ObdPid.ENGINE_RPM, "42 0C 00 1A F8\r>")
        assertNotNull(rpm)
        assertEquals(1726.0f, rpm!!, 0.1f)

        // 42 05 00 8A -> Coolant = 138 - 40 = 98 C
        val coolant = Elm327Parser.parseMode02Pid(ObdPid.COOLANT_TEMP, "42 05 00 8A\r>")
        assertNotNull(coolant)
        assertEquals(98.0f, coolant!!, 0.1f)
    }

    @Test
    fun parseMode09VinAndCalId_reassemblesIsoTpFrames() {
        // "WVWZZZ1KZ8W123456" in hex ASCII:
        // 57 56 57 5A 5A 5A 31 4B 5A 38 57 31 32 33 34 35 36
        val vinReply = """
            0: 49 02 01 57 56 57
            1: 5A 5A 5A 31 4B 5A 38
            2: 57 31 32 33 34 35 36
            >
        """.trimIndent()
        val vin = Elm327Parser.parseMode09Vin(vinReply)
        assertEquals("WVWZZZ1KZ8W123456", vin)

        // Calibration ID: "SW04E90602" -> 53 57 30 34 45 39 30 36 30 32
        val calReply = """
            0: 49 04 01 53 57 30
            1: 34 45 39 30 36 30 32
            >
        """.trimIndent()
        val cal = Elm327Parser.parseMode09CalId(calReply)
        assertEquals("SW04E90602", cal)
    }

    @Test
    fun parseMode07Dtcs_decodesPendingCodes() {
        // 47 02 01 71 04 20 -> P0171, P0420
        val pending = Elm327Parser.parseMode07Dtcs("47 01 71 04 20\r>", isEnglish = true)
        assertEquals(2, pending.size)
        assertTrue(pending.any { it.code == "P0171" })
        assertTrue(pending.any { it.code == "P0420" })
    }

    @Test
    fun parseMode06Tests_decodesPassAndFailMonitors() {
        // MID=21 TID=80 UAS=27 (ratio) val=0190(400) min=0100(256) max=0300(768) -> PASS
        // MID=A2 TID=0B UAS=24 (counts) val=0014(20) min=0000(0) max=000A(10) -> FAIL
        val raw = """
            7E8 46 21 80 27 01 90 01 00 03 00
            7E8 46 A2 0B 24 00 14 00 00 00 0A
            >
        """.trimIndent()
        val tests = Elm327Parser.parseMode06Tests(raw)
        assertEquals(2, tests.size)
        val cat = tests[0]
        assertEquals("21", cat.midHex)
        assertEquals(400, cat.value)
        assertTrue(cat.passed)
        val misfire = tests[1]
        assertEquals("A2", misfire.midHex)
        assertEquals(20, misfire.value)
        assertFalse(misfire.passed)
    }

    @Test
    fun oemPidCatalog_allPacksHaveValidTorqueEquations() {
        val packs = com.example.obd.protocol.OemPidCatalog.packs
        assertTrue(packs.size >= 5)
        packs.forEach { pack ->
            assertTrue("Pack ${pack.id} empty", pack.pids.isNotEmpty())
            pack.pids.forEach { pid ->
                val res = TorqueEquationEvaluator.validateEquation(pid.equation, isEnglish = true)
                assertTrue("Invalid equation in ${pid.modeAndPid}: ${pid.equation}", res.isSuccess)
            }
        }
    }

    @Test
    fun telemetryDatalogger_recordsFramesAndExportsCsv() {
        val logger = com.example.obd.data.TelemetryDatalogger()
        val rpmSensor = ObdPid.ENGINE_RPM.toTelemetrySensor(isEnglish = true)
        val spdSensor = ObdPid.VEHICLE_SPEED.toTelemetrySensor(isEnglish = true)
        logger.startRecording()
        logger.onReadingsSnapshot(
            mapOf(
                "010C" to com.example.obd.model.SensorReading(
                    sensorId = "010C",
                    sensor = rpmSensor,
                    value = 2400f
                ),
                "010D" to com.example.obd.model.SensorReading(
                    sensorId = "010D",
                    sensor = spdSensor,
                    value = 80f
                )
            )
        )
        logger.stopRecording()
        assertEquals(1, logger.state.value.frames.size)
        val csv = logger.exportCsv(
            sensors = listOf(rpmSensor, spdSensor),
            useFahrenheit = false,
            pressureUnit = com.example.obd.model.PressureUnit.KPA
        )
        assertTrue(csv.contains("timestamp_ms"))
        assertTrue(csv.contains("2400.00"))
        assertTrue(csv.contains("80.00"))
    }

    @Test
    fun parseMode01UInt16AndUInt8_prePurchasePids_decodeCorrectly() {
        // 0131 Distance since codes cleared: 41 31 01 F4 -> (0x01 * 256) + 0xF4 = 500 km
        assertEquals(500, Elm327Parser.parseMode01UInt16("0131", "41 31 01 F4\r>"))
        // 0121 Distance with MIL on: 41 21 00 2A -> 42 km
        assertEquals(42, Elm327Parser.parseMode01UInt16("0121", "41 21 00 2A\r>"))
        // 014E Time since trouble codes cleared: 41 4E 01 2C -> 300 min
        assertEquals(300, Elm327Parser.parseMode01UInt16("014E", "41 4E 01 2C\r>"))
        // 0130 Warm-ups since codes cleared: 41 30 0F -> 15
        assertEquals(15, Elm327Parser.parseMode01UInt8("0130", "41 30 0F\r>"))
    }
}
