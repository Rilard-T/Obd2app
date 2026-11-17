package com.example.obd.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.obd.i18n.AppLocalization
import com.example.obd.model.AppLanguage
import com.example.obd.model.BluetoothTransportType
import com.example.obd.model.CockpitPreset
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorAlertOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.obdSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "obd_settings"
)

enum class AndroidAutoDisplayMode(val label: String, val labelEn: String) {
    FULLSCREEN_COCKPIT("Полный экран (Приборы)", "Full Screen (Gauges)"),
    STANDARD_GRID("Сетка (Плитки)", "Grid (Tiles)");

    fun localizedLabel(isEnglish: Boolean): String = if (isEnglish) labelEn else label
}

data class ObdUserSettings(
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val androidAutoSensorIds: List<String> = ObdPid.defaultAndroidAutoSensorIds,
    val androidAutoDisplayMode: AndroidAutoDisplayMode = AndroidAutoDisplayMode.FULLSCREEN_COCKPIT,
    val fullscreenDialSensorIds: List<String> = DEFAULT_FULLSCREEN_DIAL_IDS,
    val fullscreenStripSensorIds: List<String> = DEFAULT_FULLSCREEN_STRIP_IDS,
    val cockpitPresets: List<CockpitPreset> = DEFAULT_PRESETS,
    val activePresetId: String = DEFAULT_PRESET_ID,
    val autoConnectOnLaunch: Boolean = true,
    val voiceAlertsEnabled: Boolean = true,
    val aaPopupAlertsEnabled: Boolean = true,
    val ttsVolume: Float = 1.0f,
    val ttsSpeechRate: Float = 1.0f,
    val ttsVoiceName: String? = null,
    val alertOverrides: Map<String, SensorAlertOverride> = emptyMap(),
    val pollingIntervalMs: Long = 350L,
    val useFahrenheit: Boolean = false,
    val pressureUnit: PressureUnit = PressureUnit.KPA,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val lastTransport: BluetoothTransportType = BluetoothTransportType.CLASSIC_SPP,
    val hasRequestedStartupPermissions: Boolean = false
) {
    val activePreset: CockpitPreset
        get() = cockpitPresets.find { it.id == activePresetId } ?: cockpitPresets.first()

    val isEnglish: Boolean
        get() = AppLocalization.isEnglish(appLanguage)

    companion object {
        const val DEFAULT_PRESET_ID = "main"

        val DEFAULT_FULLSCREEN_DIAL_IDS: List<String> = listOf(
            ObdPid.ENGINE_RPM.modeAndPid,
            ObdPid.VEHICLE_SPEED.modeAndPid,
            ObdPid.COOLANT_TEMP.modeAndPid
        )

        val DEFAULT_FULLSCREEN_STRIP_IDS: List<String> = listOf(
            ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid,
            ObdPid.ENGINE_LOAD.modeAndPid,
            ObdPid.OIL_TEMP.modeAndPid,
            ObdPid.INTAKE_MAP.modeAndPid
        )

        val DEFAULT_PRESETS: List<CockpitPreset> = listOf(
            CockpitPreset(
                id = "main",
                name = "Основной",
                dialSensorIds = DEFAULT_FULLSCREEN_DIAL_IDS,
                stripSensorIds = DEFAULT_FULLSCREEN_STRIP_IDS
            ),
            CockpitPreset(
                id = "diesel_trip",
                name = "Трасса",
                dialSensorIds = listOf(
                    ObdPid.ENGINE_RPM.modeAndPid,
                    ObdPid.VEHICLE_SPEED.modeAndPid,
                    ObdPid.INTAKE_MAP.modeAndPid
                ),
                stripSensorIds = listOf(
                    ObdPid.COOLANT_TEMP.modeAndPid,
                    ObdPid.OIL_TEMP.modeAndPid,
                    ObdPid.FUEL_LEVEL.modeAndPid,
                    ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid
                )
            ),
            CockpitPreset(
                id = "diag",
                name = "Диагностика",
                dialSensorIds = listOf(
                    ObdPid.ENGINE_RPM.modeAndPid,
                    ObdPid.ENGINE_LOAD.modeAndPid,
                    ObdPid.TIMING_ADVANCE.modeAndPid
                ),
                stripSensorIds = listOf(
                    ObdPid.SHORT_FUEL_TRIM.modeAndPid,
                    ObdPid.LONG_FUEL_TRIM.modeAndPid,
                    ObdPid.THROTTLE_POS.modeAndPid,
                    ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid
                )
            )
        )
    }
}

class SettingsDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val ANDROID_AUTO_PIDS = stringPreferencesKey("android_auto_pids")
        val ANDROID_AUTO_DISPLAY_MODE = stringPreferencesKey("android_auto_display_mode")
        val FULLSCREEN_DIAL_PIDS = stringPreferencesKey("fullscreen_dial_pids")
        val FULLSCREEN_STRIP_PIDS = stringPreferencesKey("fullscreen_strip_pids")
        val COCKPIT_PRESETS_JSON = stringPreferencesKey("cockpit_presets_json")
        val ACTIVE_PRESET_ID = stringPreferencesKey("active_preset_id")
        val AUTO_CONNECT_ON_LAUNCH = booleanPreferencesKey("auto_connect_on_launch")
        val VOICE_ALERTS_ENABLED = booleanPreferencesKey("voice_alerts_enabled")
        val AA_POPUP_ALERTS_ENABLED = booleanPreferencesKey("aa_popup_alerts_enabled")
        val TTS_VOLUME = floatPreferencesKey("tts_volume")
        val TTS_SPEECH_RATE = floatPreferencesKey("tts_speech_rate")
        val TTS_VOICE_NAME = stringPreferencesKey("tts_voice_name")
        val ALERT_OVERRIDES_JSON = stringPreferencesKey("alert_overrides_json")
        val POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val LAST_TRANSPORT = stringPreferencesKey("last_transport")
        val HAS_REQUESTED_STARTUP_PERMS = booleanPreferencesKey("has_requested_startup_permissions")
    }

    val settingsFlow: Flow<ObdUserSettings> = context.obdSettingsDataStore.data.map { prefs ->
        val appLanguage = prefs[Keys.APP_LANGUAGE]?.let { name ->
            AppLanguage.entries.find { it.name == name }
        } ?: AppLanguage.SYSTEM

        val rawPids = prefs[Keys.ANDROID_AUTO_PIDS]
        val parsedIds = parseSensorIds(rawPids, maxCount = 6)
            .takeIf { it.isNotEmpty() }
            ?: ObdPid.defaultAndroidAutoSensorIds

        val displayMode = prefs[Keys.ANDROID_AUTO_DISPLAY_MODE]?.let { name ->
            AndroidAutoDisplayMode.entries.find { it.name == name }
        } ?: AndroidAutoDisplayMode.FULLSCREEN_COCKPIT

        val presets = loadPresets(
            rawPresetsJson = prefs[Keys.COCKPIT_PRESETS_JSON],
            legacyDialRaw = prefs[Keys.FULLSCREEN_DIAL_PIDS],
            legacyStripRaw = prefs[Keys.FULLSCREEN_STRIP_PIDS]
        )
        val requestedPresetId = prefs[Keys.ACTIVE_PRESET_ID] ?: ObdUserSettings.DEFAULT_PRESET_ID
        val activePreset = presets.find { it.id == requestedPresetId } ?: presets.first()

        val alertOverrides = loadAlertOverrides(prefs[Keys.ALERT_OVERRIDES_JSON])

        val pressureUnit = prefs[Keys.PRESSURE_UNIT]?.let { name ->
            PressureUnit.entries.find { it.name == name }
        } ?: PressureUnit.KPA

        val transport = prefs[Keys.LAST_TRANSPORT]?.let { name ->
            BluetoothTransportType.entries.find { it.name == name }
        } ?: BluetoothTransportType.CLASSIC_SPP

        ObdUserSettings(
            appLanguage = appLanguage,
            androidAutoSensorIds = parsedIds,
            androidAutoDisplayMode = displayMode,
            fullscreenDialSensorIds = activePreset.dialSensorIds,
            fullscreenStripSensorIds = activePreset.stripSensorIds,
            cockpitPresets = presets,
            activePresetId = activePreset.id,
            autoConnectOnLaunch = prefs[Keys.AUTO_CONNECT_ON_LAUNCH] ?: true,
            voiceAlertsEnabled = prefs[Keys.VOICE_ALERTS_ENABLED] ?: true,
            aaPopupAlertsEnabled = prefs[Keys.AA_POPUP_ALERTS_ENABLED] ?: true,
            ttsVolume = (prefs[Keys.TTS_VOLUME] ?: 1.0f).coerceIn(0.1f, 1.0f),
            ttsSpeechRate = (prefs[Keys.TTS_SPEECH_RATE] ?: 1.0f).coerceIn(0.6f, 1.6f),
            ttsVoiceName = prefs[Keys.TTS_VOICE_NAME],
            alertOverrides = alertOverrides,
            pollingIntervalMs = prefs[Keys.POLLING_INTERVAL_MS] ?: 350L,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            pressureUnit = pressureUnit,
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS],
            lastDeviceName = prefs[Keys.LAST_DEVICE_NAME],
            lastTransport = transport,
            hasRequestedStartupPermissions = prefs[Keys.HAS_REQUESTED_STARTUP_PERMS] ?: false
        )
    }

    private fun loadPresets(
        rawPresetsJson: String?,
        legacyDialRaw: String?,
        legacyStripRaw: String?
    ): List<CockpitPreset> {
        if (!rawPresetsJson.isNullOrBlank()) {
            val decoded = runCatching {
                json.decodeFromString<List<CockpitPreset>>(rawPresetsJson)
            }.getOrNull()
            if (!decoded.isNullOrEmpty()) {
                return decoded.map { p ->
                    p.copy(
                        dialSensorIds = normalizeFixedList(p.dialSensorIds, ObdUserSettings.DEFAULT_FULLSCREEN_DIAL_IDS),
                        stripSensorIds = normalizeFixedList(p.stripSensorIds, ObdUserSettings.DEFAULT_FULLSCREEN_STRIP_IDS)
                    )
                }
            }
        }
        val legacyDials = parseFixedSlots(legacyDialRaw, ObdUserSettings.DEFAULT_FULLSCREEN_DIAL_IDS)
        val legacyStrips = parseFixedSlots(legacyStripRaw, ObdUserSettings.DEFAULT_FULLSCREEN_STRIP_IDS)
        return ObdUserSettings.DEFAULT_PRESETS.map { preset ->
            if (preset.id == ObdUserSettings.DEFAULT_PRESET_ID) {
                preset.copy(dialSensorIds = legacyDials, stripSensorIds = legacyStrips)
            } else {
                preset
            }
        }
    }

    private fun loadAlertOverrides(raw: String?): Map<String, SensorAlertOverride> {
        if (raw.isNullOrBlank()) return emptyMap()
        val list = runCatching {
            json.decodeFromString<List<SensorAlertOverride>>(raw)
        }.getOrDefault(emptyList())
        return list.associateBy { it.sensorId }
    }

    private fun normalizeFixedList(items: List<String>, defaults: List<String>): List<String> {
        return defaults.mapIndexed { idx, def ->
            items.getOrNull(idx)?.let(::parseToken) ?: def
        }
    }

    private fun parseToken(token: String): String? {
        val trimmed = token.trim()
        return when {
            trimmed.startsWith("CUSTOM_") -> trimmed
            ObdPid.fromModeAndPid(trimmed) != null -> trimmed
            else -> ObdPid.entries.find { it.name == trimmed }?.modeAndPid
        }
    }

    private fun parseSensorIds(raw: String?, maxCount: Int = 6): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",")
            .mapNotNull(::parseToken)
            .distinct()
            .take(maxCount)
    }

    private fun parseFixedSlots(raw: String?, defaults: List<String>): List<String> {
        if (raw.isNullOrBlank()) return defaults
        val parsed = raw.split(",").mapNotNull(::parseToken)
        return defaults.mapIndexed { idx, defaultId ->
            parsed.getOrNull(idx) ?: defaultId
        }
    }

    suspend fun toggleAndroidAutoSensor(sensorId: String) {
        context.obdSettingsDataStore.edit { prefs ->
            val current = parseSensorIds(prefs[Keys.ANDROID_AUTO_PIDS], maxCount = 6)
                .ifEmpty { ObdPid.defaultAndroidAutoSensorIds }
                .toMutableList()

            if (sensorId in current) {
                if (current.size > 1) {
                    current.remove(sensorId)
                }
            } else {
                if (current.size >= 6) {
                    current.removeAt(current.lastIndex)
                }
                current.add(sensorId)
            }
            prefs[Keys.ANDROID_AUTO_PIDS] = current.joinToString(",")
        }
    }

    suspend fun setAndroidAutoDisplayMode(mode: AndroidAutoDisplayMode) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.ANDROID_AUTO_DISPLAY_MODE] = mode.name
        }
    }

    suspend fun selectCockpitPreset(presetId: String) {
        context.obdSettingsDataStore.edit { prefs ->
            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            )
            val target = presets.find { it.id == presetId } ?: presets.first()
            prefs[Keys.ACTIVE_PRESET_ID] = target.id
            prefs[Keys.FULLSCREEN_DIAL_PIDS] = target.dialSensorIds.joinToString(",")
            prefs[Keys.FULLSCREEN_STRIP_PIDS] = target.stripSensorIds.joinToString(",")
        }
    }

    suspend fun cycleNextCockpitPreset(): CockpitPreset {
        var chosen: CockpitPreset = ObdUserSettings.DEFAULT_PRESETS.first()
        context.obdSettingsDataStore.edit { prefs ->
            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            )
            val currentId = prefs[Keys.ACTIVE_PRESET_ID] ?: ObdUserSettings.DEFAULT_PRESET_ID
            val currentIdx = presets.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            chosen = presets[(currentIdx + 1) % presets.size]
            prefs[Keys.ACTIVE_PRESET_ID] = chosen.id
            prefs[Keys.FULLSCREEN_DIAL_PIDS] = chosen.dialSensorIds.joinToString(",")
            prefs[Keys.FULLSCREEN_STRIP_PIDS] = chosen.stripSensorIds.joinToString(",")
        }
        return chosen
    }

    suspend fun renameCockpitPreset(presetId: String, newName: String) {
        val clean = newName.trim().take(18)
        if (clean.isEmpty()) return
        context.obdSettingsDataStore.edit { prefs ->
            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            ).map { if (it.id == presetId) it.copy(name = clean) else it }
            prefs[Keys.COCKPIT_PRESETS_JSON] = json.encodeToString(presets)
        }
    }

    suspend fun setFullscreenDialSlot(slotIndex: Int, sensorId: String) {
        if (slotIndex !in ObdUserSettings.DEFAULT_FULLSCREEN_DIAL_IDS.indices) return
        context.obdSettingsDataStore.edit { prefs ->
            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            )
            val activeId = prefs[Keys.ACTIVE_PRESET_ID] ?: ObdUserSettings.DEFAULT_PRESET_ID
            val updated = presets.map { preset ->
                if (preset.id == activeId) {
                    val dials = preset.dialSensorIds.toMutableList()
                    dials[slotIndex] = sensorId
                    prefs[Keys.FULLSCREEN_DIAL_PIDS] = dials.joinToString(",")
                    preset.copy(dialSensorIds = dials)
                } else preset
            }
            prefs[Keys.COCKPIT_PRESETS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun setFullscreenStripSlot(slotIndex: Int, sensorId: String) {
        if (slotIndex !in ObdUserSettings.DEFAULT_FULLSCREEN_STRIP_IDS.indices) return
        context.obdSettingsDataStore.edit { prefs ->
            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            )
            val activeId = prefs[Keys.ACTIVE_PRESET_ID] ?: ObdUserSettings.DEFAULT_PRESET_ID
            val updated = presets.map { preset ->
                if (preset.id == activeId) {
                    val strips = preset.stripSensorIds.toMutableList()
                    strips[slotIndex] = sensorId
                    prefs[Keys.FULLSCREEN_STRIP_PIDS] = strips.joinToString(",")
                    preset.copy(stripSensorIds = strips)
                } else preset
            }
            prefs[Keys.COCKPIT_PRESETS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun resetFullscreenLayoutDefaults() {
        context.obdSettingsDataStore.edit { prefs ->
            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            )
            val activeId = prefs[Keys.ACTIVE_PRESET_ID] ?: ObdUserSettings.DEFAULT_PRESET_ID
            val defaultTemplate = ObdUserSettings.DEFAULT_PRESETS.find { it.id == activeId }
                ?: ObdUserSettings.DEFAULT_PRESETS.first()
            val updated = presets.map { p ->
                if (p.id == activeId) {
                    prefs[Keys.FULLSCREEN_DIAL_PIDS] = defaultTemplate.dialSensorIds.joinToString(",")
                    prefs[Keys.FULLSCREEN_STRIP_PIDS] = defaultTemplate.stripSensorIds.joinToString(",")
                    defaultTemplate
                } else p
            }
            prefs[Keys.COCKPIT_PRESETS_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun removeAndroidAutoSensor(sensorId: String) {
        context.obdSettingsDataStore.edit { prefs ->
            val current = parseSensorIds(prefs[Keys.ANDROID_AUTO_PIDS], maxCount = 6).toMutableList()
            if (current.remove(sensorId)) {
                val next = current.ifEmpty { ObdPid.defaultAndroidAutoSensorIds }
                prefs[Keys.ANDROID_AUTO_PIDS] = next.joinToString(",")
            }

            val presets = loadPresets(
                prefs[Keys.COCKPIT_PRESETS_JSON],
                prefs[Keys.FULLSCREEN_DIAL_PIDS],
                prefs[Keys.FULLSCREEN_STRIP_PIDS]
            ).map { preset ->
                val defaultPreset = ObdUserSettings.DEFAULT_PRESETS.find { it.id == preset.id }
                    ?: ObdUserSettings.DEFAULT_PRESETS.first()
                val dials = preset.dialSensorIds.mapIndexed { idx, id ->
                    if (id == sensorId) defaultPreset.dialSensorIds[idx] else id
                }
                val strips = preset.stripSensorIds.mapIndexed { idx, id ->
                    if (id == sensorId) defaultPreset.stripSensorIds[idx] else id
                }
                preset.copy(dialSensorIds = dials, stripSensorIds = strips)
            }
            prefs[Keys.COCKPIT_PRESETS_JSON] = json.encodeToString(presets)

            val overrides = loadAlertOverrides(prefs[Keys.ALERT_OVERRIDES_JSON]).toMutableMap()
            if (overrides.remove(sensorId) != null) {
                prefs[Keys.ALERT_OVERRIDES_JSON] = json.encodeToString(overrides.values.toList())
            }
        }
    }

    suspend fun setSensorAlertOverride(override: SensorAlertOverride) {
        context.obdSettingsDataStore.edit { prefs ->
            val current = loadAlertOverrides(prefs[Keys.ALERT_OVERRIDES_JSON]).toMutableMap()
            current[override.sensorId] = override
            prefs[Keys.ALERT_OVERRIDES_JSON] = json.encodeToString(current.values.toList())
        }
    }

    suspend fun setAutoConnectOnLaunch(enabled: Boolean) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.AUTO_CONNECT_ON_LAUNCH] = enabled
        }
    }

    suspend fun setVoiceAlertsEnabled(enabled: Boolean) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.VOICE_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setAaPopupAlertsEnabled(enabled: Boolean) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.AA_POPUP_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun setTtsVolume(volume: Float) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.TTS_VOLUME] = volume.coerceIn(0.1f, 1.0f)
        }
    }

    suspend fun setTtsSpeechRate(rate: Float) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.TTS_SPEECH_RATE] = rate.coerceIn(0.6f, 1.6f)
        }
    }

    suspend fun setTtsVoiceName(voiceName: String?) {
        context.obdSettingsDataStore.edit { prefs ->
            if (voiceName.isNullOrBlank()) {
                prefs.remove(Keys.TTS_VOICE_NAME)
            } else {
                prefs[Keys.TTS_VOICE_NAME] = voiceName
            }
        }
    }

    suspend fun markStartupPermissionsRequested() {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.HAS_REQUESTED_STARTUP_PERMS] = true
        }
    }

    suspend fun setPollingIntervalMs(intervalMs: Long) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.POLLING_INTERVAL_MS] = intervalMs.coerceIn(150L, 1500L)
        }
    }

    suspend fun setUseFahrenheit(useFahrenheit: Boolean) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.USE_FAHRENHEIT] = useFahrenheit
        }
    }

    suspend fun setPressureUnit(unit: PressureUnit) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.PRESSURE_UNIT] = unit.name
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = language.name
        }
    }

    suspend fun saveLastDevice(address: String, name: String, transport: BluetoothTransportType) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.LAST_DEVICE_ADDRESS] = address
            prefs[Keys.LAST_DEVICE_NAME] = name
            prefs[Keys.LAST_TRANSPORT] = transport.name
        }
    }
}
