package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.obd.model.DtcSeverity
import com.example.ui.ObdUiState
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    uiState: ObdUiState,
    onScanDtcs: () -> Unit,
    onClearDtcs: () -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    val dtc = uiState.dtcState
    val dateFormatter = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("Сбросить ошибки ЭБУ?") },
            text = {
                Text(
                    "Команда Mode 04 удалит сохранённые коды неисправностей и погасит лампу Check Engine. Выполняйте только на заглушенном двигателе при включённом зажигании."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClearDialog = false
                        onClearDtcs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedlineCrimson),
                    modifier = Modifier.testTag("confirm_clear_dtc_button")
                ) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmClearDialog = false },
                    modifier = Modifier.testTag("cancel_clear_dtc_button")
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            val milColor = if (dtc.milOn) RedlineCrimson else NominalEmerald
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (dtc.milOn) RedlineCrimson.copy(alpha = 0.12f) else CockpitSurface
                ),
                border = BorderStroke(1.dp, milColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (dtc.milOn) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                            contentDescription = "Состояние Check Engine",
                            tint = milColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (dtc.milOn) "Check Engine (MIL): АКТИВЕН" else "Check Engine (MIL): Норма",
                                style = MaterialTheme.typography.titleLarge,
                                color = milColor
                            )
                            Text(
                                text = dtc.statusMessage ?: "Нажмите «Считать ошибки» для опроса блоков ЭБУ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onScanDtcs,
                            enabled = !dtc.isScanning && uiState.connectionState.isActiveTelemetry,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_dtc_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                        ) {
                            if (dtc.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Считать ошибки", color = MaterialTheme.colorScheme.onPrimary)
                        }

                        OutlinedButton(
                            onClick = { showConfirmClearDialog = true },
                            enabled = !dtc.isScanning && uiState.connectionState.isActiveTelemetry,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("clear_dtc_button"),
                            border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = RedlineCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сбросить", color = RedlineCrimson)
                        }
                    }
                }
            }
        }

        if (dtc.codes.isNotEmpty()) {
            item {
                Text(
                    text = "Активные коды неисправностей (${dtc.codes.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(dtc.codes, key = { it.code }) { code ->
                val badgeColor = when (code.severity) {
                    DtcSeverity.CRITICAL -> RedlineCrimson
                    DtcSeverity.MODERATE -> MotorsportAmber
                    DtcSeverity.INFO -> TelemetryCyan
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = code.code,
                                style = MaterialTheme.typography.headlineMedium,
                                color = badgeColor
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = badgeColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = code.severity.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = code.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = code.system,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "История проверок на устройстве",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (uiState.scanHistory.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Text("Очистить", color = TextSecondary)
                    }
                }
            }
        }

        if (uiState.scanHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Text(
                        text = "Сохранённых сканирований пока нет. Подключите адаптер и нажмите «Считать ошибки».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(uiState.scanHistory, key = { it.id }) { scan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = scan.adapterName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateFormatter.format(Date(scan.timestampMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = "Результат: ${scan.dtcSummary}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (scan.dtcCount > 0) MotorsportAmber else NominalEmerald
                        )
                        val voltStr = scan.batteryVoltage?.let { " • ${String.format("%.1f", it)} В" } ?: ""
                        Text(
                            text = "${scan.protocol}$voltStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
