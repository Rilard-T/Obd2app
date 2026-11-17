package com.example

import com.example.car.AnalogDialScale
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorCategory
import com.example.obd.model.SensorReading
import com.example.obd.model.TelemetrySensor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalogDialScaleTest {

    @Test
    fun rpmDialUsesX1000ScaleAndWarningZone() {
        val sensor = ObdPid.ENGINE_RPM.toTelemetrySensor()
        val reading = SensorReading(
            sensorId = sensor.id,
            sensor = sensor,
            value = 820f
        )

        val spec = AnalogDialScale.buildSpec(
            sensor = sensor,
            reading = reading,
            useFahrenheit = false,
            pressureUnit = PressureUnit.KPA
        )

        assertEquals("x1000", spec.unitText)
        assertEquals("0,8", spec.centerValueText)
        assertEquals(AlertLevel.NORMAL, spec.alertLevel)
        assertNotNull(spec.warningStartFraction)
        assertTrue(spec.warningStartFraction!! in 0.70f..0.75f)
        assertTrue(spec.needleFraction in 0.09f..0.12f)
        val majorLabels = spec.ticks.mapNotNull { it.label }
        assertEquals("0", majorLabels.first())
        assertEquals("8", majorLabels.last())
    }

    @Test
    fun coolantDialUsesCleanStepsAndRedline() {
        val sensor = ObdPid.COOLANT_TEMP.toTelemetrySensor()
        val reading = SensorReading(
            sensorId = sensor.id,
            sensor = sensor,
            value = 66f
        )

        val spec = AnalogDialScale.buildSpec(
            sensor = sensor,
            reading = reading,
            useFahrenheit = false,
            pressureUnit = PressureUnit.KPA
        )

        assertEquals("°C", spec.unitText)
        assertEquals("66", spec.centerValueText)
        assertNotNull(spec.warningStartFraction)
        val majorLabels = spec.ticks.mapNotNull { it.label }
        assertEquals("-20", majorLabels.first())
        assertEquals("120", majorLabels.last())
    }

    @Test
    fun customTorquePidWorksInDialAndBottomStrip() {
        val dpfSensor = TelemetrySensor(
            id = "CUSTOM_1",
            modeAndPid = "221401",
            header = "7E0",
            displayName = "Заполненность DPF",
            shortName = "DPF",
            unit = "%",
            minRange = 0f,
            maxRange = 100f,
            decimals = 0,
            category = SensorCategory.CUSTOM,
            isCustom = true,
            customId = 1L,
            equation = "A*100/255"
        )
        val reading = SensorReading(
            sensorId = dpfSensor.id,
            sensor = dpfSensor,
            value = 17f
        )

        val spec = AnalogDialScale.buildSpec(
            sensor = dpfSensor,
            reading = reading,
            useFahrenheit = false,
            pressureUnit = PressureUnit.KPA
        )
        assertEquals("DPF", spec.title)
        assertEquals("17", spec.centerValueText)
        assertEquals("%", spec.unitText)
        assertEquals("17 %", AnalogDialScale.formatStripValue(dpfSensor, reading, false, PressureUnit.KPA))
    }

    @Test
    fun englishLocalizationFormatsDialTitleUnitsAndDecimalSeparator() {
        val rpmSensor = ObdPid.ENGINE_RPM.toTelemetrySensor(isEnglish = true)
        val rpmReading = SensorReading(
            sensorId = rpmSensor.id,
            sensor = rpmSensor,
            value = 820f
        )
        val rpmSpec = AnalogDialScale.buildSpec(
            sensor = rpmSensor,
            reading = rpmReading,
            useFahrenheit = false,
            pressureUnit = PressureUnit.KPA,
            isEnglish = true
        )
        assertEquals("RPM", rpmSpec.title)
        assertEquals("0.8", rpmSpec.centerValueText)
        assertEquals("x1000", rpmSpec.unitText)

        val mapSensor = ObdPid.INTAKE_MAP.toTelemetrySensor(isEnglish = true)
        val mapReading = SensorReading(
            sensorId = mapSensor.id,
            sensor = mapSensor,
            value = 101f
        )
        val mapSpec = AnalogDialScale.buildSpec(
            sensor = mapSensor,
            reading = mapReading,
            useFahrenheit = false,
            pressureUnit = PressureUnit.KPA,
            isEnglish = true
        )
        assertEquals("MAP", mapSpec.title)
        assertEquals("kPa", mapSpec.unitText)
    }
}
