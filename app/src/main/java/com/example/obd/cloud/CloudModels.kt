package com.example.obd.cloud

import androidx.annotation.Keep
import com.example.obd.data.CustomPidEntity
import com.example.obd.data.MaintenanceItemEntity
import com.example.obd.data.TripRecordEntity
import com.example.obd.data.VehicleProfileEntity
import com.example.obd.model.FuelType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class CloudVehicleProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val vin: String? = null,
    val displacementLiters: Double = 2.0,
    val fuelType: String = FuelType.PETROL.name,
    val redlineRpm: Int = 6500,
    val shiftLightRpm: Int = 6000,
    val fuelPricePerLiter: Double = 58.0,
    val odometerKm: Double = 0.0,
    val engineHours: Double = 0.0,
    val preferredPresetId: String = "main",
    val curbWeightKg: Int = 1450,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toEntity(existingId: Long = 0L): VehicleProfileEntity {
        val safeFuel = FuelType.entries.find { it.name == fuelType }?.name ?: FuelType.PETROL.name
        return VehicleProfileEntity(
            id = existingId,
            name = name.ifBlank { "Auto" },
            vin = vin?.takeIf { it.isNotBlank() },
            displacementLiters = displacementLiters.toFloat().coerceIn(0.3f, 12.0f),
            fuelType = safeFuel,
            redlineRpm = redlineRpm.coerceIn(2000, 15000),
            shiftLightRpm = shiftLightRpm.coerceIn(1500, 15000),
            fuelPricePerLiter = fuelPricePerLiter.toFloat().coerceIn(0f, 1000f),
            preferredPresetId = preferredPresetId.ifBlank { "main" },
            odometerKm = odometerKm.toFloat().coerceIn(0f, 5_000_000f),
            engineHours = engineHours.toFloat().coerceIn(0f, 200_000f),
            curbWeightKg = curbWeightKg.coerceIn(500, 15_000)
        )
    }
}

fun VehicleProfileEntity.toCloudWriteMap(
    userId: String,
    existingCreatedAt: Timestamp? = null
): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to userId,
        "name" to name.trim().take(80).ifBlank { "Auto" },
        "displacementLiters" to displacementLiters.toDouble().coerceIn(0.3, 12.0),
        "fuelType" to fuelTypeEnum.name,
        "redlineRpm" to redlineRpm.coerceIn(2000, 15000),
        "shiftLightRpm" to shiftLightRpm.coerceIn(1500, 15000),
        "fuelPricePerLiter" to fuelPricePerLiter.toDouble().coerceIn(0.0, 1000.0),
        "odometerKm" to odometerKm.toDouble().coerceIn(0.0, 5_000_000.0),
        "engineHours" to engineHours.toDouble().coerceIn(0.0, 200_000.0),
        "preferredPresetId" to preferredPresetId.trim().take(40).ifBlank { "main" },
        "curbWeightKg" to curbWeightKg.coerceIn(500, 15_000),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    vin?.trim()?.uppercase()?.take(32)?.takeIf { it.isNotEmpty() }?.let {
        map["vin"] = it
    }
    return map
}

@Keep
@IgnoreExtraProperties
data class CloudMaintenanceItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vehicleKey: String = "",
    val title: String = "",
    val category: String = "OIL",
    val intervalKm: Int = 10000,
    val intervalHours: Int = 0,
    val lastServiceOdometerKm: Double = 0.0,
    val lastServiceEngineHours: Double = 0.0,
    val lastServiceTimestampMs: Long = 0L,
    val notes: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toEntity(vehicleId: Long, existingId: Long = 0L): MaintenanceItemEntity {
        return MaintenanceItemEntity(
            id = existingId,
            vehicleId = vehicleId,
            vehicleKey = vehicleKey,
            title = title,
            category = category,
            intervalKm = intervalKm,
            intervalHours = intervalHours,
            lastServiceOdometerKm = lastServiceOdometerKm.toFloat(),
            lastServiceEngineHours = lastServiceEngineHours.toFloat(),
            lastServiceTimestampMs = lastServiceTimestampMs,
            notes = notes?.takeIf { it.isNotBlank() }
        )
    }
}

fun MaintenanceItemEntity.toCloudWriteMap(
    userId: String,
    resolvedVehicleKey: String,
    existingCreatedAt: Timestamp? = null
): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to userId,
        "vehicleKey" to resolvedVehicleKey.take(128).ifBlank { "default" },
        "title" to title.trim().take(100).ifBlank { "Service" },
        "category" to category.trim().take(40).ifBlank { "GENERAL" },
        "intervalKm" to intervalKm.coerceIn(0, 500_000),
        "intervalHours" to intervalHours.coerceIn(0, 20_000),
        "lastServiceOdometerKm" to lastServiceOdometerKm.toDouble().coerceIn(0.0, 5_000_000.0),
        "lastServiceEngineHours" to lastServiceEngineHours.toDouble().coerceIn(0.0, 200_000.0),
        "lastServiceTimestampMs" to lastServiceTimestampMs.coerceAtLeast(0L),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    notes?.trim()?.take(300)?.takeIf { it.isNotEmpty() }?.let {
        map["notes"] = it
    }
    return map
}

@Keep
@IgnoreExtraProperties
data class CloudCustomPid(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vehicleKey: String? = null,
    val name: String = "",
    val shortName: String = "",
    val modeAndPid: String = "",
    val header: String = "",
    val equation: String = "",
    val unit: String = "",
    val minValue: Double = 0.0,
    val maxValue: Double = 100.0,
    val decimals: Int = 1,
    val enabled: Boolean = true,
    val warningHigh: Double? = null,
    val criticalHigh: Double? = null,
    val warningLow: Double? = null,
    val criticalLow: Double? = null,
    val alertsEnabled: Boolean = true,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toEntity(existingId: Long = 0L, vehicleProfileId: Long? = null): CustomPidEntity {
        return CustomPidEntity(
            id = existingId,
            name = name,
            shortName = shortName.ifBlank { name.take(12) },
            modeAndPid = modeAndPid,
            header = header,
            equation = equation,
            unit = unit,
            minValue = minValue.toFloat(),
            maxValue = maxValue.toFloat(),
            decimals = decimals.coerceIn(0, 4),
            enabled = enabled,
            warningHigh = warningHigh?.toFloat(),
            criticalHigh = criticalHigh?.toFloat(),
            warningLow = warningLow?.toFloat(),
            criticalLow = criticalLow?.toFloat(),
            alertsEnabled = alertsEnabled,
            vehicleProfileId = vehicleProfileId
        )
    }
}

fun CustomPidEntity.toCloudWriteMap(
    userId: String,
    resolvedVehicleKey: String? = null,
    existingCreatedAt: Timestamp? = null
): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to userId,
        "name" to name.trim().take(80).ifBlank { "PID" },
        "shortName" to shortName.trim().take(30).ifBlank { name.trim().take(12).ifBlank { "PID" } },
        "modeAndPid" to modeAndPid.trim().take(16).ifBlank { "0100" },
        "header" to header.trim().take(16),
        "equation" to equation.trim().take(120).ifBlank { "A" },
        "unit" to unit.trim().take(20),
        "minValue" to minValue.toDouble(),
        "maxValue" to maxValue.toDouble(),
        "decimals" to decimals.coerceIn(0, 4),
        "enabled" to enabled,
        "alertsEnabled" to alertsEnabled,
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    resolvedVehicleKey?.trim()?.take(128)?.takeIf { it.isNotEmpty() }?.let {
        map["vehicleKey"] = it
    }
    warningHigh?.let { map["warningHigh"] = it.toDouble() }
    criticalHigh?.let { map["criticalHigh"] = it.toDouble() }
    warningLow?.let { map["warningLow"] = it.toDouble() }
    criticalLow?.let { map["criticalLow"] = it.toDouble() }
    return map
}

@Keep
@IgnoreExtraProperties
data class CloudTripSummary(
    @DocumentId val id: String = "",
    val userId: String = "",
    val vehicleName: String = "",
    val startedAtMs: Long = 0L,
    val endedAtMs: Long = 0L,
    val distanceKm: Double = 0.0,
    val durationSec: Long = 0L,
    val fuelUsedLiters: Double = 0.0,
    val avgLPer100Km: Double? = null,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val estimatedCost: Double = 0.0,
    val ecoScore: Int = 100,
    val peakHorsepower: Double = 0.0,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toEntity(existingId: Long = 0L): TripRecordEntity {
        return TripRecordEntity(
            id = existingId,
            vehicleName = vehicleName,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            distanceKm = distanceKm.toFloat(),
            durationSec = durationSec,
            fuelUsedLiters = fuelUsedLiters.toFloat(),
            avgLPer100Km = avgLPer100Km?.toFloat(),
            avgSpeedKmh = avgSpeedKmh.toFloat(),
            maxSpeedKmh = maxSpeedKmh.toFloat(),
            estimatedCost = estimatedCost.toFloat(),
            ecoScore = ecoScore.coerceIn(0, 100),
            peakHorsepower = peakHorsepower.toFloat().coerceIn(0f, 2000f)
        )
    }
}

fun TripRecordEntity.toCloudWriteMap(
    userId: String,
    existingCreatedAt: Timestamp? = null
): Map<String, Any> {
    val map = mutableMapOf<String, Any>(
        "userId" to userId,
        "vehicleName" to vehicleName.trim().take(80).ifBlank { "Auto" },
        "startedAtMs" to startedAtMs.coerceAtLeast(0L),
        "endedAtMs" to endedAtMs.coerceAtLeast(0L),
        "distanceKm" to distanceKm.toDouble().coerceIn(0.0, 50_000.0),
        "durationSec" to durationSec.coerceIn(0L, 8_640_000L),
        "fuelUsedLiters" to fuelUsedLiters.toDouble().coerceIn(0.0, 10_000.0),
        "avgSpeedKmh" to avgSpeedKmh.toDouble().coerceIn(0.0, 450.0),
        "maxSpeedKmh" to maxSpeedKmh.toDouble().coerceIn(0.0, 500.0),
        "estimatedCost" to estimatedCost.toDouble().coerceIn(0.0, 10_000_000.0),
        "ecoScore" to ecoScore.coerceIn(0, 100),
        "peakHorsepower" to peakHorsepower.toDouble().coerceIn(0.0, 2000.0),
        "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
    avgLPer100Km?.toDouble()?.takeIf { it in 0.0..100.0 }?.let {
        map["avgLPer100Km"] = it
    }
    return map
}
