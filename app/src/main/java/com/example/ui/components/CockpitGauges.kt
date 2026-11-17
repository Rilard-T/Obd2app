package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdConnectionState
import com.example.obd.model.ObdPid
import com.example.obd.model.PressureUnit
import com.example.obd.model.SensorReading
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ConnectionStatusBanner(
    connectionState: ObdConnectionState,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (bgColor, accentColor, title, subtitle) = when (connectionState) {
        is ObdConnectionState.Connected -> Quadruple(
            CockpitSurfaceElevated,
            NominalEmerald,
            "Подключён: ${connectionState.deviceName}",
            "${connectionState.protocol} • ${String.format("%.1f", connectionState.pollingHz)} Гц • Android Auto готов"
        )
        is ObdConnectionState.Simulation -> Quadruple(
            MotorsportAmber.copy(alpha = 0.14f),
            MotorsportAmber,
            "РЕЖИМ СИМУЛЯЦИИ (без OBD2)",
            "Тестовые данные транслируются на телефон и Android Auto (${String.format("%.1f", connectionState.pollingHz)} Гц)"
        )
        is ObdConnectionState.Connecting -> Quadruple(
            TelemetryCyan.copy(alpha = 0.12f),
            TelemetryCyan,
            "Подключение: ${connectionState.deviceName}",
            connectionState.step
        )
        is ObdConnectionState.Error -> Quadruple(
            RedlineCrimson.copy(alpha = 0.14f),
            RedlineCrimson,
            "Ошибка подключения OBD2",
            connectionState.message
        )
        is ObdConnectionState.Disconnected -> Quadruple(
            CockpitSurface,
            TextSecondary,
            "Адаптер OBD2 не подключён",
            "Подключите ELM327 по Bluetooth или запустите тест-режим"
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onActionClick)
            .testTag("connection_status_banner"),
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (connectionState) {
                    is ObdConnectionState.Simulation -> Icons.Filled.Science
                    is ObdConnectionState.Error -> Icons.Filled.WarningAmber
                    else -> Icons.Filled.DirectionsCar
                },
                contentDescription = "Статус подключения",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ArcGauge(
    pid: ObdPid,
    reading: SensorReading?,
    useFahrenheit: Boolean,
    pressureUnit: PressureUnit,
    modifier: Modifier = Modifier
) {
    val targetFraction = if (reading != null) {
        ((reading.value - pid.minRange) / (pid.maxRange - pid.minRange)).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = spring(stiffness = 380f),
        label = "gauge_fraction"
    )

    val alertColor = when (reading?.alertLevel) {
        AlertLevel.CRITICAL -> RedlineCrimson
        AlertLevel.WARNING -> MotorsportAmber
        else -> TelemetryCyan
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = size.minDimension * 0.085f
            val inset = strokePx * 0.8f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)

            // Outer tick marks
            val radius = (size.minDimension - inset * 2f) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            for (i in 0..24) {
                val angleDeg = 135.0 + (270.0 * i / 24.0)
                val rad = angleDeg * PI / 180.0
                val innerR = radius * if (i % 4 == 0) 0.78f else 0.84f
                val outerR = radius * 0.90f
                val start = Offset(
                    center.x + (innerR * cos(rad)).toFloat(),
                    center.y + (innerR * sin(rad)).toFloat()
                )
                val end = Offset(
                    center.x + (outerR * cos(rad)).toFloat(),
                    center.y + (outerR * sin(rad)).toFloat()
                )
                drawLine(
                    color = if (i >= 20) RedlineCrimson.copy(alpha = 0.55f) else CockpitBorder,
                    start = start,
                    end = end,
                    strokeWidth = if (i % 4 == 0) 3f else 1.5f,
                    cap = StrokeCap.Round
                )
            }

            // Track arc
            drawArc(
                color = CockpitBorder.copy(alpha = 0.65f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Active progress arc
            if (animatedFraction > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to TelemetryCyan,
                        0.65f to alertColor,
                        1.0f to RedlineCrimson
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = reading?.let {
                    pid.formatValue(it.value, useFahrenheit, pressureUnit)
                } ?: "--",
                style = MaterialTheme.typography.displayMedium,
                color = if (reading?.alertLevel == AlertLevel.CRITICAL) RedlineCrimson else MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = pid.displayUnit(useFahrenheit, pressureUnit),
                style = MaterialTheme.typography.labelMedium,
                color = alertColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = pid.shortName,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SensorTileCard(
    pid: ObdPid,
    reading: SensorReading?,
    isPinnedToCar: Boolean,
    useFahrenheit: Boolean,
    pressureUnit: PressureUnit,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (reading?.alertLevel) {
        AlertLevel.CRITICAL -> RedlineCrimson
        AlertLevel.WARNING -> MotorsportAmber
        else -> TelemetryCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sensor_tile_${pid.pidHex}"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                reading?.alertLevel == AlertLevel.CRITICAL -> RedlineCrimson.copy(alpha = 0.7f)
                reading?.alertLevel == AlertLevel.WARNING -> MotorsportAmber.copy(alpha = 0.6f)
                isPinnedToCar -> TelemetryCyan.copy(alpha = 0.35f)
                else -> CockpitBorder
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pid.shortName,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("pin_auto_${pid.pidHex}")
                ) {
                    Icon(
                        imageVector = if (isPinnedToCar) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinnedToCar) "Закреплено на экране Android Auto" else "Добавить на экран Android Auto",
                        tint = if (isPinnedToCar) TelemetryCyan else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = reading?.let {
                        pid.formatValue(it.value, useFahrenheit, pressureUnit)
                    } ?: "--",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (reading?.alertLevel == AlertLevel.CRITICAL) RedlineCrimson else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = pid.displayUnit(useFahrenheit, pressureUnit),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            MiniSparkline(
                values = reading?.history.orEmpty(),
                minRange = pid.minRange,
                maxRange = pid.maxRange,
                lineColor = accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val minText = reading?.let { pid.formatValue(it.minValue, useFahrenheit, pressureUnit) } ?: "--"
                val maxText = reading?.let { pid.formatValue(it.maxValue, useFahrenheit, pressureUnit) } ?: "--"
                Text(
                    text = "min $minText",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
                Text(
                    text = "max $maxText",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun MiniSparkline(
    values: List<Float>,
    minRange: Float,
    maxRange: Float,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) {
            drawLine(
                color = CockpitBorder,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2f
            )
            return@Canvas
        }

        val span = (maxRange - minRange).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        values.forEachIndexed { index, v ->
            val x = index * stepX
            val normalized = ((v - minRange) / span).coerceIn(0f, 1f)
            val y = size.height - (normalized * (size.height - 4f)) - 2f
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun TelemetryHistoryChart(
    pid: ObdPid,
    reading: SensorReading?,
    useFahrenheit: Boolean,
    pressureUnit: PressureUnit,
    modifier: Modifier = Modifier
) {
    val values = reading?.history.orEmpty()
    val accent = when (reading?.alertLevel) {
        AlertLevel.CRITICAL -> RedlineCrimson
        AlertLevel.WARNING -> MotorsportAmber
        else -> TelemetryCyan
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = pid.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "PID ${pid.modeAndPid} • ${pid.category.displayName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Text(
                    text = reading?.let {
                        "${pid.formatValue(it.value, useFahrenheit, pressureUnit)} ${pid.displayUnit(useFahrenheit, pressureUnit)}"
                    } ?: "--",
                    style = MaterialTheme.typography.headlineMedium,
                    color = accent
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                // Horizontal grid lines
                for (i in 0..3) {
                    val y = size.height * i / 3f
                    drawLine(
                        color = CockpitBorder.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.5f
                    )
                }

                if (values.size < 2) return@Canvas

                val localMin = minOf(pid.minRange, values.minOrNull() ?: pid.minRange)
                val localMax = maxOf(pid.maxRange, values.maxOrNull() ?: pid.maxRange)
                val span = (localMax - localMin).coerceAtLeast(1f)
                val stepX = size.width / (values.size - 1)

                val linePath = Path()
                val fillPath = Path()

                values.forEachIndexed { idx, value ->
                    val x = idx * stepX
                    val norm = ((value - localMin) / span).coerceIn(0f, 1f)
                    val y = size.height - norm * (size.height - 12f) - 6f
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.35f), Color.Transparent)
                    )
                )
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )
            }
        }
    }
}
