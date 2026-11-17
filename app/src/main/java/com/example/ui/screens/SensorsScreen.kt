package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.obd.data.CustomPidEntity
import com.example.obd.i18n.l10nContext
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AlertLevel
import com.example.obd.model.LivePidProbeResult
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorAlertOverride
import com.example.obd.model.SensorCategory
import com.example.obd.model.TelemetrySensor
import com.example.ui.ObdUiState
import com.example.ui.components.CustomPidEditorDialog
import com.example.ui.components.SensorAlertConfigDialog
import com.example.ui.components.TelemetryHistoryChart
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun SensorsScreen(
    uiState: ObdUiState,
    onSelectCategory: (SensorCategory?) -> Unit,
    onSelectChartSensor: (String) -> Unit,
    onTogglePinAuto: (String) -> Unit,
    onSetPollingMs: (Long) -> Unit,
    onSetUseFahrenheit: (Boolean) -> Unit,
    onSetPressureUnit: (PressureUnit) -> Unit,
    onAddCustomPid: (
        name: String,
        shortName: String,
        modeAndPid: String,
        header: String,
        equation: String,
        unit: String,
        minValue: Float,
        maxValue: Float,
        decimals: Int,
        warningHigh: Float?,
        criticalHigh: Float?,
        warningLow: Float?,
        criticalLow: Float?,
        alertsEnabled: Boolean
    ) -> Unit,
    onUpdateCustomPid: (CustomPidEntity) -> Unit,
    onDeleteCustomPid: (Long) -> Unit,
    onImportTorqueCsv: (String, (Int) -> Unit) -> Unit,
    onTestLivePid: suspend (modeAndPid: String, header: String, equation: String) -> LivePidProbeResult,
    onSetSensorAlertOverride: (SensorAlertOverride) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val l10nCtx = l10nContext()
    val isEng = uiState.settings.isEnglish
    var showPidDialog by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<CustomPidEntity?>(null) }
    var editingAlertSensor by remember { mutableStateOf<TelemetrySensor?>(null) }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                importFeedback = l10nCtx.getString(R.string.csv_read_error)
            } else {
                onImportTorqueCsv(text) { count ->
                    importFeedback = if (count > 0) {
                        l10nCtx.getString(R.string.csv_import_success, count)
                    } else {
                        l10nCtx.getString(R.string.csv_import_empty)
                    }
                }
            }
        }
    }

    if (showPidDialog) {
        CustomPidEditorDialog(
            initialEntity = editingEntity,
            onTestLivePid = onTestLivePid,
            onDismiss = {
                showPidDialog = false
                editingEntity = null
            },
            onSave = { name, shortName, modeAndPid, header, equation, unit, minVal, maxVal, decimals, warnH, critH, warnL, critL, alertsEn ->
                val currentEdit = editingEntity
                if (currentEdit == null) {
                    onAddCustomPid(
                        name, shortName, modeAndPid, header, equation, unit,
                        minVal, maxVal, decimals, warnH, critH, warnL, critL, alertsEn
                    )
                } else {
                    onUpdateCustomPid(
                        currentEdit.copy(
                            name = name,
                            shortName = shortName,
                            modeAndPid = modeAndPid,
                            header = header,
                            equation = equation,
                            unit = unit,
                            minValue = minVal,
                            maxValue = maxVal,
                            decimals = decimals,
                            warningHigh = warnH,
                            criticalHigh = critH,
                            warningLow = warnL,
                            criticalLow = critL,
                            alertsEnabled = alertsEn
                        )
                    )
                    onSetSensorAlertOverride(
                        SensorAlertOverride(
                            sensorId = currentEdit.sensorId,
                            alertsEnabled = alertsEn,
                            warningHigh = warnH,
                            criticalHigh = critH,
                            warningLow = warnL,
                            criticalLow = critL
                        )
                    )
                }
                showPidDialog = false
                editingEntity = null
            },
            isEnglish = isEng
        )
    }

    editingAlertSensor?.let { targetSensor ->
        SensorAlertConfigDialog(
            sensor = targetSensor,
            onDismiss = { editingAlertSensor = null },
            onSave = { override ->
                onSetSensorAlertOverride(override)
                if (targetSensor.isCustom && targetSensor.customId != null) {
                    uiState.customPids.find { it.id == targetSensor.customId }?.let { entity ->
                        onUpdateCustomPid(
                            entity.copy(
                                warningHigh = override.warningHigh,
                                criticalHigh = override.criticalHigh,
                                warningLow = override.warningLow,
                                criticalLow = override.criticalLow,
                                alertsEnabled = override.alertsEnabled
                            )
                        )
                    }
                }
                editingAlertSensor = null
            },
            useFahrenheit = uiState.settings.useFahrenheit,
            pressureUnit = uiState.settings.pressureUnit,
            isEnglish = isEng
        )
    }

    val allSensors = uiState.sensors
    val selectedSensor = allSensors.find { it.id == uiState.selectedChartSensorId }
        ?: allSensors.firstOrNull()
        ?: ObdPid.ENGINE_RPM.toTelemetrySensor(isEng)

    val filteredSensors = if (uiState.selectedCategory == null) {
        allSensors
    } else {
        allSensors.filter { it.category == uiState.selectedCategory }
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
                sensor = selectedSensor,
                reading = uiState.readings[selectedSensor.id],
                useFahrenheit = uiState.settings.useFahrenheit,
                pressureUnit = uiState.settings.pressureUnit,
                isEnglish = isEng
            )
        }

        // Custom Torque Pro PID toolbar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = l10nStr(R.string.custom_pid_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = l10nStr(R.string.custom_pid_card_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    uiState.supportedMode01Pids?.let { supported ->
                        val supportedBuiltIn = ObdPid.entries.count { it.modeAndPid in supported }
                        Text(
                            text = l10nStr(R.string.pid_map_active, supportedBuiltIn, ObdPid.entries.size, supported.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = NominalEmerald,
                            modifier = Modifier.testTag("supported_pid_map_badge")
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                editingEntity = null
                                showPidDialog = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_custom_pid_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                tint = CarbonObsidian,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.btn_add_pid), color = CarbonObsidian)
                        }
                        OutlinedButton(
                            onClick = {
                                csvPickerLauncher.launch(
                                    arrayOf("text/*", "text/csv", "text/comma-separated-values", "*/*")
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_torque_csv_button"),
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.65f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileOpen,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.btn_import_csv), color = TelemetryCyan)
                        }
                    }
                    importFeedback?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.labelMedium,
                            color = NominalEmerald,
                            modifier = Modifier.testTag("csv_import_feedback")
                        )
                    }
                }
            }
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
                        label = { Text(l10nStr(R.string.poll_fast)) },
                        modifier = Modifier.testTag("poll_fast_chip")
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.settings.pollingIntervalMs in 221L..450L,
                        onClick = { onSetPollingMs(350L) },
                        label = { Text(l10nStr(R.string.poll_normal)) },
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
                        label = { Text(unit.localizedLabel(isEng)) },
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
                        label = { Text(l10nStr(R.string.cat_all_count, allSensors.size)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = TelemetryCyan
                        ),
                        modifier = Modifier.testTag("cat_all_chip")
                    )
                }
                items(SensorCategory.entries) { category ->
                    val count = allSensors.count { it.category == category }
                    val catName = category.localizedName(isEng)
                    FilterChip(
                        selected = uiState.selectedCategory == category,
                        onClick = { onSelectCategory(category) },
                        label = {
                            Text(
                                if (category == SensorCategory.CUSTOM) "$catName ($count)"
                                else catName
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                            selectedLabelColor = TelemetryCyan
                        ),
                        modifier = Modifier.testTag("cat_${category.name}_chip")
                    )
                }
            }
        }

        items(filteredSensors, key = { it.id }) { sensor ->
            val reading = uiState.readings[sensor.id]
            val isChartSelected = sensor.id == selectedSensor.id
            val isPinned = sensor.id in uiState.settings.androidAutoSensorIds
            val valueColor = when (reading?.alertLevel) {
                AlertLevel.CRITICAL -> RedlineCrimson
                AlertLevel.WARNING -> MotorsportAmber
                else -> TelemetryCyan
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectChartSensor(sensor.id) }
                    .testTag("sensor_row_${sensor.id}"),
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
                            text = sensor.localizedDisplayName(isEng),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val hdr = if (sensor.header.isNotBlank()) " • HDR ${sensor.header}" else ""
                        val supportedSet = uiState.supportedMode01Pids
                        val unsupportedNote = if (
                            supportedSet != null &&
                            !sensor.isCustom &&
                            sensor.builtInPid != null &&
                            sensor.builtInPid != ObdPid.CONTROL_MODULE_VOLTAGE &&
                            sensor.modeAndPid !in supportedSet
                        ) {
                            l10nStr(R.string.not_in_ecu)
                        } else {
                            ""
                        }
                        val minMaxText = reading?.let {
                            val minStr = sensor.formatValue(it.minValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)
                            val maxStr = sensor.formatValue(it.maxValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)
                            l10nStr(R.string.min_max_line, sensor.modeAndPid, hdr, minStr, maxStr, unsupportedNote)
                        } ?: "PID ${sensor.modeAndPid}$hdr • ${sensor.category.localizedName(isEng)}$unsupportedNote"
                        Text(
                            text = minMaxText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = reading?.let {
                            "${sensor.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)} ${sensor.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)}"
                        } ?: "--",
                        style = MaterialTheme.typography.titleLarge,
                        color = valueColor
                    )

                    val hasThreshold = sensor.alertsEnabled && (
                        sensor.warningThreshold != null ||
                            sensor.criticalThreshold != null ||
                            sensor.lowWarningThreshold != null ||
                            sensor.lowCriticalThreshold != null
                        )
                    IconButton(
                        onClick = { editingAlertSensor = sensor },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("alert_config_${sensor.id}")
                    ) {
                        Icon(
                            imageVector = if (hasThreshold) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                            contentDescription = l10nStr(R.string.cd_alert_thresholds),
                            tint = if (hasThreshold) MotorsportAmber else TextMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    if (sensor.isCustom && sensor.customId != null) {
                        val customEntity = uiState.customPids.find { it.id == sensor.customId }
                        if (customEntity != null) {
                            IconButton(
                                onClick = {
                                    editingEntity = customEntity
                                    showPidDialog = true
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("edit_custom_pid_${sensor.customId}")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = l10nStr(R.string.cd_edit_pid),
                                    tint = TextSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onDeleteCustomPid(sensor.customId) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_custom_pid_${sensor.customId}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = l10nStr(R.string.cd_delete_pid),
                                tint = RedlineCrimson,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onTogglePinAuto(sensor.id) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sensors_pin_${sensor.id}")
                    ) {
                        Icon(
                            imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = l10nStr(R.string.cd_pin_sensor_aa),
                            tint = if (isPinned) TelemetryCyan else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
