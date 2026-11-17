package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.data.AndroidAutoDisplayMode
import com.example.obd.i18n.l10nContext
import com.example.obd.i18n.l10nStr
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorCategory
import com.example.obd.model.TelemetrySensor
import com.example.ui.ObdUiState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

sealed interface CockpitSlotTarget {
    val label: String
    data class Dial(val index: Int, override val label: String) : CockpitSlotTarget
    data class Strip(val index: Int, override val label: String) : CockpitSlotTarget
}

@Composable
fun CockpitLayoutConfiguratorSection(
    uiState: ObdUiState,
    onSelectDisplayMode: (AndroidAutoDisplayMode) -> Unit,
    onSelectPreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onSetDialSlot: (Int, String) -> Unit,
    onSetStripSlot: (Int, String) -> Unit,
    onResetFullscreenDefaults: () -> Unit,
    onToggleGridSensor: (String) -> Unit,
    onOpenPreview: () -> Unit
) {
    val isEng = uiState.settings.isEnglish
    val useF = uiState.settings.useFahrenheit
    val pUnit = uiState.settings.pressureUnit
    var pickerTarget by remember { mutableStateOf<CockpitSlotTarget?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val activeTarget = pickerTarget
    if (activeTarget != null) {
        val currentId = when (activeTarget) {
            is CockpitSlotTarget.Dial -> uiState.settings.fullscreenDialSensorIds.getOrNull(activeTarget.index)
            is CockpitSlotTarget.Strip -> uiState.settings.fullscreenStripSensorIds.getOrNull(activeTarget.index)
        }
        SensorSlotPickerDialog(
            title = l10nStr(R.string.cockpit_pick_sensor_title, activeTarget.label),
            sensors = uiState.sensors,
            selectedSensorId = currentId,
            useFahrenheit = useF,
            pressureUnit = pUnit,
            isEnglish = isEng,
            onSelectSensor = { chosen ->
                when (activeTarget) {
                    is CockpitSlotTarget.Dial -> onSetDialSlot(activeTarget.index, chosen.id)
                    is CockpitSlotTarget.Strip -> onSetStripSlot(activeTarget.index, chosen.id)
                }
                pickerTarget = null
            },
            onDismiss = { pickerTarget = null }
        )
    }

    if (showRenameDialog) {
        val activePreset = uiState.settings.activePreset
        var newName by remember(activePreset.id, isEng) {
            mutableStateOf(activePreset.localizedName(isEng))
        }
        Dialog(onDismissRequest = { showRenameDialog = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = CockpitSurface,
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = l10nStr(R.string.cockpit_rename_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it.take(18) },
                        singleLine = true,
                        label = { Text(l10nStr(R.string.cockpit_rename_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRenameDialog = false },
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CockpitBorder)
                        ) {
                            Text(l10nStr(R.string.action_cancel), color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    onRenamePreset(activePreset.id, newName)
                                }
                                showRenameDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                        ) {
                            Text(l10nStr(R.string.action_save), color = CarbonObsidian)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = l10nStr(R.string.cockpit_cfg_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = l10nStr(R.string.cockpit_cfg_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isFullscreen = uiState.settings.androidAutoDisplayMode == AndroidAutoDisplayMode.FULLSCREEN_COCKPIT
            ModeOptionButton(
                title = l10nStr(R.string.cockpit_mode_fullscreen),
                subtitle = l10nStr(R.string.cockpit_mode_fullscreen_sub),
                icon = Icons.Filled.Fullscreen,
                selected = isFullscreen,
                onClick = { onSelectDisplayMode(AndroidAutoDisplayMode.FULLSCREEN_COCKPIT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("aa_mode_fullscreen_button")
            )
            ModeOptionButton(
                title = l10nStr(R.string.cockpit_mode_grid),
                subtitle = l10nStr(R.string.cockpit_mode_grid_sub),
                icon = Icons.Filled.GridView,
                selected = !isFullscreen,
                onClick = { onSelectDisplayMode(AndroidAutoDisplayMode.STANDARD_GRID) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("aa_mode_grid_button")
            )
        }

        if (uiState.settings.androidAutoDisplayMode == AndroidAutoDisplayMode.FULLSCREEN_COCKPIT) {
            val l10nCtx = l10nContext()
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = CockpitSurface,
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Dashboard Preset Selector (Switchable in 1 tap both here and in AA ActionStrip)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = l10nStr(R.string.cockpit_profile_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        IconButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("rename_preset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = l10nStr(R.string.cd_rename_profile),
                                tint = TelemetryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.settings.cockpitPresets, key = { it.id }) { preset ->
                            val selected = preset.id == uiState.settings.activePresetId
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectPreset(preset.id) },
                                label = { Text(preset.localizedName(isEng)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = TelemetryCyan
                                ),
                                modifier = Modifier.testTag("cockpit_preset_${preset.id}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Speed,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = l10nStr(R.string.cockpit_dials_header),
                                style = MaterialTheme.typography.labelLarge,
                                color = TelemetryCyan
                            )
                        }
                        TextButton(
                            onClick = onResetFullscreenDefaults,
                            modifier = Modifier.testTag("reset_cockpit_slots_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RestartAlt,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.cockpit_reset), color = TextSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dialLabels = listOf(
                            l10nStr(R.string.dial_left),
                            l10nStr(R.string.dial_center),
                            l10nStr(R.string.dial_right)
                        )
                        val dialDefaults = listOf(
                            ObdPid.ENGINE_RPM.modeAndPid,
                            ObdPid.VEHICLE_SPEED.modeAndPid,
                            ObdPid.COOLANT_TEMP.modeAndPid
                        )
                        dialLabels.forEachIndexed { idx, slotTitle ->
                            val sensorId = uiState.settings.fullscreenDialSensorIds.getOrNull(idx)
                                ?: dialDefaults[idx]
                            val sensor = uiState.sensors.find { it.id == sensorId }
                            CockpitSlotTile(
                                slotHeader = slotTitle,
                                sensor = sensor,
                                fallbackId = sensorId,
                                useFahrenheit = useF,
                                pressureUnit = pUnit,
                                isEnglish = isEng,
                                onClick = {
                                    pickerTarget = CockpitSlotTarget.Dial(
                                        idx,
                                        l10nCtx.getString(R.string.dial_slot_label, slotTitle)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dial_slot_$idx")
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = l10nStr(R.string.cockpit_strip_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = TelemetryCyan
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val stripDefaults = listOf(
                            ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid,
                            ObdPid.ENGINE_LOAD.modeAndPid,
                            ObdPid.OIL_TEMP.modeAndPid,
                            ObdPid.INTAKE_MAP.modeAndPid
                        )
                        for (idx in 0 until 4) {
                            val sensorId = uiState.settings.fullscreenStripSensorIds.getOrNull(idx)
                                ?: stripDefaults[idx]
                            val sensor = uiState.sensors.find { it.id == sensorId }
                            CockpitSlotTile(
                                slotHeader = l10nStr(R.string.strip_slot_short, idx + 1),
                                sensor = sensor,
                                fallbackId = sensorId,
                                useFahrenheit = useF,
                                pressureUnit = pUnit,
                                isEnglish = isEng,
                                onClick = {
                                    pickerTarget = CockpitSlotTarget.Strip(
                                        idx,
                                        l10nCtx.getString(R.string.strip_slot_full, idx + 1)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("strip_slot_$idx")
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onOpenPreview,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_cockpit_preview_from_config_button"),
                        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f))
                    ) {
                        Text(l10nStr(R.string.cockpit_open_preview_btn), color = TelemetryCyan)
                    }
                }
            }
        } else {
            val sensorRows = uiState.sensors.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pair in sensorRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (sensor in pair) {
                            val selected = sensor.id in uiState.settings.androidAutoSensorIds
                            val dispUnit = sensor.displayUnit(useF, pUnit, isEng)
                            val unitSuffix = if (dispUnit.isNotBlank()) " ($dispUnit)" else ""
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleGridSensor(sensor.id) },
                                label = { Text("${sensor.localizedShortName(isEng)}$unitSuffix", maxLines = 1) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = TelemetryCyan,
                                    selectedLeadingIconColor = TelemetryCyan
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("auto_pid_chip_${sensor.id}")
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeOptionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) TelemetryCyan.copy(alpha = 0.16f) else CockpitSurface,
        border = BorderStroke(1.dp, if (selected) TelemetryCyan else CockpitBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) TelemetryCyan else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) TelemetryCyan else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CockpitSlotTile(
    slotHeader: String,
    sensor: TelemetrySensor?,
    fallbackId: String,
    useFahrenheit: Boolean,
    pressureUnit: PressureUnit,
    isEnglish: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = CockpitSurfaceElevated,
        border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = slotHeader,
                style = MaterialTheme.typography.labelSmall,
                color = TelemetryCyan,
                maxLines = 1
            )
            Text(
                text = sensor?.localizedShortName(isEnglish) ?: fallbackId,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dispUnit = sensor?.displayUnit(useFahrenheit, pressureUnit, isEnglish)
            Text(
                text = dispUnit?.ifBlank { sensor.modeAndPid } ?: "PID",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SensorSlotPickerDialog(
    title: String,
    sensors: List<TelemetrySensor>,
    selectedSensorId: String?,
    onSelectSensor: (TelemetrySensor) -> Unit,
    onDismiss: () -> Unit,
    useFahrenheit: Boolean = false,
    pressureUnit: PressureUnit = PressureUnit.KPA,
    isEnglish: Boolean = false
) {
    val orderedCategories = listOf(SensorCategory.CUSTOM) +
        SensorCategory.entries.filter { it != SensorCategory.CUSTOM }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .testTag("sensor_slot_picker_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, CockpitBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
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

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    for (category in orderedCategories) {
                        val list = sensors.filter { it.category == category }
                        if (list.isEmpty()) continue
                        item(key = "cat_${category.name}") {
                            Text(
                                text = category.localizedName(isEnglish),
                                style = MaterialTheme.typography.labelLarge,
                                color = TelemetryCyan,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(list, key = { it.id }) { sensor ->
                            val isSelected = sensor.id == selectedSensorId
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSensor(sensor) }
                                    .testTag("pick_slot_sensor_${sensor.id}"),
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TelemetryCyan.copy(alpha = 0.16f) else CockpitSurfaceElevated,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) TelemetryCyan else CockpitBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = sensor.localizedDisplayName(isEnglish),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val unitTxt = sensor.displayUnit(useFahrenheit, pressureUnit, isEnglish)
                                        val tag = if (sensor.isCustom) {
                                            "Torque PID ${sensor.modeAndPid} • $unitTxt"
                                        } else {
                                            "PID ${sensor.modeAndPid} • $unitTxt"
                                        }
                                        Text(
                                            text = tag,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = l10nStr(R.string.cd_selected),
                                            tint = TelemetryCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
