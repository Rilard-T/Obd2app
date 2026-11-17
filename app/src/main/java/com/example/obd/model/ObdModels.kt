package com.example.obd.model

import kotlin.math.roundToInt

enum class SensorCategory(val displayName: String) {
    ENGINE("Двигатель"),
    TEMPERATURE("Температуры"),
    AIR_FUEL("Воздух и топливо"),
    POWER("Питание и электроника")
}

enum class AlertLevel {
    NORMAL,
    WARNING,
    CRITICAL
}

enum class PressureUnit(val label: String) {
    KPA("кПа"),
    BAR("бар"),
    PSI("PSI")
}

enum class ObdPid(
    val pidHex: String,
    val modeAndPid: String,
    val displayName: String,
    val shortName: String,
    val unit: String,
    val minRange: Float,
    val maxRange: Float,
    val warningThreshold: Float?,
    val criticalThreshold: Float?,
    val lowWarningThreshold: Float? = null,
    val lowCriticalThreshold: Float? = null,
    val decimals: Int = 0,
    val category: SensorCategory
) {
    ENGINE_RPM(
        pidHex = "0C",
        modeAndPid = "010C",
        displayName = "Обороты двигателя",
        shortName = "Обороты",
        unit = "об/мин",
        minRange = 0f,
        maxRange = 8000f,
        warningThreshold = 5800f,
        criticalThreshold = 6800f,
        decimals = 0,
        category = SensorCategory.ENGINE
    ),
    VEHICLE_SPEED(
        pidHex = "0D",
        modeAndPid = "010D",
        displayName = "Скорость автомобиля",
        shortName = "Скорость",
        unit = "км/ч",
        minRange = 0f,
        maxRange = 240f,
        warningThreshold = 130f,
        criticalThreshold = 170f,
        decimals = 0,
        category = SensorCategory.ENGINE
    ),
    COOLANT_TEMP(
        pidHex = "05",
        modeAndPid = "0105",
        displayName = "Температура ОЖ",
        shortName = "ОЖ ДВС",
        unit = "°C",
        minRange = -20f,
        maxRange = 130f,
        warningThreshold = 103f,
        criticalThreshold = 112f,
        decimals = 0,
        category = SensorCategory.TEMPERATURE
    ),
    CONTROL_MODULE_VOLTAGE(
        pidHex = "42",
        modeAndPid = "0142",
        displayName = "Напряжение бортсети",
        shortName = "Напряжение",
        unit = "В",
        minRange = 10.0f,
        maxRange = 16.0f,
        warningThreshold = 15.0f,
        criticalThreshold = 15.5f,
        lowWarningThreshold = 12.1f,
        lowCriticalThreshold = 11.5f,
        decimals = 1,
        category = SensorCategory.POWER
    ),
    ENGINE_LOAD(
        pidHex = "04",
        modeAndPid = "0104",
        displayName = "Нагрузка двигателя",
        shortName = "Нагрузка",
        unit = "%",
        minRange = 0f,
        maxRange = 100f,
        warningThreshold = 88f,
        criticalThreshold = 96f,
        decimals = 0,
        category = SensorCategory.ENGINE
    ),
    THROTTLE_POS(
        pidHex = "11",
        modeAndPid = "0111",
        displayName = "Дроссельная заслонка",
        shortName = "Дроссель",
        unit = "%",
        minRange = 0f,
        maxRange = 100f,
        warningThreshold = null,
        criticalThreshold = null,
        decimals = 0,
        category = SensorCategory.AIR_FUEL
    ),
    INTAKE_AIR_TEMP(
        pidHex = "0F",
        modeAndPid = "010F",
        displayName = "Температура впуска",
        shortName = "Впуск",
        unit = "°C",
        minRange = -20f,
        maxRange = 90f,
        warningThreshold = 55f,
        criticalThreshold = 70f,
        decimals = 0,
        category = SensorCategory.TEMPERATURE
    ),
    INTAKE_MAP(
        pidHex = "0B",
        modeAndPid = "010B",
        displayName = "Давление во впуске (ДАД)",
        shortName = "Давление",
        unit = "кПа",
        minRange = 10f,
        maxRange = 255f,
        warningThreshold = 210f,
        criticalThreshold = 235f,
        decimals = 0,
        category = SensorCategory.AIR_FUEL
    ),
    MAF_FLOW(
        pidHex = "10",
        modeAndPid = "0110",
        displayName = "Расход воздуха (ДМРВ)",
        shortName = "ДМРВ",
        unit = "г/с",
        minRange = 0f,
        maxRange = 220f,
        warningThreshold = null,
        criticalThreshold = null,
        decimals = 1,
        category = SensorCategory.AIR_FUEL
    ),
    FUEL_LEVEL(
        pidHex = "2F",
        modeAndPid = "012F",
        displayName = "Уровень топлива",
        shortName = "Топливо",
        unit = "%",
        minRange = 0f,
        maxRange = 100f,
        warningThreshold = null,
        criticalThreshold = null,
        lowWarningThreshold = 15f,
        lowCriticalThreshold = 8f,
        decimals = 0,
        category = SensorCategory.AIR_FUEL
    ),
    OIL_TEMP(
        pidHex = "5C",
        modeAndPid = "015C",
        displayName = "Температура масла",
        shortName = "Масло",
        unit = "°C",
        minRange = -20f,
        maxRange = 150f,
        warningThreshold = 118f,
        criticalThreshold = 130f,
        decimals = 0,
        category = SensorCategory.TEMPERATURE
    ),
    TIMING_ADVANCE(
        pidHex = "0E",
        modeAndPid = "010E",
        displayName = "Угол опережения зажигания",
        shortName = "УОЗ",
        unit = "°",
        minRange = -20f,
        maxRange = 60f,
        warningThreshold = null,
        criticalThreshold = null,
        decimals = 1,
        category = SensorCategory.ENGINE
    ),
    SHORT_FUEL_TRIM(
        pidHex = "06",
        modeAndPid = "0106",
        displayName = "Кратк. коррекция топлива",
        shortName = "STFT B1",
        unit = "%",
        minRange = -30f,
        maxRange = 30f,
        warningThreshold = 15f,
        criticalThreshold = 25f,
        lowWarningThreshold = -15f,
        lowCriticalThreshold = -25f,
        decimals = 1,
        category = SensorCategory.AIR_FUEL
    ),
    LONG_FUEL_TRIM(
        pidHex = "07",
        modeAndPid = "0107",
        displayName = "Долг. коррекция топлива",
        shortName = "LTFT B1",
        unit = "%",
        minRange = -30f,
        maxRange = 30f,
        warningThreshold = 12f,
        criticalThreshold = 20f,
        lowWarningThreshold = -12f,
        lowCriticalThreshold = -20f,
        decimals = 1,
        category = SensorCategory.AIR_FUEL
    );

    fun evaluateAlert(value: Float): AlertLevel {
        if (criticalThreshold != null && value >= criticalThreshold) return AlertLevel.CRITICAL
        if (lowCriticalThreshold != null && value <= lowCriticalThreshold) return AlertLevel.CRITICAL
        if (warningThreshold != null && value >= warningThreshold) return AlertLevel.WARNING
        if (lowWarningThreshold != null && value <= lowWarningThreshold) return AlertLevel.WARNING
        return AlertLevel.NORMAL
    }

    fun formatValue(
        rawValue: Float,
        useFahrenheit: Boolean = false,
        pressureUnit: PressureUnit = PressureUnit.KPA
    ): String {
        val converted = convertValue(rawValue, useFahrenheit, pressureUnit)
        return when {
            this == ENGINE_RPM -> "${converted.roundToInt()}"
            decimals == 0 -> "${converted.roundToInt()}"
            this == INTAKE_MAP && pressureUnit == PressureUnit.BAR -> String.format("%.2f", converted)
            else -> String.format("%.${decimals}f", converted)
        }
    }

    fun displayUnit(
        useFahrenheit: Boolean = false,
        pressureUnit: PressureUnit = PressureUnit.KPA
    ): String {
        return when {
            unit == "°C" && useFahrenheit -> "°F"
            this == INTAKE_MAP -> pressureUnit.label
            else -> unit
        }
    }

    fun convertValue(
        rawValue: Float,
        useFahrenheit: Boolean = false,
        pressureUnit: PressureUnit = PressureUnit.KPA
    ): Float {
        return when {
            unit == "°C" && useFahrenheit -> rawValue * 9f / 5f + 32f
            this == INTAKE_MAP -> when (pressureUnit) {
                PressureUnit.KPA -> rawValue
                PressureUnit.BAR -> rawValue / 100f
                PressureUnit.PSI -> rawValue * 0.145038f
            }
            else -> rawValue
        }
    }

    companion object {
        val defaultAndroidAutoPids: List<ObdPid> = listOf(
            ENGINE_RPM,
            VEHICLE_SPEED,
            COOLANT_TEMP,
            CONTROL_MODULE_VOLTAGE,
            ENGINE_LOAD,
            INTAKE_MAP
        )

        fun fromModeAndPid(code: String): ObdPid? = entries.find { it.modeAndPid == code }
    }
}

data class SensorReading(
    val pid: ObdPid,
    val value: Float,
    val minValue: Float = value,
    val maxValue: Float = value,
    val history: List<Float> = listOf(value),
    val alertLevel: AlertLevel = pid.evaluateAlert(value),
    val timestampMs: Long = System.currentTimeMillis()
)

enum class BluetoothTransportType(val label: String) {
    CLASSIC_SPP("Bluetooth Classic (SPP)"),
    BLE("Bluetooth LE (GATT)")
}

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val transport: BluetoothTransportType,
    val isPaired: Boolean,
    val isLikelyObd: Boolean,
    val rssi: Int? = null
)

sealed interface ObdConnectionState {
    data object Disconnected : ObdConnectionState
    data class Connecting(val deviceName: String, val step: String) : ObdConnectionState
    data class Connected(
        val deviceName: String,
        val address: String,
        val protocol: String,
        val transport: BluetoothTransportType,
        val pollingHz: Float = 0f
    ) : ObdConnectionState
    data class Simulation(val pollingHz: Float = 2.5f) : ObdConnectionState
    data class Error(val message: String, val deviceName: String? = null) : ObdConnectionState

    val isActiveTelemetry: Boolean
        get() = this is Connected || this is Simulation
}

enum class DtcSeverity(val label: String) {
    INFO("Предупреждение"),
    MODERATE("Требует внимания"),
    CRITICAL("Критическая")
}

data class DtcCode(
    val code: String,
    val system: String,
    val description: String,
    val severity: DtcSeverity
)

data class DtcState(
    val milOn: Boolean = false,
    val ecuReportedCount: Int = 0,
    val codes: List<DtcCode> = emptyList(),
    val isScanning: Boolean = false,
    val lastScanTimestampMs: Long? = null,
    val statusMessage: String? = null
)
