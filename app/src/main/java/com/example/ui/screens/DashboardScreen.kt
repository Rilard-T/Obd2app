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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.obd.model.ObdPid
import com.example.ui.ObdUiState
import com.example.ui.components.ArcGauge
import com.example.ui.components.ConnectionStatusBanner
import com.example.ui.components.SensorTileCard
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    uiState: ObdUiState,
    onNavigateToConnection: () -> Unit,
    onStartSimulation: () -> Unit,
    onTogglePinAuto: (ObdPid) -> Unit,
    onOpenSensorChart: (ObdPid) -> Unit,
    onOpenAndroidAutoPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val autoPids = uiState.settings.androidAutoPids
    val dashboardPairs = ObdPid.entries.chunked(2)

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
                onActionClick = onNavigateToConnection
            )
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
                            text = "Быстрый старт телеметрии",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Подключите Bluetooth/BLE ELM327 адаптер к разъёму OBD2 автомобиля или включите режим симуляции для проверки отображения в Android Auto.",
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
                                Text("Подключить", color = MaterialTheme.colorScheme.onPrimary)
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
                                Text("Тест-режим", color = MotorsportAmber)
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
                            pid = ObdPid.ENGINE_RPM,
                            reading = uiState.readings[ObdPid.ENGINE_RPM],
                            useFahrenheit = uiState.settings.useFahrenheit,
                            pressureUnit = uiState.settings.pressureUnit,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        )
                        ArcGauge(
                            pid = ObdPid.VEHICLE_SPEED,
                            reading = uiState.readings[ObdPid.VEHICLE_SPEED],
                            useFahrenheit = uiState.settings.useFahrenheit,
                            pressureUnit = uiState.settings.pressureUnit,
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
                            text = "На экране Android Auto (${autoPids.size}/6)",
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
                            text = "Открыть макет",
                            style = MaterialTheme.typography.labelMedium,
                            color = TelemetryCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(autoPids, key = { it.pidHex }) { pid ->
                        val reading = uiState.readings[pid]
                        val valText = reading?.let {
                            "${pid.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} ${pid.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit)}"
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
                                    text = pid.shortName,
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
        items(dashboardPairs) { rowPids ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (pid in rowPids) {
                    SensorTileCard(
                        pid = pid,
                        reading = uiState.readings[pid],
                        isPinnedToCar = pid in autoPids,
                        useFahrenheit = uiState.settings.useFahrenheit,
                        pressureUnit = uiState.settings.pressureUnit,
                        onTogglePin = { onTogglePinAuto(pid) },
                        onClick = { onOpenSensorChart(pid) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowPids.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
