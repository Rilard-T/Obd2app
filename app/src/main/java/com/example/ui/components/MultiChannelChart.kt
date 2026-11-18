package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.DataloggerFrame
import com.example.obd.model.PressureUnit
import com.example.obd.model.TelemetrySensor
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

private val CHANNEL_COLORS = listOf(
    TelemetryCyan,
    MotorsportAmber,
    NominalEmerald,
    RedlineCrimson
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiChannelChart(
    frames: List<DataloggerFrame>,
    sensors: List<TelemetrySensor>,
    useFahrenheit: Boolean,
    pressureUnit: PressureUnit,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val numLocale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")

    val availableSensors = remember(frames, sensors) {
        val keys = frames.flatMap { it.values.keys }.distinct()
        keys.mapNotNull { id -> sensors.find { it.id == id } }
    }

    val selectedIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(availableSensors) {
        if (selectedIds.isEmpty() && availableSensors.isNotEmpty()) {
            val preferred = listOf("010C", "010D", "0105", "0111")
            preferred.forEach { pid ->
                if (availableSensors.any { it.id == pid } && selectedIds.size < 4) {
                    selectedIds.add(pid)
                }
            }
            availableSensors.forEach { sensor ->
                if (sensor.id !in selectedIds && selectedIds.size < 4) {
                    selectedIds.add(sensor.id)
                }
            }
        } else {
            selectedIds.retainAll(availableSensors.map { it.id }.toSet())
        }
    }

    var scrubFraction by remember { mutableStateOf<Float?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (frames.size < 2 || availableSensors.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CarbonObsidian,
                border = BorderStroke(1.dp, CockpitBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = l10nStr(R.string.datalogger_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(14.dp)
                )
            }
            return
        }

        Text(
            text = l10nStr(R.string.datalogger_channels_hint),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            availableSensors.take(10).forEach { sensor ->
                val activeIdx = selectedIds.indexOf(sensor.id)
                val isSelected = activeIdx >= 0
                val chipColor = if (isSelected) CHANNEL_COLORS[activeIdx % CHANNEL_COLORS.size] else TextMuted
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            if (selectedIds.size > 1) selectedIds.remove(sensor.id)
                        } else if (selectedIds.size < 4) {
                            selectedIds.add(sensor.id)
                        } else {
                            selectedIds.removeAt(0)
                            selectedIds.add(sensor.id)
                        }
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(chipColor)
                            )
                        }
                    } else null,
                    label = { Text(sensor.localizedShortName(isEnglish)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipColor.copy(alpha = 0.18f),
                        selectedLabelColor = chipColor
                    ),
                    modifier = Modifier.testTag("logger_channel_chip_${sensor.id}")
                )
            }
        }

        val activeIndex = scrubFraction?.let { frac ->
            (frac * (frames.size - 1)).roundToInt().coerceIn(0, frames.size - 1)
        } ?: (frames.size - 1)

        val inspectedFrame = frames[activeIndex]
        val startTs = frames.first().timestampMs
        val elapsedSec = (inspectedFrame.timestampMs - startTs) / 1000f

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CarbonObsidian,
            border = BorderStroke(1.dp, CockpitBorder),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("multi_channel_chart_canvas")
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .pointerInput(frames.size) {
                            detectTapGestures { offset ->
                                scrubFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            }
                        }
                        .pointerInput(frames.size) {
                            detectHorizontalDragGestures(
                                onDragEnd = { },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    scrubFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val padV = 10.dp.toPx()
                    val plotH = (h - padV * 2).coerceAtLeast(1f)

                    // Grid lines at 0%, 25%, 50%, 75%, 100%
                    for (step in 0..4) {
                        val y = padV + plotH * (step / 4f)
                        drawLine(
                            color = CockpitBorder.copy(alpha = 0.55f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Plot each selected channel normalized against its sensor range or observed min..max
                    selectedIds.forEachIndexed { idx, sensorId ->
                        val color = CHANNEL_COLORS[idx % CHANNEL_COLORS.size]
                        val sensor = availableSensors.find { it.id == sensorId }
                        val rawVals = frames.mapNotNull { it.values[sensorId] }
                        val minObs = rawVals.minOrNull() ?: sensor?.minRange ?: 0f
                        val maxObs = rawVals.maxOrNull() ?: sensor?.maxRange ?: 100f
                        val minBound = sensor?.minRange?.coerceAtMost(minObs) ?: minObs
                        val maxBound = sensor?.maxRange?.coerceAtLeast(maxObs) ?: maxObs
                        val span = (maxBound - minBound).takeIf { it > 0.001f } ?: 1f

                        val path = Path()
                        var started = false
                        frames.forEachIndexed { fIdx, frame ->
                            val raw = frame.values[sensorId] ?: return@forEachIndexed
                            val norm = ((raw - minBound) / span).coerceIn(0f, 1f)
                            val x = if (frames.size <= 1) 0f else (fIdx.toFloat() / (frames.size - 1)) * w
                            val y = padV + plotH * (1f - norm)
                            if (!started) {
                                path.moveTo(x, y)
                                started = true
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        if (started) {
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Highlight point at active cursor
                        inspectedFrame.values[sensorId]?.let { cur ->
                            val norm = ((cur - minBound) / span).coerceIn(0f, 1f)
                            val cx = if (frames.size <= 1) 0f else (activeIndex.toFloat() / (frames.size - 1)) * w
                            val cy = padV + plotH * (1f - norm)
                            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(cx, cy))
                        }
                    }

                    // Crosshair vertical line
                    val cursorX = if (frames.size <= 1) 0f else (activeIndex.toFloat() / (frames.size - 1)) * w
                    drawLine(
                        color = Color.White.copy(alpha = 0.65f),
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, h),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = l10nStr(
                            R.string.datalogger_scrub_time,
                            String.format(numLocale, "%.1f", elapsedSec)
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedIds.forEachIndexed { idx, sensorId ->
                            val sensor = availableSensors.find { it.id == sensorId } ?: return@forEachIndexed
                            val color = CHANNEL_COLORS[idx % CHANNEL_COLORS.size]
                            val valText = inspectedFrame.values[sensorId]?.let { raw ->
                                "${sensor.formatValue(raw, useFahrenheit, pressureUnit, isEnglish)} ${sensor.displayUnit(useFahrenheit, pressureUnit, isEnglish)}"
                            } ?: "--"
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${sensor.localizedShortName(isEnglish)}: $valText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
