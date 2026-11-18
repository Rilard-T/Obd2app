package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.BatteryTestPhase
import com.example.obd.model.BatteryTestState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun BatteryTestDialog(
    state: BatteryTestState,
    isEnglish: Boolean,
    isConnectionActive: Boolean,
    onStartTest: () -> Unit,
    onCancelTest: () -> Unit,
    onDismiss: () -> Unit
) {
    val isRunning = state.phase in listOf(
        BatteryTestPhase.RESTING_SAMPLE,
        BatteryTestPhase.CRANKING_WATCH,
        BatteryTestPhase.CHARGING_IDLE,
        BatteryTestPhase.CHARGING_LOAD
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battery_test_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.55f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.BatteryChargingFull,
                            contentDescription = null,
                            tint = NominalEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.batt_test_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = l10nStr(R.string.action_close), tint = TextSecondary)
                    }
                }

                val phaseText = when (state.phase) {
                    BatteryTestPhase.IDLE -> l10nStr(R.string.batt_phase_resting)
                    BatteryTestPhase.RESTING_SAMPLE -> l10nStr(R.string.batt_phase_resting)
                    BatteryTestPhase.CRANKING_WATCH -> l10nStr(R.string.batt_phase_crank)
                    BatteryTestPhase.CHARGING_IDLE -> l10nStr(R.string.batt_phase_idle)
                    BatteryTestPhase.CHARGING_LOAD -> l10nStr(R.string.batt_phase_load)
                    BatteryTestPhase.COMPLETED -> l10nStr(R.string.batt_phase_done)
                    BatteryTestPhase.FAILED -> state.errorMessage ?: "Error"
                }

                Text(
                    text = phaseText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (state.phase) {
                        BatteryTestPhase.CRANKING_WATCH -> MotorsportAmber
                        BatteryTestPhase.COMPLETED -> NominalEmerald
                        BatteryTestPhase.FAILED -> RedlineCrimson
                        else -> TelemetryCyan
                    }
                )

                LinearProgressIndicator(
                    progress = { state.progressPct.coerceIn(0f, 1f) },
                    color = if (state.phase == BatteryTestPhase.COMPLETED) NominalEmerald else TelemetryCyan,
                    trackColor = CarbonObsidian,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                state.liveVoltage?.let { live ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonObsidian,
                        border = BorderStroke(1.dp, CockpitBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2f V", live),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            color = TelemetryCyan,
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoltageCell(
                        label = l10nStr(R.string.batt_resting_label),
                        voltage = state.restingVoltage,
                        accent = TelemetryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    VoltageCell(
                        label = l10nStr(R.string.batt_crank_label),
                        voltage = state.minCrankingVoltage,
                        accent = MotorsportAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VoltageCell(
                        label = l10nStr(R.string.batt_idle_label),
                        voltage = state.idleChargingVoltage,
                        accent = NominalEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    VoltageCell(
                        label = l10nStr(R.string.batt_load_label),
                        voltage = state.loadChargingVoltage,
                        accent = NominalEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.phase == BatteryTestPhase.COMPLETED) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = CockpitSurfaceElevated,
                        border = BorderStroke(1.dp, NominalEmerald.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isEnglish) state.batteryStatusEn else state.batteryStatusRu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEnglish) state.alternatorStatusEn else state.alternatorStatusRu,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TelemetryCyan
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRunning) {
                        OutlinedButton(
                            onClick = onCancelTest,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.7f))
                        ) {
                            Text(l10nStr(R.string.action_cancel), color = RedlineCrimson)
                        }
                    } else {
                        Button(
                            onClick = onStartTest,
                            enabled = isConnectionActive,
                            colors = ButtonDefaults.buttonColors(containerColor = NominalEmerald),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("battery_test_start_button")
                        ) {
                            Text(l10nStr(R.string.batt_start_btn), color = CarbonObsidian)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoltageCell(
    label: String,
    voltage: Float?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = CockpitSurfaceElevated,
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(
                text = voltage?.let { String.format(Locale.US, "%.2f V", it) } ?: "--",
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
        }
    }
}
