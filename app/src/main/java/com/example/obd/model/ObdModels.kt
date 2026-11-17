package com.example.obd.model

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.roundToInt

enum class AppLanguage {
    SYSTEM,
    RU,
    EN
}

enum class SensorCategory(val displayName: String, val displayNameEn: String) {
    ENGINE("Двигатель", "Engine"),
    TEMPERATURE("Температуры", "Temperatures"),
    AIR_FUEL("Воздух и топливо", "Air & Fuel"),
    POWER("Питание и электроника", "Power & Electronics"),
    CUSTOM("Свои PID", "Custom PIDs");

    fun localizedName(isEnglish: Boolean): String = if (isEnglish) displayNameEn else displayName
}

enum class AlertLevel {
    NORMAL,
    WARNING,
    CRITICAL
}

enum class PressureUnit(val label: String, val labelEn: String) {
    KPA("кПа", "kPa"),
    BAR("бар", "bar"),
    PSI("PSI", "PSI");

    fun localizedLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else label
}

data class TelemetrySensor(
    val id: String,
    val modeAndPid: String,
    val header: String = "",
    val displayName: String,
    val shortName: String,
    val unit: String,
    val minRange: Float,
    val maxRange: Float,
    val decimals: Int = 1,
    val category: SensorCategory,
    val isCustom: Boolean = false,
    val customId: Long? = null,
    val equation: String? = null,
    val builtInPid: ObdPid? = null,
    val warningThreshold: Float? = null,
    val criticalThreshold: Float? = null,
    val lowWarningThreshold: Float? = null,
    val lowCriticalThreshold: Float? = null,
    val alertsEnabled: Boolean = true
) {
    fun localizedDisplayName(isEnglish: Boolean): String {
        return builtInPid?.localizedDisplayName(isEnglish) ?: displayName
    }

    fun localizedShortName(isEnglish: Boolean): String {
        return builtInPid?.localizedShortName(isEnglish) ?: shortName
    }

    fun evaluateAlert(value: Float): AlertLevel {
        if (!alertsEnabled) return AlertLevel.NORMAL
        if (criticalThreshold != null && value >= criticalThreshold) return AlertLevel.CRITICAL
        if (lowCriticalThreshold != null && value <= lowCriticalThreshold) return AlertLevel.CRITICAL
        if (warningThreshold != null && value >= warningThreshold) return AlertLevel.WARNING
        if (lowWarningThreshold != null && value <= lowWarningThreshold) return AlertLevel.WARNING
        return AlertLevel.NORMAL
    }

    fun formatValue(
        rawValue: Float,
        useFahrenheit: Boolean = false,
        pressureUnit: PressureUnit = PressureUnit.KPA,
        isEnglish: Boolean = false
    ): String {
        if (builtInPid != null) {
            return builtInPid.formatValue(rawValue, useFahrenheit, pressureUnit, isEnglish)
        }
        val d = decimals.coerceIn(0, 3)
        val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
        return if (d == 0) "${rawValue.roundToInt()}" else String.format(locale, "%.${d}f", rawValue)
    }

    fun displayUnit(
        useFahrenheit: Boolean = false,
        pressureUnit: PressureUnit = PressureUnit.KPA,
        isEnglish: Boolean = false
    ): String {
        return builtInPid?.displayUnit(useFahrenheit, pressureUnit, isEnglish) ?: unit
    }
}

enum class ObdPid(
    val pidHex: String,
    val modeAndPid: String,
    val displayName: String,
    val displayNameEn: String,
    val shortName: String,
    val shortNameEn: String,
    val unit: String,
    val unitEn: String,
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
        displayNameEn = "Engine RPM",
        shortName = "Обороты",
        shortNameEn = "RPM",
        unit = "об/мин",
        unitEn = "RPM",
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
        displayNameEn = "Vehicle Speed",
        shortName = "Скорость",
        shortNameEn = "Speed",
        unit = "км/ч",
        unitEn = "km/h",
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
        displayNameEn = "Coolant Temp",
        shortName = "ОЖ ДВС",
        shortNameEn = "Coolant",
        unit = "°C",
        unitEn = "°C",
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
        displayNameEn = "Control Module Voltage",
        shortName = "Напряжение",
        shortNameEn = "Voltage",
        unit = "В",
        unitEn = "V",
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
        displayNameEn = "Engine Load",
        shortName = "Нагрузка",
        shortNameEn = "Load",
        unit = "%",
        unitEn = "%",
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
        displayNameEn = "Throttle Position",
        shortName = "Дроссель",
        shortNameEn = "Throttle",
        unit = "%",
        unitEn = "%",
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
        displayNameEn = "Intake Air Temp",
        shortName = "Впуск",
        shortNameEn = "Intake",
        unit = "°C",
        unitEn = "°C",
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
        displayNameEn = "Intake MAP Pressure",
        shortName = "Давление",
        shortNameEn = "MAP",
        unit = "кПа",
        unitEn = "kPa",
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
        displayNameEn = "MAF Air Flow",
        shortName = "ДМРВ",
        shortNameEn = "MAF",
        unit = "г/с",
        unitEn = "g/s",
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
        displayNameEn = "Fuel Level",
        shortName = "Топливо",
        shortNameEn = "Fuel",
        unit = "%",
        unitEn = "%",
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
        displayNameEn = "Engine Oil Temp",
        shortName = "Масло",
        shortNameEn = "Oil",
        unit = "°C",
        unitEn = "°C",
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
        displayNameEn = "Timing Advance",
        shortName = "УОЗ",
        shortNameEn = "Timing",
        unit = "°",
        unitEn = "°",
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
        displayNameEn = "Short Term Fuel Trim",
        shortName = "STFT B1",
        shortNameEn = "STFT B1",
        unit = "%",
        unitEn = "%",
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
        displayNameEn = "Long Term Fuel Trim",
        shortName = "LTFT B1",
        shortNameEn = "LTFT B1",
        unit = "%",
        unitEn = "%",
        minRange = -30f,
        maxRange = 30f,
        warningThreshold = 12f,
        criticalThreshold = 20f,
        lowWarningThreshold = -12f,
        lowCriticalThreshold = -20f,
        decimals = 1,
        category = SensorCategory.AIR_FUEL
    );

    fun localizedDisplayName(isEnglish: Boolean): String = if (isEnglish) displayNameEn else displayName
    fun localizedShortName(isEnglish: Boolean): String = if (isEnglish) shortNameEn else shortName
    fun localizedUnit(isEnglish: Boolean): String = if (isEnglish) unitEn else unit

    fun toTelemetrySensor(isEnglish: Boolean = false): TelemetrySensor = TelemetrySensor(
        id = modeAndPid,
        modeAndPid = modeAndPid,
        header = "",
        displayName = localizedDisplayName(isEnglish),
        shortName = localizedShortName(isEnglish),
        unit = localizedUnit(isEnglish),
        minRange = minRange,
        maxRange = maxRange,
        decimals = decimals,
        category = category,
        isCustom = false,
        customId = null,
        equation = null,
        builtInPid = this,
        warningThreshold = warningThreshold,
        criticalThreshold = criticalThreshold,
        lowWarningThreshold = lowWarningThreshold,
        lowCriticalThreshold = lowCriticalThreshold,
        alertsEnabled = true
    )

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
        pressureUnit: PressureUnit = PressureUnit.KPA,
        isEnglish: Boolean = false
    ): String {
        val converted = convertValue(rawValue, useFahrenheit, pressureUnit)
        val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
        return when {
            this == ENGINE_RPM -> "${converted.roundToInt()}"
            decimals == 0 -> "${converted.roundToInt()}"
            this == INTAKE_MAP && pressureUnit == PressureUnit.BAR -> String.format(locale, "%.2f", converted)
            else -> String.format(locale, "%.${decimals}f", converted)
        }
    }

    fun displayUnit(
        useFahrenheit: Boolean = false,
        pressureUnit: PressureUnit = PressureUnit.KPA,
        isEnglish: Boolean = false
    ): String {
        return when {
            unit == "°C" && useFahrenheit -> "°F"
            this == INTAKE_MAP -> pressureUnit.localizedLabel(isEnglish)
            else -> localizedUnit(isEnglish)
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

        val defaultAndroidAutoSensorIds: List<String> = defaultAndroidAutoPids.map { it.modeAndPid }

        fun fromModeAndPid(code: String): ObdPid? = entries.find { it.modeAndPid == code }
    }
}

data class SensorReading(
    val sensorId: String,
    val sensor: TelemetrySensor,
    val value: Float,
    val minValue: Float = value,
    val maxValue: Float = value,
    val history: List<Float> = listOf(value),
    val alertLevel: AlertLevel = sensor.evaluateAlert(value),
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

enum class DtcSeverity(val label: String, val labelEn: String) {
    INFO("Предупреждение", "Warning"),
    MODERATE("Требует внимания", "Attention"),
    CRITICAL("Критическая", "Critical");

    fun localizedLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else label
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

@Serializable
data class CockpitPreset(
    val id: String,
    val name: String,
    val dialSensorIds: List<String>,
    val stripSensorIds: List<String>
) {
    fun localizedName(isEnglish: Boolean): String {
        return when (id) {
            "main" -> if (name == "Основной" || name == "Main") {
                if (isEnglish) "Main" else "Основной"
            } else name
            "diesel_trip" -> if (name == "Трасса" || name == "Highway") {
                if (isEnglish) "Highway" else "Трасса"
            } else name
            "diag" -> if (name == "Диагностика" || name == "Diagnostics") {
                if (isEnglish) "Diagnostics" else "Диагностика"
            } else name
            else -> name
        }
    }
}

@Serializable
data class SensorAlertOverride(
    val sensorId: String,
    val alertsEnabled: Boolean = true,
    val warningHigh: Float? = null,
    val criticalHigh: Float? = null,
    val warningLow: Float? = null,
    val criticalLow: Float? = null
)

data class LivePidProbeResult(
    val isSuccess: Boolean,
    val modeAndPid: String,
    val rawResponse: String,
    val extractedBytes: List<Int>,
    val evaluatedValue: Float?,
    val latencyMs: Long,
    val errorMessage: String? = null
)

data class Elm327BenchmarkResult(
    val adapterVersion: String,
    val protocolName: String,
    val avgLatencyMs: Long,
    val minLatencyMs: Long,
    val maxLatencyMs: Long,
    val maxPollingHz: Float,
    val supportsAdaptiveTiming: Boolean,
    val supportsTimeoutCmd: Boolean,
    val supportedMode01Count: Int?,
    val qualityLabel: String,
    val summary: String,
    val isSimulated: Boolean = false
)

data class ObdAlertEvent(
    val id: Int,
    val sensorId: String,
    val sensorName: String,
    val level: AlertLevel,
    val formattedValue: String,
    val unit: String,
    val message: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class TtsVoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val isNetworkRequired: Boolean
)
