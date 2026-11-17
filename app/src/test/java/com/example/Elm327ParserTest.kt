package com.example

import com.example.obd.model.ObdPid
import com.example.obd.protocol.Elm327Parser
import org.junit.Assert.assertEquals
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
    }
}
