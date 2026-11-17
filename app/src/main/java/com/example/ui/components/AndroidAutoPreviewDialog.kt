package com.example.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.car.CarGaugeBitmapFactory
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.SensorCategory
import com.example.ui.ObdUiState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun AndroidAutoPreviewDialog(
    uiState: ObdUiState,
    onDismiss: () -> Unit,
    onTogglePinAuto: (ObdPid) -> Unit,
    onScanDtcs: () -> Unit,
    onStartSimulation: () -> Unit,
    onDisconnect: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var detailPid by remember { mutableStateOf<ObdPid?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 20.dp)
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
                                text = "Макет экрана Android Auto",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Интерактивный предпросмотр Car App Library",
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
                            contentDescription = "Закрыть предпросмотр",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Simulated Android Auto Coolwalk Head Unit Bezel
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(470.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF10131A),
                    border = BorderStroke(2.dp, Color(0xFF2B3445))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Android Auto System Rail
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
                                contentDescription = "Иконка OBD2 Авто",
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                            )

                            Icon(
                                imageVector = Icons.Filled.Apps,
                                contentDescription = "Меню Android Auto",
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Right Car App Library Template Surface
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(Color(0xFF141821))
                        ) {
                            val currentDetail = detailPid
                            if (currentDetail != null) {
                                CarDetailPanePreview(
                                    pid = currentDetail,
                                    uiState = uiState,
                                    onBack = { detailPid = null },
                                    onTogglePin = { onTogglePinAuto(currentDetail) }
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
                                            onSelectPid = { detailPid = it }
                                        )
                                        1 -> CarAllSensorsListPreview(
                                            uiState = uiState,
                                            onSelectPid = { detailPid = it }
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

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.connectionState.isActiveTelemetry) {
                            "Данные обновляются в реальном времени"
                        } else {
                            "Совет: включите «Тест-режим», чтобы увидеть живые шкалы"
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
                            Text("Тест-режим", color = CarbonObsidian)
                        }
                    }
                }
            }
        }
    }
}

private data class CarTabItem(val title: String, val icon: ImageVector)

@Composable
private fun CarTabHeaderBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    val tabs = listOf(
        CarTabItem("Приборы", Icons.Filled.Speed),
        CarTabItem("Датчики", Icons.Filled.FormatListBulleted),
        CarTabItem("Ошибки", Icons.Filled.WarningAmber),
        CarTabItem("OBD2", Icons.Filled.Bluetooth)
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
    onSelectPid: (ObdPid) -> Unit
) {
    val pids = uiState.settings.androidAutoPids.take(6)
    val rows = pids.chunked(2)

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
                for (pid in pair) {
                    val reading = uiState.readings[pid]
                    val bitmap = remember(
                        pid,
                        reading?.value,
                        reading?.alertLevel,
                        uiState.settings.useFahrenheit,
                        uiState.settings.pressureUnit
                    ) {
                        CarGaugeBitmapFactory.createGaugeBitmap(
                            pid = pid,
                            reading = reading,
                            useFahrenheit = uiState.settings.useFahrenheit,
                            pressureUnit = uiState.settings.pressureUnit,
                            sizePx = 180
                        )
                    }
                    val valueStr = reading?.let {
                        "${pid.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} ${pid.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit)}"
                    } ?: "Нет данных"

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectPid(pid) }
                            .testTag("aa_grid_item_${pid.pidHex}"),
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
                                contentDescription = pid.shortName,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = pid.shortName,
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
    onSelectPid: (ObdPid) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (category in SensorCategory.entries) {
            item(key = "header_${category.name}") {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TelemetryCyan,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val pids = ObdPid.entries.filter { it.category == category }
            items(pids, key = { it.pidHex }) { pid ->
                val reading = uiState.readings[pid]
                val valStr = reading?.let {
                    "${pid.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} ${pid.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit)}"
                } ?: "--"
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPid(pid) },
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
                                text = pid.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "PID ${pid.modeAndPid}",
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
                        text = if (dtc.milOn) "Check Engine (MIL): АКТИВЕН" else "Check Engine (MIL): Норма",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dtc.milOn) RedlineCrimson else NominalEmerald
                    )
                    Text(
                        text = dtc.statusMessage ?: "Нажмите «Считать ошибки» для опроса ЭБУ",
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
                Text("Считать ошибки ЭБУ (Mode 03)", color = CarbonObsidian)
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
                        text = "${code.code} • ${code.severity.label}",
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
                        is ObdConnectionState.Connected -> "Подключён: ${conn.deviceName}"
                        is ObdConnectionState.Simulation -> "РЕЖИМ СИМУЛЯЦИИ (без OBD2)"
                        is ObdConnectionState.Connecting -> "Подключение: ${conn.deviceName}"
                        is ObdConnectionState.Error -> "Ошибка подключения"
                        is ObdConnectionState.Disconnected -> "Адаптер не подключён"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = when (conn) {
                            is ObdConnectionState.Connected -> "Протокол: ${conn.protocol}"
                            is ObdConnectionState.Simulation -> "Тестовые показания датчиков"
                            else -> "Подключите адаптер или включите тест-режим"
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
                        text = "Датчиков на экране: ${uiState.settings.androidAutoPids.size} из 6",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = uiState.settings.androidAutoPids.joinToString(", ") { it.shortName },
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
                    Text("Отключить", color = TelemetryCyan)
                }
            } else {
                Button(
                    onClick = onStartSimulation,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                ) {
                    Text("Тест-режим", color = CarbonObsidian)
                }
            }
        }
    }
}

@Composable
private fun CarDetailPanePreview(
    pid: ObdPid,
    uiState: ObdUiState,
    onBack: () -> Unit,
    onTogglePin: () -> Unit
) {
    val reading = uiState.readings[pid]
    val bitmap = remember(
        pid,
        reading?.value,
        uiState.settings.useFahrenheit,
        uiState.settings.pressureUnit
    ) {
        CarGaugeBitmapFactory.createGaugeBitmap(
            pid = pid,
            reading = reading,
            useFahrenheit = uiState.settings.useFahrenheit,
            pressureUnit = uiState.settings.pressureUnit,
            sizePx = 220
        )
    }
    val unit = pid.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit)
    val isPinned = pid in uiState.settings.androidAutoPids

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
                    contentDescription = "Назад",
                    tint = Color.White
                )
            }
            Text(
                text = pid.displayName,
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
                contentDescription = pid.displayName,
                modifier = Modifier.size(110.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val cur = reading?.let {
                    "${pid.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} $unit"
                } ?: "Ожидание данных..."
                Text(
                    text = "Текущее: $cur",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                val minMax = reading?.let {
                    "Мин: ${pid.formatValue(it.minValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} • Макс: ${pid.formatValue(it.maxValue, uiState.settings.useFahrenheit, uiState.settings.pressureUnit)} $unit"
                } ?: "PID ${pid.modeAndPid}"
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
                    text = if (isPinned) "Убрать с приборов" else "На приборы",
                    color = if (isPinned) Color.White else CarbonObsidian
                )
            }
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Text("Назад", color = Color.White)
            }
        }
    }
}
