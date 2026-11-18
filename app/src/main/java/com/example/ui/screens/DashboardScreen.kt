package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.ui.ObdUiState
import com.example.ui.components.ArcGauge
import com.example.ui.components.ConnectionStatusBanner
import com.example.ui.components.SensorTileCard
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    uiState: ObdUiState,
    onNavigateToConnection: () -> Unit,
    onStartSimulation: () -> Unit,
    onTogglePinAuto: (String) -> Unit,
    onOpenSensorChart: (String) -> Unit,
    onOpenAndroidAutoPreview: () -> Unit,
    onOpenHudMode: () -> Unit,
    onOpenVoiceAssistant: () -> Unit,
    onDismissAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEng = uiState.settings.isEnglish
    val autoSensorIds = uiState.settings.androidAutoSensorIds
    val allSensors = uiState.sensors
    val isFullscreenAuto =
        uiState.settings.effectiveAndroidAutoDisplayMode == com.example.obd.data.AndroidAutoDisplayMode.FULLSCREEN_COCKPIT
    val activeCarIds = if (isFullscreenAuto) {
        (uiState.settings.fullscreenDialSensorIds + uiState.settings.fullscreenStripSensorIds).distinct()
    } else {
        autoSensorIds
    }
    val pinnedSensors = activeCarIds.mapNotNull { id -> allSensors.find { it.id == id } }
    val dashboardPairs = allSensors.chunked(2)

    val rpmSensor = allSensors.find { it.id == ObdPid.ENGINE_RPM.modeAndPid }
        ?: ObdPid.ENGINE_RPM.toTelemetrySensor(isEng)
    val speedSensor = allSensors.find { it.id == ObdPid.VEHICLE_SPEED.modeAndPid }
        ?: ObdPid.VEHICLE_SPEED.toTelemetrySensor(isEng)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ConnectionStatusBanner(
                connectionState = uiState.connectionState,
                onActionClick = onNavigateToConnection,
                isEnglish = isEng
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val activeCarName = uiState.activeVehicleProfile?.name ?: (if (isEng) "My Car" else "Авто")
                val vinSuffix = uiState.vehicleInfo.vin?.let { " • ${it.takeLast(6)}" } ?: ""
                Text(
                    text = "$activeCarName$vinSuffix",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenVoiceAssistant,
                        border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("dashboard_voice_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = MotorsportAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(l10nStr(R.string.voice_ai_btn), color = MotorsportAmber)
                    }
                    OutlinedButton(
                        onClick = onOpenHudMode,
                        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f)),
                        modifier = Modifier.testTag("dashboard_hud_button")
                    ) {
                        Text(l10nStr(R.string.btn_hud_mode), color = TelemetryCyan)
                    }
                }
            }
        }

        uiState.activeAlert?.let { alert ->
            item {
                val alertColor = if (alert.level == AlertLevel.CRITICAL) RedlineCrimson else MotorsportAmber
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("active_alert_banner"),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = alertColor.copy(alpha = 0.16f)),
                    border = BorderStroke(1.5.dp, alertColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = l10nStr(R.string.cd_warning),
                            tint = alertColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = alert.message,
                                style = MaterialTheme.typography.titleMedium,
                                color = alertColor
                            )
                            Text(
                                text = l10nStr(R.string.alert_threshold_exceeded),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = onDismissAlert,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("dismiss_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = l10nStr(R.string.cd_dismiss_alert),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        if (!uiState.connectionState.isActiveTelemetry) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                    border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = l10nStr(R.string.quick_start_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = l10nStr(R.string.quick_start_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onNavigateToConnection,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_connect_obd_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SettingsInputComponent,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(l10nStr(R.string.action_connect), color = MaterialTheme.colorScheme.onPrimary)
                            }
                            OutlinedButton(
                                onClick = onStartSimulation,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_simulation_button"),
                                border = BorderStroke(1.dp, MotorsportAmber)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = MotorsportAmber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(l10nStr(R.string.action_sim_mode), color = MotorsportAmber)
                            }
                        }
                    }
                }
            }
        }

        // Primary Digital Instrument Cluster (RPM & Speed)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArcGauge(
                            sensor = rpmSensor,
                            reading = uiState.readings[rpmSensor.id],
                            useFahrenheit = uiState.settings.useFahrenheit,
                            pressureUnit = uiState.settings.pressureUnit,
                            isEnglish = isEng,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                        ArcGauge(
                            sensor = speedSensor,
                            reading = uiState.readings[speedSensor.id],
                            useFahrenheit = uiState.settings.useFahrenheit,
                            pressureUnit = uiState.settings.pressureUnit,
                            isEnglish = isEng,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                    }
                }
            }
        }

        // Android Auto active grid preview strip
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = "Android Auto",
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFullscreenAuto) {
                                l10nStr(R.string.aa_strip_fullscreen)
                            } else {
                                l10nStr(R.string.aa_strip_grid_count, pinnedSensors.size)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Surface(
                        onClick = onOpenAndroidAutoPreview,
                        shape = MaterialTheme.shapes.small,
                        color = TelemetryCyan.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("open_aa_preview_button")
                    ) {
                        Text(
                            text = l10nStr(R.string.aa_open_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = TelemetryCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pinnedSensors, key = { it.id }) { sensor ->
                        val reading = uiState.readings[sensor.id]
                        val valText = reading?.let {
                            "${sensor.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)} ${sensor.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)}"
                        } ?: "--"
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = sensor.localizedShortName(isEng),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan
                                )
                                Text(
                                    text = valText,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2-column sensor grid
        items(dashboardPairs) { rowSensors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (sensor in rowSensors) {
                    SensorTileCard(
                        sensor = sensor,
                        reading = uiState.readings[sensor.id],
                        isPinnedToCar = sensor.id in autoSensorIds,
                        useFahrenheit = uiState.settings.useFahrenheit,
                        pressureUnit = uiState.settings.pressureUnit,
                        onTogglePin = { onTogglePinAuto(sensor.id) },
                        onClick = { onOpenSensorChart(sensor.id) },
                        isEnglish = isEng,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowSensors.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
