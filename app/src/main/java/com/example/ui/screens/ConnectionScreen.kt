package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.obd.model.BluetoothDeviceItem
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.ui.ObdUiState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

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
    onToggleAndroidAutoPid: (ObdPid) -> Unit,
    onOpenAndroidAutoPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onPermissionsResult()
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
                        contentDescription = "Экран автомобиля Android Auto",
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
                                    text = "Android Auto активен",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TelemetryCyan
                                )
                            }
                            Text(
                                text = "Показать экран →",
                                style = MaterialTheme.typography.labelMedium,
                                color = TelemetryCyan
                            )
                        }
                        Text(
                            text = "Подключите телефон к магнитоле или нажмите, чтобы посмотреть интерфейс автомобиля.",
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
                                is ObdConnectionState.Connected -> "Подключён: ${s.deviceName}"
                                is ObdConnectionState.Simulation -> "Режим симуляции активен"
                                is ObdConnectionState.Connecting -> "Подключение: ${s.deviceName}"
                                else -> ""
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val sub = when (val s = uiState.connectionState) {
                                is ObdConnectionState.Connected -> "${s.protocol} • ${s.transport.label}"
                                is ObdConnectionState.Simulation -> "Тестовые показания всех 14 датчиков"
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
                            Text("Отключить", color = TelemetryCyan)
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
                            text = "Требуется разрешение Bluetooth",
                            style = MaterialTheme.typography.titleMedium,
                            color = MotorsportAmber
                        )
                        Text(
                            text = "Для поиска и подключения к OBD2-сканеру (ELM327 Classic или BLE) предоставьте доступ к ближайшим Bluetooth-устройствам.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Button(
                            onClick = { permissionLauncher.launch(requiredPermissions) },
                            modifier = Modifier.testTag("request_bt_permission_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber)
                        ) {
                            Text("Разрешить Bluetooth", color = CarbonObsidian)
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
                    text = "OBD2 адаптеры (Bluetooth)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = uiState.showAllBtDevices,
                        onClick = onToggleShowAllBt,
                        label = { Text("Все BT") },
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
                            contentDescription = "Обновить список устройств",
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
                        Text("Остановить BLE", color = CarbonObsidian)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.BluetoothSearching,
                            contentDescription = null,
                            tint = CarbonObsidian,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поиск BLE ELM327", color = CarbonObsidian)
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
                    Text("Тест-режим", color = MotorsportAmber)
                }
            }
        }

        val combinedDevices = (pairedVisible + bleVisible).distinctBy { it.address }

        if (combinedDevices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_obd_adapter),
                            contentDescription = "Иллюстрация адаптера OBD2",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(78.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!uiState.isBluetoothEnabled) "Bluetooth выключен" else "Адаптеры не найдены",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Для ELM327 Classic выполните сопряжение в настройках Bluetooth телефона (PIN обычно 1234 или 0000), либо нажмите «Поиск BLE ELM327».",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        } else {
            items(combinedDevices, key = { it.address }) { device ->
                DeviceRowCard(
                    device = device,
                    isConnected = (uiState.connectionState as? ObdConnectionState.Connected)?.address == device.address,
                    onClick = { onConnectDevice(device) }
                )
            }
        }

        // Android Auto 6-Sensor Grid Picker
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Выбор 6 датчиков для экрана Android Auto",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Отмеченные датчики отображаются крупными шкалами на главном экране автомобиля.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        item {
            val pidRows = ObdPid.entries.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pair in pidRows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (pid in pair) {
                            val selected = pid in uiState.settings.androidAutoPids
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleAndroidAutoPid(pid) },
                                label = { Text("${pid.shortName} (${pid.unit})") },
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
                                    .testTag("auto_pid_chip_${pid.pidHex}")
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
                contentDescription = "Bluetooth устройство",
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
                text = if (isConnected) "Активен" else "Подкл.",
                style = MaterialTheme.typography.labelLarge,
                color = if (isConnected) NominalEmerald else TelemetryCyan
            )
        }
    }
}
