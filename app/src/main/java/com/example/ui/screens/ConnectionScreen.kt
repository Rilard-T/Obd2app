package com.example.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AppLanguage
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.TtsVoiceOption
import com.example.ui.ObdUiState
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
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectionScreen(
    uiState: ObdUiState,
    requiredPermissions: Array<String>,
    onPermissionsResult: () -> Unit,
    onRefreshDevices: () -> Unit,
    onStartBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onConnectDevice: (BluetoothDeviceItem) -> Unit,
    onStartSimulation: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleShowAllBt: () -> Unit,
    onToggleAndroidAutoSensor: (String) -> Unit,
    onSelectDisplayMode: (com.example.obd.data.AndroidAutoDisplayMode) -> Unit,
    onSelectPreset: (String) -> Unit,
    onRenamePreset: (String, String) -> Unit,
    onSetDialSlot: (Int, String) -> Unit,
    onSetStripSlot: (Int, String) -> Unit,
    onResetFullscreenDefaults: () -> Unit,
    onSetAutoConnect: (Boolean) -> Unit,
    onSetVoiceAlerts: (Boolean) -> Unit,
    onSetAaPopupAlerts: (Boolean) -> Unit,
    onSetTtsVolume: (Float) -> Unit,
    onSetTtsSpeechRate: (Float) -> Unit,
    onSetTtsVoiceName: (String?) -> Unit,
    onPreloadTtsVoices: () -> Unit,
    onTriggerTestAlert: () -> Unit,
    onRunAdapterBenchmark: () -> Unit,
    onOpenAndroidAutoPreview: () -> Unit,
    onOpenErrorLogs: () -> Unit,
    onSetAppLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEng = uiState.settings.isEnglish
    val numLocale = if (isEng) Locale.US else Locale.forLanguageTag("ru-RU")
    var showVoicePickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.settings.voiceAlertsEnabled) {
        if (uiState.settings.voiceAlertsEnabled) {
            onPreloadTtsVoices()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onRefreshDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsResult()
    }

    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshDevices()
    }

    val pairedVisible = if (uiState.showAllBtDevices) {
        uiState.pairedDevices
    } else {
        uiState.pairedDevices.filter { it.isLikelyObd }.ifEmpty { uiState.pairedDevices }
    }

    val bleVisible = if (uiState.showAllBtDevices) {
        uiState.bleDiscoveredDevices
    } else {
        uiState.bleDiscoveredDevices.filter { it.isLikelyObd }.ifEmpty { uiState.bleDiscoveredDevices }
    }

    if (showVoicePickerDialog) {
        TtsVoicePickerDialog(
            voices = uiState.availableTtsVoices,
            selectedVoiceName = uiState.settings.ttsVoiceName,
            onSelectVoice = { name ->
                onSetTtsVoiceName(name)
            },
            onRefreshVoices = onPreloadTtsVoices,
            onTestVoice = onTriggerTestAlert,
            onDismiss = { showVoicePickerDialog = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("connection_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Android Auto Hero Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAndroidAutoPreview)
                    .testTag("connection_aa_preview_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_cockpit_banner),
                        contentDescription = l10nStr(R.string.cd_aa_banner),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        CarbonObsidian.copy(alpha = 0.25f),
                                        CarbonObsidian.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = l10nStr(R.string.aa_active_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TelemetryCyan
                                )
                            }
                            Text(
                                text = l10nStr(R.string.aa_show_screen),
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan
                            )
                        }
                        Text(
                            text = l10nStr(R.string.aa_banner_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Active connection / Simulation control
        if (uiState.connectionState.isActiveTelemetry || uiState.connectionState is ObdConnectionState.Connecting) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                    border = BorderStroke(1.dp, NominalEmerald.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val title = when (val s = uiState.connectionState) {
                                is ObdConnectionState.Connected -> l10nStr(R.string.banner_connected_title, s.deviceName)
                                is ObdConnectionState.Simulation -> l10nStr(R.string.conn_sim_active)
                                is ObdConnectionState.Connecting -> l10nStr(R.string.banner_connecting_title, s.deviceName)
                                else -> ""
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val sub = when (val s = uiState.connectionState) {
                                is ObdConnectionState.Connected -> "${s.protocol} • ${s.transport.label}"
                                is ObdConnectionState.Simulation -> l10nStr(R.string.conn_sim_sub)
                                is ObdConnectionState.Connecting -> s.step
                                else -> ""
                            }
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.testTag("disconnect_obd_button"),
                            border = BorderStroke(1.dp, TelemetryCyan)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LinkOff,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.action_disconnect), color = TelemetryCyan)
                        }
                    }
                }
            }
        }

        // Runtime Bluetooth permission check
        if (!uiState.hasBluetoothPermissions) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MotorsportAmber.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = l10nStr(R.string.bt_perm_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MotorsportAmber
                        )
                        Text(
                            text = l10nStr(R.string.bt_perm_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = { permissionLauncher.launch(requiredPermissions) },
                            modifier = Modifier.testTag("request_bt_permission_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber)
                        ) {
                            Text(l10nStr(R.string.bt_perm_btn), color = CarbonObsidian)
                        }
                    }
                }
            }
        } else if (!uiState.isBluetoothEnabled) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MotorsportAmber.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = l10nStr(R.string.bt_disabled_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MotorsportAmber
                        )
                        Text(
                            text = l10nStr(R.string.bt_disabled_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = {
                                enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            },
                            modifier = Modifier.testTag("enable_bt_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber)
                        ) {
                            Text(l10nStr(R.string.bt_enable_btn), color = CarbonObsidian)
                        }
                    }
                }
            }
        }

        // Language Selector Card (Instant Switch RU / EN / Auto)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language_selector_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Language,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.lang_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = l10nStr(R.string.lang_card_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    val langOptions = listOf(
                        AppLanguage.SYSTEM to l10nStr(R.string.lang_option_system),
                        AppLanguage.RU to l10nStr(R.string.lang_option_ru),
                        AppLanguage.EN to l10nStr(R.string.lang_option_en)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        langOptions.forEach { (lang, label) ->
                            val selected = uiState.settings.appLanguage == lang
                            FilterChip(
                                selected = selected,
                                onClick = { onSetAppLanguage(lang) },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = TelemetryCyan
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lang_chip_${lang.name}")
                            )
                        }
                    }
                }
            }
        }

        // Bluetooth OBD2 Adapter Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = l10nStr(R.string.bt_adapters_header),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = uiState.showAllBtDevices,
                        onClick = onToggleShowAllBt,
                        label = { Text(l10nStr(R.string.bt_all_chip)) },
                        modifier = Modifier.testTag("show_all_bt_chip")
                    )
                    IconButton(
                        onClick = onRefreshDevices,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_bt_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = l10nStr(R.string.cd_refresh_bt),
                            tint = TelemetryCyan
                        )
                    }
                }
            }
        }

        // BLE Scan button
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (!uiState.hasBluetoothPermissions) {
                            permissionLauncher.launch(requiredPermissions)
                        } else if (uiState.isScanningBle) {
                            onStopBleScan()
                        } else {
                            onStartBleScan()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_ble_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                ) {
                    if (uiState.isScanningBle) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = CarbonObsidian
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(l10nStr(R.string.btn_stop_scan), color = CarbonObsidian)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.BluetoothSearching,
                            contentDescription = null,
                            tint = CarbonObsidian,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.btn_scan_elm), color = CarbonObsidian)
                    }
                }

                OutlinedButton(
                    onClick = onStartSimulation,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("start_simulation_button"),
                    border = BorderStroke(1.dp, MotorsportAmber)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Science,
                        contentDescription = null,
                        tint = MotorsportAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(l10nStr(R.string.action_sim_mode), color = MotorsportAmber)
                }
            }
        }

        val discoveredOnly = bleVisible.filter { d -> pairedVisible.none { it.address == d.address } }

        if (pairedVisible.isNotEmpty()) {
            item {
                Text(
                    text = l10nStr(R.string.bt_paired_header, pairedVisible.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = TelemetryCyan
                )
            }
            items(pairedVisible, key = { "paired_${it.address}" }) { device ->
                DeviceRowCard(
                    device = device,
                    isConnected = (uiState.connectionState as? ObdConnectionState.Connected)?.address == device.address,
                    onClick = { onConnectDevice(device) }
                )
            }
        }

        if (discoveredOnly.isNotEmpty()) {
            item {
                Text(
                    text = l10nStr(R.string.bt_discovered_header, discoveredOnly.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = TelemetryCyan
                )
            }
            items(discoveredOnly, key = { "disc_${it.address}" }) { device ->
                DeviceRowCard(
                    device = device,
                    isConnected = (uiState.connectionState as? ObdConnectionState.Connected)?.address == device.address,
                    onClick = { onConnectDevice(device) }
                )
            }
        }

        if (pairedVisible.isEmpty() && discoveredOnly.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.img_obd_adapter),
                                contentDescription = l10nStr(R.string.cd_obd_adapter_img),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(78.dp)
                                    .clip(MaterialTheme.shapes.small)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!uiState.isBluetoothEnabled) {
                                        l10nStr(R.string.bt_disabled_title)
                                    } else {
                                        l10nStr(R.string.bt_none_paired_title)
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = l10nStr(R.string.bt_none_paired_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("open_bt_settings_button"),
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bluetooth,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.btn_open_bt_settings), color = TelemetryCyan)
                        }
                    }
                }
            }
        }

        val connError = uiState.connectionState as? ObdConnectionState.Error
        if (connError != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = RedlineCrimson.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.6f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = l10nStr(R.string.conn_error_header, connError.deviceName ?: "ELM327"),
                            style = MaterialTheme.typography.titleMedium,
                            color = RedlineCrimson
                        )
                        Text(
                            text = connError.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onOpenErrorLogs,
                            border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.7f)),
                            modifier = Modifier.testTag("connection_open_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = RedlineCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.btn_open_logs_report), color = RedlineCrimson)
                        }
                    }
                }
            }
        }

        // Android Auto Display Mode & Gauge Layout Configurator
        item {
            com.example.ui.components.CockpitLayoutConfiguratorSection(
                uiState = uiState,
                onSelectDisplayMode = onSelectDisplayMode,
                onSelectPreset = onSelectPreset,
                onRenamePreset = onRenamePreset,
                onSetDialSlot = onSetDialSlot,
                onSetStripSlot = onSetStripSlot,
                onResetFullscreenDefaults = onResetFullscreenDefaults,
                onToggleGridSensor = onToggleAndroidAutoSensor,
                onOpenPreview = onOpenAndroidAutoPreview
            )
        }

        // Auto-connect, Voice (TTS), and Android Auto Popup Alerts Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MotorsportAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.alerts_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SettingsSwitchRow(
                        title = l10nStr(R.string.auto_connect_title),
                        subtitle = l10nStr(R.string.auto_connect_sub),
                        checked = uiState.settings.autoConnectOnLaunch,
                        onCheckedChange = onSetAutoConnect,
                        tag = "auto_connect_switch"
                    )
                    SettingsSwitchRow(
                        title = l10nStr(R.string.voice_alerts_title),
                        subtitle = l10nStr(R.string.voice_alerts_sub),
                        checked = uiState.settings.voiceAlertsEnabled,
                        onCheckedChange = onSetVoiceAlerts,
                        tag = "voice_alerts_switch"
                    )

                    AnimatedVisibility(visible = uiState.settings.voiceAlertsEnabled) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, CockpitBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Volume slider
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = TelemetryCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = l10nStr(R.string.tts_volume_label),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "${(uiState.settings.ttsVolume * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TelemetryCyan
                                        )
                                    }
                                    Slider(
                                        value = uiState.settings.ttsVolume,
                                        onValueChange = onSetTtsVolume,
                                        valueRange = 0.1f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = TelemetryCyan,
                                            activeTrackColor = TelemetryCyan,
                                            inactiveTrackColor = CockpitBorder
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("tts_volume_slider")
                                    )
                                }

                                HorizontalDivider(color = CockpitBorder)

                                // Speech rate quick chips
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = l10nStr(R.string.tts_rate_label),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    val rateOptions = listOf(
                                        0.85f to l10nStr(R.string.tts_rate_slow),
                                        1.0f to l10nStr(R.string.tts_rate_normal),
                                        1.2f to l10nStr(R.string.tts_rate_fast),
                                        1.4f to "1.4x"
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        rateOptions.forEach { (rateValue, rateLabel) ->
                                            val isSelected = abs(uiState.settings.ttsSpeechRate - rateValue) < 0.05f
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSetTtsSpeechRate(rateValue) },
                                                label = { Text(rateLabel) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                                                    selectedLabelColor = TelemetryCyan
                                                ),
                                                modifier = Modifier.testTag("tts_rate_chip_${(rateValue * 100).roundToInt()}")
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = CockpitBorder)

                                // Voice picker button row
                                val activeVoiceLabel = uiState.availableTtsVoices
                                    .find { it.name == uiState.settings.ttsVoiceName }
                                    ?.label
                                    ?: l10nStr(R.string.tts_voice_system_default)

                                Surface(
                                    onClick = {
                                        onPreloadTtsVoices()
                                        showVoicePickerDialog = true
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    color = CockpitSurface,
                                    border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("tts_voice_picker_button")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = TelemetryCyan,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = l10nStr(R.string.tts_voice_picker_label),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = TextSecondary
                                                )
                                                Text(
                                                    text = activeVoiceLabel,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        Text(
                                            text = l10nStr(R.string.tts_voice_choose),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = TelemetryCyan
                                        )
                                    }
                                }
                            }
                        }
                    }

                    SettingsSwitchRow(
                        title = l10nStr(R.string.aa_popup_alerts_title),
                        subtitle = l10nStr(R.string.aa_popup_alerts_sub),
                        checked = uiState.settings.aaPopupAlertsEnabled,
                        onCheckedChange = onSetAaPopupAlerts,
                        tag = "aa_popup_alerts_switch"
                    )

                    OutlinedButton(
                        onClick = onTriggerTestAlert,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_alert_button"),
                        border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.65f))
                    ) {
                        Text(l10nStr(R.string.btn_test_alert), color = MotorsportAmber)
                    }
                }
            }
        }

        // ELM327 Adapter Speed & Clone Benchmark Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = l10nStr(R.string.bench_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = l10nStr(R.string.bench_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Button(
                        onClick = onRunAdapterBenchmark,
                        enabled = uiState.connectionState.isActiveTelemetry && !uiState.isRunningBenchmark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_elm_benchmark_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                    ) {
                        if (uiState.isRunningBenchmark) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = CarbonObsidian
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(l10nStr(R.string.bench_running), color = CarbonObsidian)
                        } else {
                            Text(
                                text = if (uiState.connectionState.isActiveTelemetry) {
                                    l10nStr(R.string.bench_run_btn)
                                } else {
                                    l10nStr(R.string.bench_need_conn_btn)
                                },
                                color = CarbonObsidian
                            )
                        }
                    }

                    val bench = uiState.benchmarkResult
                    if (bench != null) {
                        val isBadClone = (bench.qualityLabel.contains("клон", ignoreCase = true) &&
                            bench.qualityLabel.contains("Урезан", ignoreCase = true)) ||
                            (bench.qualityLabel.contains("Stripped", ignoreCase = true) ||
                                bench.qualityLabel.contains("Clone", ignoreCase = true))
                        val yes = l10nStr(R.string.bench_yes)
                        val no = l10nStr(R.string.bench_no)
                        val pidPrefix = if (isEng) "ECU PIDs" else "PID ЭБУ"

                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.35f)),
                            modifier = Modifier.testTag("elm_benchmark_result_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = l10nStr(R.string.bench_verdict, bench.qualityLabel),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isBadClone) RedlineCrimson else NominalEmerald
                                )
                                Text(
                                    text = l10nStr(R.string.bench_chip, bench.adapterVersion, bench.protocolName),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TelemetryCyan
                                )
                                Text(
                                    text = l10nStr(
                                        R.string.bench_latency,
                                        bench.avgLatencyMs,
                                        bench.minLatencyMs,
                                        bench.maxLatencyMs,
                                        String.format(numLocale, "%.1f", bench.maxPollingHz)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val atFlags = buildString {
                                    append("ATAT2: ${if (bench.supportsAdaptiveTiming) yes else no}")
                                    append(" • ")
                                    append("ATST: ${if (bench.supportsTimeoutCmd) yes else no}")
                                    bench.supportedMode01Count?.let { count ->
                                        append(" • $pidPrefix: $count")
                                    }
                                }
                                Text(
                                    text = atFlags,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = bench.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun DeviceRowCard(
    device: BluetoothDeviceItem,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("bt_device_${device.address}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) CockpitSurfaceElevated else CockpitSurface
        ),
        border = BorderStroke(
            1.dp,
            when {
                isConnected -> NominalEmerald
                device.isLikelyObd -> TelemetryCyan.copy(alpha = 0.55f)
                else -> CockpitBorder
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Bluetooth,
                contentDescription = l10nStr(R.string.cd_bt_device),
                tint = if (device.isLikelyObd) TelemetryCyan else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (device.isLikelyObd) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = TelemetryCyan.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "OBD2",
                                style = MaterialTheme.typography.labelSmall,
                                color = TelemetryCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${device.transport.label} • ${device.address}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
            Text(
                text = if (isConnected) l10nStr(R.string.bt_status_active) else l10nStr(R.string.bt_status_connect_short),
                style = MaterialTheme.typography.labelLarge,
                color = if (isConnected) NominalEmerald else TelemetryCyan
            )
        }
    }
}

@Composable
private fun TtsVoicePickerDialog(
    voices: List<TtsVoiceOption>,
    selectedVoiceName: String?,
    onSelectVoice: (String?) -> Unit,
    onRefreshVoices: () -> Unit,
    onTestVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tts_voice_picker_dialog")
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.tts_dialog_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onRefreshVoices,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tts_refresh_voices_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = l10nStr(R.string.cd_refresh_voices),
                            tint = TelemetryCyan
                        )
                    }
                }

                Text(
                    text = l10nStr(R.string.tts_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TtsVoiceOptionRow(
                        label = l10nStr(R.string.tts_voice_system_default),
                        subtitle = l10nStr(R.string.tts_voice_default_sub),
                        selected = selectedVoiceName == null,
                        onClick = { onSelectVoice(null) },
                        tag = "tts_voice_option_default"
                    )

                    if (voices.isEmpty()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, CockpitBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = l10nStr(R.string.tts_voices_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMuted,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        voices.forEach { voice ->
                            TtsVoiceOptionRow(
                                label = voice.label,
                                subtitle = voice.name,
                                selected = selectedVoiceName == voice.name,
                                onClick = { onSelectVoice(voice.name) },
                                tag = "tts_voice_option_${voice.name}"
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onTestVoice,
                        border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tts_dialog_test_button")
                    ) {
                        Text(l10nStr(R.string.action_test), color = MotorsportAmber)
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("tts_dialog_close_button")
                    ) {
                        Text(l10nStr(R.string.action_done), color = TelemetryCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun TtsVoiceOptionRow(
    label: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) TelemetryCyan.copy(alpha = 0.14f) else CockpitSurfaceElevated,
        border = BorderStroke(
            1.dp,
            if (selected) TelemetryCyan else CockpitBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = TelemetryCyan,
                    unselectedColor = TextSecondary
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) TelemetryCyan else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

