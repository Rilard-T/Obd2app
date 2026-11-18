package com.example.obd.data

import com.example.obd.model.CockpitDialTheme
import com.example.obd.model.CockpitPreset
import com.example.obd.model.FuelType
import com.example.obd.model.SensorAlertOverride
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class CustomPidBackupDto(
    val name: String,
    val shortName: String,
    val modeAndPid: String,
    val header: String = "",
    val equation: String,
    val unit: String,
    val minValue: Float = 0f,
    val maxValue: Float = 100f,
    val decimals: Int = 1,
    val enabled: Boolean = true,
    val warningHigh: Float? = null,
    val criticalHigh: Float? = null,
    val warningLow: Float? = null,
    val criticalLow: Float? = null,
    val alertsEnabled: Boolean = true
)

@Serializable
data class VehicleProfileBackupDto(
    val name: String,
    val vin: String? = null,
    val displacementLiters: Float = 2.0f,
    val fuelType: String = FuelType.PETROL.name,
    val redlineRpm: Int = 6500,
    val shiftLightRpm: Int = 6000,
    val fuelPricePerLiter: Float = 58.0f,
    val preferredPresetId: String = "main",
    val curbWeightKg: Int = 1450
)

@Serializable
data class AppBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMs: Long = System.currentTimeMillis(),
    val dialTheme: String = CockpitDialTheme.SPORT_CARBON.name,
    val shiftLightEnabled: Boolean = true,
    val shiftLightRpm: Int = 6000,
    val activePresetId: String = ObdUserSettings.DEFAULT_PRESET_ID,
    val cockpitPresets: List<CockpitPreset> = emptyList(),
    val androidAutoSensorIds: List<String> = emptyList(),
    val alertOverrides: List<SensorAlertOverride> = emptyList(),
    val customPids: List<CustomPidBackupDto> = emptyList(),
    val vehicleProfiles: List<VehicleProfileBackupDto> = emptyList()
)

object SettingsBackupManager {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToJson(
        settings: ObdUserSettings,
        customPids: List<CustomPidEntity>,
        profiles: List<VehicleProfileEntity>
    ): String {
        val payload = AppBackupPayload(
            schemaVersion = 1,
            exportedAtMs = System.currentTimeMillis(),
            dialTheme = settings.dialTheme.name,
            shiftLightEnabled = settings.shiftLightEnabled,
            shiftLightRpm = settings.shiftLightRpm,
            activePresetId = settings.activePresetId,
            cockpitPresets = settings.cockpitPresets,
            androidAutoSensorIds = settings.androidAutoSensorIds,
            alertOverrides = settings.alertOverrides.values.toList(),
            customPids = customPids.map {
                CustomPidBackupDto(
                    name = it.name,
                    shortName = it.shortName,
                    modeAndPid = it.modeAndPid,
                    header = it.header,
                    equation = it.equation,
                    unit = it.unit,
                    minValue = it.minValue,
                    maxValue = it.maxValue,
                    decimals = it.decimals,
                    enabled = it.enabled,
                    warningHigh = it.warningHigh,
                    criticalHigh = it.criticalHigh,
                    warningLow = it.warningLow,
                    criticalLow = it.criticalLow,
                    alertsEnabled = it.alertsEnabled
                )
            },
            vehicleProfiles = profiles.map {
                VehicleProfileBackupDto(
                    name = it.name,
                    vin = it.vin,
                    displacementLiters = it.displacementLiters,
                    fuelType = it.fuelType,
                    redlineRpm = it.redlineRpm,
                    shiftLightRpm = it.shiftLightRpm,
                    fuelPricePerLiter = it.fuelPricePerLiter,
                    preferredPresetId = it.preferredPresetId,
                    curbWeightKg = it.curbWeightKg
                )
            }
        )
        return json.encodeToString(payload)
    }

    fun decodeBackup(rawJson: String): Result<AppBackupPayload> = runCatching {
        val payload = json.decodeFromString<AppBackupPayload>(rawJson)
        require(payload.schemaVersion >= 1) { "Unsupported backup version" }
        payload
    }
}
