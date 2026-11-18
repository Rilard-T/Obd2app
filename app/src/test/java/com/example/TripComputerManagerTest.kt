package com.example

import com.example.obd.data.TripComputerManager
import com.example.obd.model.AccelDiscipline
import com.example.obd.model.AccelTimerPhase
import com.example.obd.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripComputerManagerTest {

    @Test
    fun tripStats_mafAndSpeedDensity_accumulateDistanceFuelAndIdle() {
        val manager = TripComputerManager()
        // Initial standstill sample at t=0
        manager.onTelemetrySample(
            timestampMs = 1_000_000L,
            speedKmh = 0f,
            rpm = 750f,
            coolantTempC = 88f,
            mafGramsPerSec = null,
            intakeMapKpa = 32f,
            intakeAirTempC = 25f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f
        )
        // Idle 4 seconds later at speed 0
        manager.onTelemetrySample(
            timestampMs = 1_004_000L,
            speedKmh = 0f,
            rpm = 750f,
            coolantTempC = 90f,
            mafGramsPerSec = null,
            intakeMapKpa = 32f,
            intakeAirTempC = 25f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f
        )
        assertTrue(manager.tripStats.value.idleTimeSec >= 4L)
        // Speed-density estimate should yield positive instant L/h
        assertNotNull(manager.tripStats.value.instantLPerHour)
        assertTrue(manager.tripStats.value.instantLPerHour!! > 0f)

        // Cruise at 90 km/h with direct MAF for 4 seconds
        manager.onTelemetrySample(
            timestampMs = 1_008_000L,
            speedKmh = 90f,
            rpm = 2400f,
            coolantTempC = 92f,
            mafGramsPerSec = 18.0f,
            intakeMapKpa = null,
            intakeAirTempC = 22f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f
        )
        val stats = manager.tripStats.value
        assertTrue(stats.distanceKm > 0.04f)
        assertTrue(stats.fuelUsedLiters > 0f)
        assertTrue(stats.estimatedCost > 0f)
        assertEquals(90f, stats.maxSpeedKmh, 0.1f)
        assertEquals(92f, stats.maxCoolantTempC!!, 0.1f)
    }

    @Test
    fun accelTimer_0To100_triggersAndInterpolatesCompletion() {
        val manager = TripComputerManager()
        manager.selectAccelDiscipline(AccelDiscipline.ZERO_TO_100)
        manager.armAccelTimer()
        assertEquals(AccelTimerPhase.ARMED, manager.accelTimer.value.phase)

        var completedSec: Float? = null
        // Standstill -> launch at t=0
        manager.onTelemetrySample(
            timestampMs = 10_000L,
            speedKmh = 0f,
            rpm = 2500f,
            coolantTempC = 90f,
            mafGramsPerSec = 10f,
            intakeMapKpa = null,
            intakeAirTempC = 25f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 58f
        )
        assertEquals(AccelTimerPhase.ARMED, manager.accelTimer.value.phase)

        // Launch detected at speed=5 km/h
        manager.onTelemetrySample(
            timestampMs = 10_200L,
            speedKmh = 5f,
            rpm = 3500f,
            coolantTempC = 90f,
            mafGramsPerSec = 25f,
            intakeMapKpa = null,
            intakeAirTempC = 25f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 58f
        )
        assertEquals(AccelTimerPhase.RUNNING, manager.accelTimer.value.phase)

        // Mid sample 60 km/h
        manager.onTelemetrySample(
            timestampMs = 13_200L,
            speedKmh = 60f,
            rpm = 5500f,
            coolantTempC = 90f,
            mafGramsPerSec = 60f,
            intakeMapKpa = null,
            intakeAirTempC = 25f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 58f
        )

        // Overshoot sample 105 km/h at t = 16_200 -> 6.0s raw, interpolated earlier
        manager.onTelemetrySample(
            timestampMs = 16_200L,
            speedKmh = 105f,
            rpm = 6200f,
            coolantTempC = 91f,
            mafGramsPerSec = 85f,
            intakeMapKpa = null,
            intakeAirTempC = 25f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 58f
        ) { discipline, timeSec, peakSpeed, _ ->
            assertEquals(AccelDiscipline.ZERO_TO_100, discipline)
            assertEquals(105f, peakSpeed, 0.1f)
            completedSec = timeSec
        }

        assertEquals(AccelTimerPhase.COMPLETED, manager.accelTimer.value.phase)
        assertNotNull(completedSec)
        // Interpolation from 60 -> 105 over 3.0s should subtract (5/45)*3s = 0.333s -> ~5.67s
        assertTrue(completedSec!! in 5.5f..5.85f)
    }

    @Test
    fun tripStats_calibrationFactorsScaleSpeedDistanceAndFuelUsed() {
        val base = TripComputerManager()
        val calibrated = TripComputerManager()

        base.onTelemetrySample(
            timestampMs = 1_000_000L,
            speedKmh = 100f,
            rpm = 2500f,
            coolantTempC = 90f,
            mafGramsPerSec = 20f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            speedCalibrationFactor = 1.0f,
            fuelCalibrationFactor = 1.0f
        )
        base.onTelemetrySample(
            timestampMs = 1_004_000L,
            speedKmh = 100f,
            rpm = 2500f,
            coolantTempC = 90f,
            mafGramsPerSec = 20f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            speedCalibrationFactor = 1.0f,
            fuelCalibrationFactor = 1.0f
        )

        calibrated.onTelemetrySample(
            timestampMs = 1_000_000L,
            speedKmh = 100f,
            rpm = 2500f,
            coolantTempC = 90f,
            mafGramsPerSec = 20f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            speedCalibrationFactor = 1.05f,
            fuelCalibrationFactor = 1.20f
        )
        calibrated.onTelemetrySample(
            timestampMs = 1_004_000L,
            speedKmh = 100f,
            rpm = 2500f,
            coolantTempC = 90f,
            mafGramsPerSec = 20f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            speedCalibrationFactor = 1.05f,
            fuelCalibrationFactor = 1.20f
        )

        assertEquals(base.tripStats.value.distanceKm * 1.05f, calibrated.tripStats.value.distanceKm, 0.002f)
        assertEquals(base.tripStats.value.fuelUsedLiters * 1.20f, calibrated.tripStats.value.fuelUsedLiters, 0.002f)
    }

    @Test
    fun virtualDyno_ecoScoreAndAccelCurve_computeAndCaptureCorrectly() {
        val manager = TripComputerManager()
        manager.selectAccelDiscipline(AccelDiscipline.ZERO_TO_60)
        manager.armAccelTimer()

        var capturedSamplesSize = 0
        // Sample 0: cold engine at standstill
        manager.onTelemetrySample(
            timestampMs = 10_000L,
            speedKmh = 0f,
            rpm = 900f,
            coolantTempC = 45f,
            mafGramsPerSec = 5f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            curbWeightKg = 1500,
            shiftLightRpm = 6000
        )

        // Sample 1: hard launch on cold engine -> triggers RUNNING + cold rev + harsh event + dyno HP
        manager.onTelemetrySample(
            timestampMs = 11_000L,
            speedKmh = 25f, // +6.94 m/s^2 harsh accel (>4.2 m/s^2)
            rpm = 3800f,    // > 3200 RPM with coolant 45C (<60C)
            coolantTempC = 45f,
            mafGramsPerSec = 35f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            curbWeightKg = 1500,
            shiftLightRpm = 6000
        )

        // Sample 2: finish 0-60 km/h
        manager.onTelemetrySample(
            timestampMs = 13_500L,
            speedKmh = 64f,
            rpm = 5400f,
            coolantTempC = 50f,
            mafGramsPerSec = 65f,
            intakeMapKpa = null,
            intakeAirTempC = 20f,
            displacementLiters = 2.0f,
            fuelType = FuelType.PETROL,
            fuelPricePerLiter = 60f,
            curbWeightKg = 1500,
            shiftLightRpm = 6000
        ) { _, _, _, _ ->
            capturedSamplesSize = manager.accelTimer.value.samples.size
        }

        val stats = manager.tripStats.value
        assertTrue("Expected positive peak HP, got ${stats.peakHorsepower}", stats.peakHorsepower > 20f)
        assertTrue("Expected positive peak torque, got ${stats.peakTorqueNm}", stats.peakTorqueNm > 20f)
        assertTrue("Expected harsh event count >= 1, got ${stats.harshEventsCount}", stats.harshEventsCount >= 1)
        assertTrue("Expected cold rev seconds >= 1, got ${stats.coldRevSeconds}", stats.coldRevSeconds >= 1L)
        assertTrue("Expected ecoScore < 100, got ${stats.ecoScore}", stats.ecoScore < 100)
        assertTrue("Expected >= 2 accel curve samples, got $capturedSamplesSize", capturedSamplesSize >= 2)
    }
}
