package com.example.obd

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.ObdApplication
import com.example.R
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class ObdQuickConnectTileService : TileService() {

    private var serviceScope: CoroutineScope? = null
    private var collectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val app = application as? ObdApplication ?: return
        val repo = app.telemetryRepository
        collectJob?.cancel()
        val scope = serviceScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            serviceScope = it
        }
        collectJob = scope.launch {
            combine(
                repo.connectionState,
                repo.settings,
                repo.readings
            ) { conn, settings, readings ->
                Triple(conn, settings, readings)
            }.collect { (conn, settings, readings) ->
                val tile = qsTile ?: return@collect
                val isEng = settings.isEnglish
                when (conn) {
                    is ObdConnectionState.Connected -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = getString(R.string.qs_tile_connected)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val ect = readings[ObdPid.COOLANT_TEMP.modeAndPid]?.value
                            val volt = readings[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]?.value
                            val details = listOfNotNull(
                                ect?.let { "${it.toInt()}°C" },
                                volt?.let { String.format("%.1fV", it) }
                            ).joinToString(" • ").ifBlank { conn.deviceName }
                            tile.subtitle = details
                        }
                    }
                    is ObdConnectionState.Simulation -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = if (isEng) "OBD2 Sim" else "OBD2 Тест"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = "${String.format("%.1f", conn.pollingHz)} Hz"
                        }
                    }
                    is ObdConnectionState.Connecting -> {
                        tile.state = Tile.STATE_ACTIVE
                        tile.label = if (isEng) "Linking OBD2..." else "Поиск ELM327..."
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = conn.deviceName
                        }
                    }
                    is ObdConnectionState.Error,
                    is ObdConnectionState.Disconnected -> {
                        tile.state = Tile.STATE_INACTIVE
                        tile.label = getString(R.string.qs_tile_label)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            tile.subtitle = settings.lastDeviceName ?: if (isEng) "Tap to connect" else "Нажмите для связи"
                        }
                    }
                }
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val app = application as? ObdApplication ?: return
        val repo = app.telemetryRepository
        val conn = repo.connectionState.value
        if (conn.isActiveTelemetry || conn is ObdConnectionState.Connecting) {
            repo.disconnect()
        } else {
            val initiated = repo.quickConnectLastAdapter()
            if (!initiated) {
                val launchIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pi = PendingIntent.getActivity(
                        this,
                        101,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launchIntent)
                }
            }
        }
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        serviceScope?.cancel()
        serviceScope = null
        super.onDestroy()
    }
}
