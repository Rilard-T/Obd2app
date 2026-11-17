package com.example

import com.example.obd.protocol.TorqueEquationEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorqueEquationEvaluatorTest {

    @Test
    fun evaluate_standardRpmFormula_returnsExactValue() {
        val result = TorqueEquationEvaluator.evaluate("((A*256)+B)/4", listOf(0x1A, 0xF8))
        assertNotNull(result)
        assertEquals(1726.0f, result!!, 0.01f)
    }

    @Test
    fun evaluate_signedAndBitAndMathFunctions_workCorrectly() {
        // 0xFE as signed byte = -2
        val signedVal = TorqueEquationEvaluator.evaluate("SIGNED(A)", listOf(0xFE))
        assertEquals(-2.0f, signedVal!!, 0.01f)

        // 0x08 has bit 3 set (0b00001000)
        val bitOn = TorqueEquationEvaluator.evaluate("BIT(A,3)", listOf(0x08))
        val bitOff = TorqueEquationEvaluator.evaluate("BIT(A,2)", listOf(0x08))
        assertEquals(1.0f, bitOn!!, 0.01f)
        assertEquals(0.0f, bitOff!!, 0.01f)

        // ABS / MAX / MIN / SQRT
        val combined = TorqueEquationEvaluator.evaluate("MAX(ABS(-12), SQRT(16)) + MIN(5, 2)", listOf(0))
        assertEquals(14.0f, combined!!, 0.01f)
    }

    @Test
    fun parseCustomPidResponse_extendedMode22_parsesPayload() {
        // Request 221E1C -> Expected response prefix 621E1C
        val header = TorqueEquationEvaluator.expectedResponseHeader("221E1C")
        assertEquals("621E1C", header)

        val value = TorqueEquationEvaluator.parseCustomPidResponse(
            modeAndPid = "221E1C",
            equation = "A-40",
            rawResponse = "62 1E 1C 7D\r>"
        )
        assertNotNull(value)
        assertEquals(85.0f, value!!, 0.01f) // 0x7D = 125; 125 - 40 = 85
    }

    @Test
    fun validateEquation_rejectsInvalidSyntaxAndTrailingJunk() {
        assertTrue(TorqueEquationEvaluator.validateEquation("((A*256)+B)/4").isSuccess)
        assertTrue(TorqueEquationEvaluator.validateEquation("").isFailure)
        assertTrue(TorqueEquationEvaluator.validateEquation("A + Z").isFailure)
        assertTrue(TorqueEquationEvaluator.validateEquation("A + B extra").isFailure)
        assertNull(TorqueEquationEvaluator.evaluate("A/0", listOf(10)))
    }

    @Test
    fun parseTorqueCsv_parsesStandardAndQuotedRows() {
        val csv = """
            Name,ShortName,ModeAndPID,Equation,Min Value,Max Value,Units,Header
            "ATF Temperature, Main",ATF,221E1C,A-40,-20,140,°C,7E1
            Boost Pressure,Boost,010B,(A-101)*0.01,-1,2.5,bar,
            InvalidRow,Bad,01,((A,0,10,V,
        """.trimIndent()

        val parsed = TorqueEquationEvaluator.parseTorqueCsv(csv)
        assertEquals(2, parsed.size)
        assertEquals("ATF Temperature, Main", parsed[0].name)
        assertEquals("221E1C", parsed[0].modeAndPid)
        assertEquals("7E1", parsed[0].header)
        assertEquals("010B", parsed[1].modeAndPid)
        assertEquals("bar", parsed[1].unit)
    }

    @Test
    fun parseCustomPidResponse_multiframeIsoTpUpToByteP_works() {
        val multiFrame = """
            0: 62 21 10 01 02 03
            1: 04 05 06 07 08 09 0A
            2: 0B 0C 0D 0E 0F 10
        """.trimIndent()
        // Byte I (index 8) = 0x09 (9), Byte P (index 15) = 0x10 (16)
        val result = TorqueEquationEvaluator.parseCustomPidResponse(
            modeAndPid = "222110",
            equation = "I + P",
            rawResponse = multiFrame
        )
        assertNotNull(result)
        assertEquals(25.0f, result!!, 0.01f)
    }
}
