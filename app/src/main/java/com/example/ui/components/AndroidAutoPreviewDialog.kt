package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.car.AnalogCockpitRenderer
import com.example.car.AnalogDialScale
import com.example.car.CarGaugeBitmapFactory
import com.example.car.CockpitRenderFrame
import com.example.obd.data.AndroidAutoDisplayMode
import com.example.obd.i18n.l10nContext
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.SensorCategory
import com.example.obd.model.TelemetrySensor
import com.example.ui.ObdUiState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun AndroidAutoPreviewDialog(
    uiState: ObdUiState,
    onDismiss: () -> Unit,
    onSelectDisplayMode: (AndroidAutoDisplayMode) -> Unit,
    onCyclePreset: () -> Unit,
    onSetDialSlot: (Int, String) -> Unit,
    onSetStripSlot: (Int, String) -> Unit,
    onTogglePinAuto: (String) -> Unit,
    onScanDtcs: () -> Unit,
    onStartSimulation: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isEng = uiState.settings.isEnglish
    val l10nCtx = l10nContext()
    var selectedTab by remember { mutableIntStateOf(0) }
    var detailSensorId by remember { mutableStateOf<String?>(null) }
    var editingTarget by remember { mutableStateOf<CockpitSlotTarget?>(null) }

    val isFullscreenMode = com.example.BuildConfig.ALLOW_AA_NAVIGATION_SURFACE &&
        uiState.settings.effectiveAndroidAutoDisplayMode == AndroidAutoDisplayMode.FULLSCREEN_COCKPIT

    val activeTarget = editingTarget
    if (activeTarget != null) {
        val currentId = when (activeTarget) {
            is CockpitSlotTarget.Dial -> uiState.settings.fullscreenDialSensorIds.getOrNull(activeTarget.index)
            is CockpitSlotTarget.Strip -> uiState.settings.fullscreenStripSensorIds.getOrNull(activeTarget.index)
        }
        SensorSlotPickerDialog(
            title = l10nStr(R.string.cockpit_assign_title, activeTarget.label),
            sensors = uiState.sensors,
            selectedSensorId = currentId,
            useFahrenheit = uiState.settings.useFahrenheit,
            pressureUnit = uiState.settings.pressureUnit,
            isEnglish = isEng,
            onSelectSensor = { chosen ->
                when (activeTarget) {
                    is CockpitSlotTarget.Dial -> onSetDialSlot(activeTarget.index, chosen.id)
                    is CockpitSlotTarget.Strip -> onSetStripSlot(activeTarget.index, chosen.id)
                }
                editingTarget = null
            },
            onDismiss = { editingTarget = null }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 16.dp)
                .testTag("aa_preview_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF07090D),
            border = BorderStroke(1.5.dp, CockpitBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Dialog title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = l10nStr(R.string.aa_preview_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isFullscreenMode) {
                                    l10nStr(R.string.aa_preview_sub_full)
                                } else {
                                    l10nStr(R.string.aa_preview_sub_grid)
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_aa_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = l10nStr(R.string.cd_close_preview),
                            tint = TextSecondary
                        )
                    }
                }

                if (com.example.BuildConfig.ALLOW_AA_NAVIGATION_SURFACE) {
                    // Mode Switch inside Head Unit Preview (GitHub Edition only)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChipButton(
                            label = l10nStr(R.string.aa_preview_chip_full),
                            icon = Icons.Filled.Fullscreen,
                            selected = isFullscreenMode,
                            onClick = { onSelectDisplayMode(AndroidAutoDisplayMode.FULLSCREEN_COCKPIT) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("aa_preview_mode_fullscreen")
                        )
                        FilterChipButton(
                            label = l10nStr(R.string.aa_preview_chip_grid),
                            icon = Icons.Filled.GridView,
                            selected = !isFullscreenMode,
                            onClick = { onSelectDisplayMode(AndroidAutoDisplayMode.STANDARD_GRID) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("aa_preview_mode_grid")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isFullscreenMode) {
                    // Simulated Wide Head Unit Full-Screen NavigationTemplate + Right AA Dock
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(236.dp)
                            .testTag("aa_fullscreen_preview_bezel"),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF04060A),
                        border = BorderStroke(2.dp, Color(0xFF2B3445))
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // Main Fullscreen Car App Surface
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                val dialSensors = resolveDialSensors(uiState)
                                val stripSensors = resolveStripSensors(uiState)

                                val dialFractions = dialSensors.map { sensor ->
                                    val spec = AnalogDialScale.buildSpec(
                                        sensor = sensor,
                                        reading = uiState.readings[sensor.id],
                                        useFahrenheit = uiState.settings.useFahrenheit,
                                        pressureUnit = uiState.settings.pressureUnit,
                                        isEnglish = isEng
                                    )
                                    val animated by animateFloatAsState(
                                        targetValue = spec.needleFraction,
                                        animationSpec = spring(stiffness = 280f),
                                        label = "preview_needle_${sensor.id}"
                                    )
                                    sensor.id to animated
                                }.toMap()

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val rpmVal = uiState.readings[ObdPid.ENGINE_RPM.modeAndPid]?.value ?: 0f
                                    val shiftActive = uiState.settings.shiftLightEnabled && rpmVal >= uiState.settings.shiftLightRpm
                                    val frame = CockpitRenderFrame(
                                        dialSensors = dialSensors,
                                        stripSensors = stripSensors,
                                        readings = uiState.readings,
                                        dialNeedleOverrides = dialFractions,
                                        connectionState = uiState.connectionState,
                                        useFahrenheit = uiState.settings.useFahrenheit,
                                        pressureUnit = uiState.settings.pressureUnit,
                                        isEnglish = isEng,
                                        dialTheme = uiState.settings.dialTheme,
                                        shiftLightActive = shiftActive
                                    )
                                    drawIntoCanvas { canvas ->
                                        AnalogCockpitRenderer.render(
                                            canvas = canvas.nativeCanvas,
                                            width = size.width,
                                            height = size.height,
                                            visibleArea = null,
                                            frame = frame
                                        )
                                    }
                                }

                                // Transparent clickable overlays over Dials and Bottom Strip so user can swap gauges directly on the preview
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .weight(0.78f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                    ) {
                                        val dialLabels = listOf(
                                            l10nStr(R.string.dial_left_full),
                                            l10nStr(R.string.dial_center_full),
                                            l10nStr(R.string.dial_right_full)
                                        )
                                        dialLabels.forEachIndexed { idx, label ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        editingTarget = CockpitSlotTarget.Dial(idx, label)
                                                    }
                                                    .testTag("preview_dial_tap_$idx")
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .weight(0.22f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                    ) {
                                        for (idx in 0 until 4) {
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        editingTarget = CockpitSlotTarget.Strip(
                                                            idx,
                                                            l10nCtx.getString(R.string.strip_slot_full, idx + 1)
                                                        )
                                                    }
                                                    .testTag("preview_strip_tap_$idx")
                                            )
                                        }
                                    }
                                }

                                // Top-right ActionStrip overlay (Preset + "Меню"/"Menu" + "Выход"/"Exit") matching AA NavigationTemplate
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        onClick = onCyclePreset,
                                        shape = RoundedCornerShape(16.dp),
                                        color = TelemetryCyan.copy(alpha = 0.22f),
                                        border = BorderStroke(1.dp, TelemetryCyan),
                                        modifier = Modifier.testTag("aa_preview_cycle_preset_button")
                                    ) {
                                        Text(
                                            text = uiState.settings.activePreset.localizedName(isEng),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TelemetryCyan,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = { onSelectDisplayMode(AndroidAutoDisplayMode.STANDARD_GRID) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF282E38),
                                        border = BorderStroke(1.dp, Color(0xFF3E4756))
                                    ) {
                                        Text(
                                            text = l10nStr(R.string.aa_btn_menu),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    Surface(
                                        onClick = onDismiss,
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color(0xFF282E38),
                                        border = BorderStroke(1.dp, Color(0xFF3E4756))
                                    ) {
                                        Text(
                                            text = l10nStr(R.string.aa_btn_exit),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // Right-side Android Auto Dock (matching photo)
                            Column(
                                modifier = Modifier
                                    .width(44.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF0A0C10))
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "07:40",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.obd_app_icon),
                                        contentDescription = l10nStr(R.string.app_name),
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = null,
                                        tint = NominalEmerald,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Apps,
                                    contentDescription = l10nStr(R.string.cd_aa_menu),
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Simulated Android Auto Coolwalk Tab/Grid Head Unit Bezel
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(440.dp),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF10131A),
                        border = BorderStroke(2.dp, Color(0xFF2B3445))
                    ) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .width(52.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF0A0C10))
                                    .padding(vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "12:45",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.Bluetooth,
                                        contentDescription = null,
                                        tint = if (uiState.connectionState.isActiveTelemetry) TelemetryCyan else TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                Image(
                                    painter = painterResource(id = R.drawable.obd_app_icon),
                                    contentDescription = l10nStr(R.string.app_name),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                )

                                Icon(
                                    imageVector = Icons.Filled.Apps,
                                    contentDescription = l10nStr(R.string.cd_aa_menu),
                                    tint = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF141821))
                            ) {
                                val currentDetailSensor = detailSensorId?.let { id ->
                                    uiState.sensors.find { it.id == id }
                                }
                                if (currentDetailSensor != null) {
                                    CarDetailPanePreview(
                                        sensor = currentDetailSensor,
                                        uiState = uiState,
                                        onBack = { detailSensorId = null },
                                        onTogglePin = { onTogglePinAuto(currentDetailSensor.id) }
                                    )
                                } else {
                                    CarTabHeaderBar(
                                        selectedTab = selectedTab,
                                        onSelectTab = { selectedTab = it }
                                    )
                                    HorizontalDivider(color = Color(0xFF232B3A))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    ) {
                                        when (selectedTab) {
                                            0 -> CarGaugesGridPreview(
                                                uiState = uiState,
                                                onSelectSensor = { detailSensorId = it.id }
                                            )
                                            1 -> CarAllSensorsListPreview(
                                                uiState = uiState,
                                                onSelectSensor = { detailSensorId = it.id }
                                            )
                                            2 -> CarDtcListPreview(
                                                uiState = uiState,
                                                onScanDtcs = onScanDtcs
                                            )
                                            else -> CarConnectionPanePreview(
                                                uiState = uiState,
                                                onStartSimulation = onStartSimulation,
                                                onDisconnect = onDisconnect
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.connectionState.isActiveTelemetry) {
                            l10nStr(R.string.aa_preview_live_note)
                        } else {
                            l10nStr(R.string.aa_preview_sim_note)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (!uiState.connectionState.isActiveTelemetry) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onStartSimulation,
                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber),
                            modifier = Modifier.testTag("aa_preview_sim_button")
                        ) {
                            Text(l10nStr(R.string.action_sim_mode), color = CarbonObsidian)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChipButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) TelemetryCyan.copy(alpha = 0.20f) else Color(0xFF151A23),
        border = BorderStroke(1.dp, if (selected) TelemetryCyan else CockpitBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) TelemetryCyan else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) TelemetryCyan else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun resolveDialSensors(uiState: ObdUiState): List<TelemetrySensor> {
    val defaults = listOf(
        ObdPid.ENGINE_RPM.modeAndPid,
        ObdPid.VEHICLE_SPEED.modeAndPid,
        ObdPid.COOLANT_TEMP.modeAndPid
    )
    return (0 until 3).map { idx ->
        val id = uiState.settings.fullscreenDialSensorIds.getOrNull(idx) ?: defaults[idx]
        uiState.sensors.find { it.id == id }
            ?: uiState.sensors.find { it.id == defaults[idx] }
            ?: ObdPid.entries[idx].toTelemetrySensor()
    }
}

private fun resolveStripSensors(uiState: ObdUiState): List<TelemetrySensor> {
    val defaults = listOf(
        ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid,
        ObdPid.ENGINE_LOAD.modeAndPid,
        ObdPid.OIL_TEMP.modeAndPid,
        ObdPid.INTAKE_MAP.modeAndPid
    )
    return (0 until 4).map { idx ->
        val id = uiState.settings.fullscreenStripSensorIds.getOrNull(idx) ?: defaults[idx]
        uiState.sensors.find { it.id == id }
            ?: uiState.sensors.find { it.id == defaults[idx] }
            ?: ObdPid.entries[(idx + 3) % ObdPid.entries.size].toTelemetrySensor()
    }
}

private data class CarTabItem(val title: String, val icon: ImageVector)

@Composable
private fun CarTabHeaderBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        CarTabItem(l10nStr(R.string.nav_gauges), Icons.Filled.Speed),
        CarTabItem(l10nStr(R.string.nav_sensors), Icons.Filled.FormatListBulleted),
        CarTabItem(l10nStr(R.string.nav_diagnostics), Icons.Filled.WarningAmber),
        CarTabItem(l10nStr(R.string.nav_obd2), Icons.Filled.Bluetooth)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF191E29))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedTab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelectTab(index) }
                    .testTag("aa_tab_$index"),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) TelemetryCyan.copy(alpha = 0.22f) else Color.Transparent
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = if (selected) TelemetryCyan else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = tab.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) TelemetryCyan else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CarGaugesGridPreview(
    uiState: ObdUiState,
    onSelectSensor: (TelemetrySensor) -> Unit
) {
    val isEng = uiState.settings.isEnglish
    val pinnedSensors = uiState.settings.androidAutoSensorIds
        .mapNotNull { id -> uiState.sensors.find { it.id == id } }
        .take(6)
        .ifEmpty { uiState.sensors.take(6) }
    val rows = pinnedSensors.chunked(2)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (sensor in pair) {
                    val reading = uiState.readings[sensor.id]
                    val bitmap = remember(
                        sensor.id,
                        reading?.value,
                        reading?.alertLevel,
                        uiState.settings.useFahrenheit,
                        uiState.settings.pressureUnit,
                        isEng
                    ) {
                        CarGaugeBitmapFactory.createGaugeBitmap(
                            sensor = sensor,
                            reading = reading,
                            useFahrenheit = uiState.settings.useFahrenheit,
                            pressureUnit = uiState.settings.pressureUnit,
                            sizePx = 180,
                            isEnglish = isEng
                        )
                    }
                    val valueStr = reading?.let {
                        "${sensor.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)} ${sensor.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)}"
                    } ?: l10nStr(R.string.label_no_data)

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectSensor(sensor) }
                            .testTag("aa_grid_item_${sensor.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1D2330),
                        border = BorderStroke(1.dp, Color(0xFF2B3548))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = sensor.localizedShortName(isEng),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = sensor.localizedShortName(isEng),
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = valueStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CarAllSensorsListPreview(
    uiState: ObdUiState,
    onSelectSensor: (TelemetrySensor) -> Unit
) {
    val isEng = uiState.settings.isEnglish
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val orderedCategories = listOf(SensorCategory.CUSTOM) +
            SensorCategory.entries.filter { it != SensorCategory.CUSTOM }
        for (category in orderedCategories) {
            val categorySensors = uiState.sensors.filter { it.category == category }
            if (categorySensors.isEmpty()) continue
            item(key = "header_${category.name}") {
                Text(
                    text = category.localizedName(isEng),
                    style = MaterialTheme.typography.labelLarge,
                    color = TelemetryCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(categorySensors, key = { it.id }) { sensor ->
                val reading = uiState.readings[sensor.id]
                val valStr = reading?.let {
                    "${sensor.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)} ${sensor.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)}"
                } ?: "--"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSensor(sensor) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1D2330)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sensor.localizedDisplayName(isEng),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PID ${sensor.modeAndPid}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = valStr,
                            style = MaterialTheme.typography.titleMedium,
                            color = when (reading?.alertLevel) {
                                AlertLevel.CRITICAL -> RedlineCrimson
                                AlertLevel.WARNING -> MotorsportAmber
                                else -> TelemetryCyan
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarDtcListPreview(
    uiState: ObdUiState,
    onScanDtcs: () -> Unit
) {
    val isEng = uiState.settings.isEnglish
    val dtc = uiState.dtcState
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1D2330)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (dtc.milOn) l10nStr(R.string.mil_active) else l10nStr(R.string.mil_normal),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dtc.milOn) RedlineCrimson else NominalEmerald
                    )
                    Text(
                        text = dtc.statusMessage ?: l10nStr(R.string.dtc_scan_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }
        item {
            Button(
                onClick = onScanDtcs,
                enabled = uiState.connectionState.isActiveTelemetry && !dtc.isScanning,
                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(l10nStr(R.string.car_dtc_read_title), color = CarbonObsidian)
            }
        }
        items(dtc.codes, key = { it.code }) { code ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1D2330)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "${code.code} • ${code.severity.localizedLabel(isEng)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MotorsportAmber
                    )
                    Text(
                        text = code.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CarConnectionPanePreview(
    uiState: ObdUiState,
    onStartSimulation: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isEng = uiState.settings.isEnglish
    val numLocale = if (isEng) Locale.US else Locale.forLanguageTag("ru-RU")
    val conn = uiState.connectionState
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1D2330)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val title = when (conn) {
                        is ObdConnectionState.Connected -> l10nStr(R.string.banner_connected_title, conn.deviceName)
                        is ObdConnectionState.Simulation -> l10nStr(R.string.banner_sim_title)
                        is ObdConnectionState.Connecting -> l10nStr(R.string.banner_connecting_title, conn.deviceName)
                        is ObdConnectionState.Error -> l10nStr(R.string.banner_error_title)
                        is ObdConnectionState.Disconnected -> l10nStr(R.string.banner_disconnected_title)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = when (conn) {
                            is ObdConnectionState.Connected -> l10nStr(
                                R.string.car_conn_proto,
                                conn.protocol,
                                String.format(numLocale, "%.1f", conn.pollingHz)
                            )
                            is ObdConnectionState.Simulation -> l10nStr(
                                R.string.car_conn_sim_sub,
                                String.format(numLocale, "%.1f", conn.pollingHz)
                            )
                            else -> l10nStr(R.string.car_conn_disconn_sub)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1D2330)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = l10nStr(R.string.car_sensors_count, uiState.settings.androidAutoSensorIds.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = uiState.settings.androidAutoSensorIds.joinToString(", ") { id ->
                            uiState.sensors.find { it.id == id }?.localizedShortName(isEng) ?: id
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TelemetryCyan
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (conn.isActiveTelemetry) {
                OutlinedButton(
                    onClick = onDisconnect,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, TelemetryCyan)
                ) {
                    Text(l10nStr(R.string.action_disconnect), color = TelemetryCyan)
                }
            } else {
                Button(
                    onClick = onStartSimulation,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                ) {
                    Text(l10nStr(R.string.action_sim_mode), color = CarbonObsidian)
                }
            }
        }
    }
}

@Composable
private fun CarDetailPanePreview(
    sensor: TelemetrySensor,
    uiState: ObdUiState,
    onBack: () -> Unit,
    onTogglePin: () -> Unit
) {
    val isEng = uiState.settings.isEnglish
    val reading = uiState.readings[sensor.id]
    val bitmap = remember(
        sensor.id,
        reading?.value,
        uiState.settings.useFahrenheit,
        uiState.settings.pressureUnit,
        isEng
    ) {
        CarGaugeBitmapFactory.createGaugeBitmap(
            sensor = sensor,
            reading = reading,
            useFahrenheit = uiState.settings.useFahrenheit,
            pressureUnit = uiState.settings.pressureUnit,
            sizePx = 220,
            isEnglish = isEng
        )
    }
    val unit = sensor.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)
    val isPinned = sensor.id in uiState.settings.androidAutoSensorIds

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = l10nStr(R.string.action_back),
                    tint = Color.White
                )
            }
            Text(
                text = sensor.localizedDisplayName(isEng),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = sensor.localizedDisplayName(isEng),
                modifier = Modifier.size(110.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cur = reading?.let {
                    "${sensor.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)} $unit"
                } ?: l10nStr(R.string.label_waiting_data)
                Text(
                    text = l10nStr(R.string.aa_current_short, cur),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                val minMax = reading?.let {
                    val minStr = sensor.formatValue(it.minValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)
                    val maxStr = sensor.formatValue(it.maxValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)
                    l10nStr(R.string.car_detail_minmax, minStr, unit, maxStr)
                } ?: "PID ${sensor.modeAndPid}"
                Text(
                    text = minMax,
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
                onClick = onTogglePin,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPinned) Color(0xFF283247) else TelemetryCyan
                )
            ) {
                Text(
                    text = if (isPinned) l10nStr(R.string.aa_pin_remove) else l10nStr(R.string.aa_pin_add),
                    color = if (isPinned) Color.White else CarbonObsidian
                )
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Text(l10nStr(R.string.action_back), color = Color.White)
            }
        }
    }
}
