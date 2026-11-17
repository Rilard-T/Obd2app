package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorCategory
import com.example.ui.ObdUiState
import com.example.ui.components.TelemetryHistoryChart
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun SensorsScreen(
    uiState: ObdUiState,
    onSelectCategory: (SensorCategory?) -> Unit,
    onSelectChartPid: (ObdPid) -> Unit,
    onTogglePinAuto: (ObdPid) -> Unit,
    onSetPollingMs: (Long) -> Unit,
    onSetUseFahrenheit: (Boolean) -> Unit,
    onSetPressureUnit: (PressureUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredPids = if (uiState.selectedCategory == null) {
        ObdPid.entries
    } else {
        ObdPid.entries.filter { it.category == uiState.selectedCategory }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sensors_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TelemetryHistoryChart(
                pid = uiState.selectedChartPid,
                reading = uiState.readings[uiState.selectedChartPid],
                useFahrenheit = uiState.settings.useFahrenheit,
                pressureUnit = uiState.settings.pressureUnit
            )
        }

        // Units & polling frequency quick strip
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.settings.pollingIntervalMs <= 220L,
                        onClick = { onSetPollingMs(200L) },
                        label = { Text("Быстро (5 Гц)") },
                        modifier = Modifier.testTag("poll_fast_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.settings.pollingIntervalMs in 221L..450L,
                        onClick = { onSetPollingMs(350L) },
                        label = { Text("Норма (3 Гц)") },
                        modifier = Modifier.testTag("poll_normal_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = !uiState.settings.useFahrenheit,
                        onClick = { onSetUseFahrenheit(!uiState.settings.useFahrenheit) },
                        label = { Text(if (uiState.settings.useFahrenheit) "°F" else "°C") },
                        modifier = Modifier.testTag("unit_temp_chip")
                    )
                }
                items(PressureUnit.entries) { unit ->
                    FilterChip(
                        selected = uiState.settings.pressureUnit == unit,
                        onClick = { onSetPressureUnit(unit) },
                        label = { Text(unit.label) },
                        modifier = Modifier.testTag("unit_pressure_${unit.name}")
                    )
                }
            }
        }

        // Category filter chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedCategory == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("Все (${ObdPid.entries.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = TelemetryCyan
                        ),
                        modifier = Modifier.testTag("cat_all_chip")
                    )
                }
                items(SensorCategory.entries) { category ->
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text(category.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = TelemetryCyan
                        ),
                        modifier = Modifier.testTag("cat_${category.name}_chip")
                    )
                }
            }
        }

        items(filteredPids, key = { it.pidHex }) { pid ->
            val reading = uiState.readings[pid]
            val isChartSelected = pid == uiState.selectedChartPid
            val isPinned = pid in uiState.settings.androidAutoPids
            val valueColor = when (reading?.alertLevel) {
                AlertLevel.CRITICAL -> RedlineCrimson
                AlertLevel.WARNING -> MotorsportAmber
                else -> TelemetryCyan
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChartPid(pid) }
                    .testTag("sensor_row_${pid.pidHex}"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = if (isChartSelected) CockpitSurfaceElevated else CockpitSurface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isChartSelected) TelemetryCyan else CockpitBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pid.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val minMaxText = reading?.let {
                            val minStr = pid.formatValue(it.minValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)
                            val maxStr = pid.formatValue(it.maxValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)
                            "PID ${pid.modeAndPid} • Мин $minStr / Макс $maxStr"
                        } ?: "PID ${pid.modeAndPid} • ${pid.category.displayName}"
                        Text(
                            text = minMaxText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = reading?.let {
                            "${pid.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} ${pid.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit)}"
                        } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        color = valueColor
                    )

                    IconButton(
                        onClick = { onTogglePinAuto(pid) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sensors_pin_${pid.pidHex}")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Закрепить на экране Android Auto",
                            tint = if (isPinned) TelemetryCyan else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
