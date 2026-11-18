package com.example

import com.example.obd.data.CustomPidEntity
import com.example.obd.data.ObdUserSettings
import com.example.obd.data.SettingsBackupManager
import com.example.obd.data.VehicleProfileEntity
import com.example.obd.model.CockpitDialTheme
import com.example.obd.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    @Test
    fun exportAndDecodeBackup_roundTripsThemesCustomPidsAndVehicles() {
        val settings = ObdUserSettings(
            dialTheme = CockpitDialTheme.TRACK_RED,
            shiftLightEnabled = true,
            shiftLightRpm = 6400,
            androidAutoSensorIds = listOf("010C", "010D", "0105")
        )
        val pids = listOf(
            CustomPidEntity(
                id = 1L,
                name = "Oil Temp",
                shortName = "Oil",
                modeAndPid = "221144",
                header = "7E0",
                equation = "A-40",
                unit = "°C",
                minValue = 0f,
                maxValue = 150f,
                decimals = 0,
                enabled = true
            )
        )
        val vehicles = listOf(
            VehicleProfileEntity(
                id = 1L,
                name = "Track Hatch",
                vin = "WVWZZZ1KZ8W123456",
                displacementLiters = 2.0f,
                fuelType = FuelType.PETROL.name,
                redlineRpm = 7000,
                shiftLightRpm = 6400,
                fuelPricePerLiter = 62.5f,
                preferredPresetId = "track"
            )
        )

        val json = SettingsBackupManager.exportToJson(settings, pids, vehicles)
        val decoded = SettingsBackupManager.decodeBackup(json)
        assertTrue(decoded.isSuccess)

        val payload = decoded.getOrThrow()
        assertEquals(CockpitDialTheme.TRACK_RED.name, payload.dialTheme)
        assertEquals(6400, payload.shiftLightRpm)
        assertEquals(1, payload.customPids.size)
        assertEquals("221144", payload.customPids.first().modeAndPid)
        assertEquals(1, payload.vehicleProfiles.size)
        assertEquals("WVWZZZ1KZ8W123456", payload.vehicleProfiles.first().vin)
    }
}
