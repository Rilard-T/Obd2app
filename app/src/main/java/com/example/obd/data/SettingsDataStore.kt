package com.example.obd.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.obd.model.BluetoothTransportType
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.obdSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "obd_settings"
)

data class ObdUserSettings(
    val androidAutoPids: List<ObdPid> = ObdPid.defaultAndroidAutoPids,
    val pollingIntervalMs: Long = 350L,
    val useFahrenheit: Boolean = false,
    val pressureUnit: PressureUnit = PressureUnit.KPA,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
    val lastTransport: BluetoothTransportType = BluetoothTransportType.CLASSIC_SPP
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val ANDROID_AUTO_PIDS = stringPreferencesKey("android_auto_pids")
        val POLLING_INTERVAL_MS = longPreferencesKey("polling_interval_ms")
        val USE_FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val LAST_DEVICE_ADDRESS = stringPreferencesKey("last_device_address")
        val LAST_DEVICE_NAME = stringPreferencesKey("last_device_name")
        val LAST_TRANSPORT = stringPreferencesKey("last_transport")
    }

    val settingsFlow: Flow<ObdUserSettings> = context.obdSettingsDataStore.data.map { prefs ->
        val rawPids = prefs[Keys.ANDROID_AUTO_PIDS]
        val parsedPids = rawPids
            ?.split(",")
            ?.mapNotNull { ObdPid.fromModeAndPid(it.trim()) }
            ?.distinct()
            ?.take(6)
            ?.takeIf { it.isNotEmpty() }
            ?: ObdPid.defaultAndroidAutoPids

        val pressureUnit = prefs[Keys.PRESSURE_UNIT]?.let { name ->
            PressureUnit.entries.find { it.name == name }
        } ?: PressureUnit.KPA

        val transport = prefs[Keys.LAST_TRANSPORT]?.let { name ->
            BluetoothTransportType.entries.find { it.name == name }
        } ?: BluetoothTransportType.CLASSIC_SPP

        ObdUserSettings(
            androidAutoPids = parsedPids,
            pollingIntervalMs = prefs[Keys.POLLING_INTERVAL_MS] ?: 350L,
            useFahrenheit = prefs[Keys.USE_FAHRENHEIT] ?: false,
            pressureUnit = pressureUnit,
            lastDeviceAddress = prefs[Keys.LAST_DEVICE_ADDRESS],
            lastDeviceName = prefs[Keys.LAST_DEVICE_NAME],
            lastTransport = transport
        )
    }

    suspend fun toggleAndroidAutoPid(pid: ObdPid) {
        context.obdSettingsDataStore.edit { prefs ->
            val current = prefs[Keys.ANDROID_AUTO_PIDS]
                ?.split(",")
                ?.mapNotNull { ObdPid.fromModeAndPid(it.trim()) }
                ?.toMutableList()
                ?: ObdPid.defaultAndroidAutoPids.toMutableList()

            if (pid in current) {
                if (current.size > 1) {
                    current.remove(pid)
                }
            } else {
                if (current.size >= 6) {
                    current.removeAt(current.lastIndex)
                }
                current.add(pid)
            }
            prefs[Keys.ANDROID_AUTO_PIDS] = current.joinToString(",") { it.modeAndPid }
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

    suspend fun saveLastDevice(address: String, name: String, transport: BluetoothTransportType) {
        context.obdSettingsDataStore.edit { prefs ->
            prefs[Keys.LAST_DEVICE_ADDRESS] = address
            prefs[Keys.LAST_DEVICE_NAME] = name
            prefs[Keys.LAST_TRANSPORT] = transport.name
        }
    }
}
