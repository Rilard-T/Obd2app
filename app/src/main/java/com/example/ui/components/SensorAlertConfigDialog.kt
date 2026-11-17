package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorAlertOverride
import com.example.obd.model.TelemetrySensor
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

@Composable
fun SensorAlertConfigDialog(
    sensor: TelemetrySensor,
    onDismiss: () -> Unit,
    onSave: (SensorAlertOverride) -> Unit,
    useFahrenheit: Boolean = false,
    pressureUnit: PressureUnit = PressureUnit.KPA,
    isEnglish: Boolean = false
) {
    fun formatFloatField(v: Float?): String {
        if (v == null) return ""
        return if (v % 1f == 0f) v.toInt().toString() else v.toString()
    }

    var alertsEnabled by remember(sensor.id) { mutableStateOf(sensor.alertsEnabled) }
    var warnHighText by remember(sensor.id) { mutableStateOf(formatFloatField(sensor.warningThreshold)) }
    var critHighText by remember(sensor.id) { mutableStateOf(formatFloatField(sensor.criticalThreshold)) }
    var warnLowText by remember(sensor.id) { mutableStateOf(formatFloatField(sensor.lowWarningThreshold)) }
    var critLowText by remember(sensor.id) { mutableStateOf(formatFloatField(sensor.lowCriticalThreshold)) }

    val dispUnit = sensor.displayUnit(useFahrenheit, pressureUnit, isEnglish).ifBlank { sensor.modeAndPid }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sensor_alert_config_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, CockpitBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MotorsportAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = l10nStr(R.string.alert_dlg_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${sensor.localizedDisplayName(isEnglish)} ($dispUnit)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = l10nStr(R.string.action_close),
                            tint = TextSecondary
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = l10nStr(R.string.alert_dlg_enable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = { alertsEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan),
                        modifier = Modifier.testTag("sensor_alert_enabled_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = warnHighText,
                        onValueChange = { warnHighText = it },
                        label = { Text(l10nStr(R.string.alert_warn_high)) },
                        placeholder = { Text(l10nStr(R.string.alert_hint_102)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alert_warn_high_input")
                    )
                    OutlinedTextField(
                        value = critHighText,
                        onValueChange = { critHighText = it },
                        label = { Text(l10nStr(R.string.alert_crit_high)) },
                        placeholder = { Text(l10nStr(R.string.alert_hint_110)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alert_crit_high_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = warnLowText,
                        onValueChange = { warnLowText = it },
                        label = { Text(l10nStr(R.string.alert_warn_low)) },
                        placeholder = { Text(l10nStr(R.string.alert_hint_12_1)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alert_warn_low_input")
                    )
                    OutlinedTextField(
                        value = critLowText,
                        onValueChange = { critLowText = it },
                        label = { Text(l10nStr(R.string.alert_crit_low)) },
                        placeholder = { Text(l10nStr(R.string.alert_hint_11_5)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("alert_crit_low_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, CockpitBorder)
                    ) {
                        Text(l10nStr(R.string.action_cancel), color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            onSave(
                                SensorAlertOverride(
                                    sensorId = sensor.id,
                                    alertsEnabled = alertsEnabled,
                                    warningHigh = warnHighText.replace(',', '.').toFloatOrNull(),
                                    criticalHigh = critHighText.replace(',', '.').toFloatOrNull(),
                                    warningLow = warnLowText.replace(',', '.').toFloatOrNull(),
                                    criticalLow = critLowText.replace(',', '.').toFloatOrNull()
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_sensor_alert_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                    ) {
                        Text(l10nStr(R.string.action_save), color = CarbonObsidian)
                    }
                }
            }
        }
    }
}
