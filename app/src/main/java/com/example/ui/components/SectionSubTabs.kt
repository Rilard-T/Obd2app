package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.data.FuelTrimHealthStatus
import com.example.obd.data.FuelTrimHealthSummary
import com.example.obd.data.TelemetrySnapshotEntity
import com.example.obd.data.TripRecordEntity
import com.example.obd.i18n.l10nStr
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionSubTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_subtabs"),
        shape = MaterialTheme.shapes.medium,
        color = CockpitSurface,
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onSelect(index) }
                        .testTag("${testTagPrefix}_subtab_$index"),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) TelemetryCyan.copy(alpha = 0.18f) else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.7f)) else null
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TelemetryCyan else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FuelTrimHealthCard(
    summary: FuelTrimHealthSummary,
    isEnglish: Boolean,
    modifier: Modifier = Modifier
) {
    val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
    val accent = when (summary.status) {
        FuelTrimHealthStatus.NO_DATA -> TextSecondary
        FuelTrimHealthStatus.OPTIMAL -> NominalEmerald
        FuelTrimHealthStatus.SLIGHT_LEAN,
        FuelTrimHealthStatus.SLIGHT_RICH -> MotorsportAmber
        FuelTrimHealthStatus.LEAN_VACUUM_LEAK,
        FuelTrimHealthStatus.RICH_OVERFUEL -> RedlineCrimson
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fuel_trim_health_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = l10nStr(R.string.trim_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = summary.localizedTitle(isEnglish),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TrimStatChip(
                    label = l10nStr(R.string.trim_stft_label),
                    value = summary.stftPercent?.let { String.format(locale, "%+.1f%%", it) } ?: "--",
                    color = TelemetryCyan,
                    modifier = Modifier.weight(1f)
                )
                TrimStatChip(
                    label = l10nStr(R.string.trim_ltft_label),
                    value = summary.ltftPercent?.let { String.format(locale, "%+.1f%%", it) } ?: "--",
                    color = TelemetryCyan,
                    modifier = Modifier.weight(1f)
                )
                TrimStatChip(
                    label = l10nStr(R.string.trim_total_label),
                    value = summary.totalTrimPercent?.let { String.format(locale, "%+.1f%%", it) } ?: "--",
                    color = accent,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = summary.localizedHint(isEnglish),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun TrimStatChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = CockpitSurfaceElevated,
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.titleSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TelemetrySnapshotListCard(
    snapshots: List<TelemetrySnapshotEntity>,
    isEnglish: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
    val dateFmt = remember(isEnglish) { SimpleDateFormat("dd.MM HH:mm:ss", locale) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("telemetry_snapshots_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = MotorsportAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = l10nStr(R.string.snap_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (snapshots.isNotEmpty()) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.testTag("clear_snapshots_button")
                    ) {
                        Text(l10nStr(R.string.action_clear), color = TextSecondary)
                    }
                }
            }

            if (snapshots.isEmpty()) {
                Text(
                    text = l10nStr(R.string.snap_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            } else {
                snapshots.take(10).forEach { snap ->
                    val isAlert = snap.triggerType.equals("ALERT", ignoreCase = true)
                    val badgeColor = if (isAlert) RedlineCrimson else MotorsportAmber
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = CockpitSurfaceElevated,
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = badgeColor.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (isAlert) l10nStr(R.string.snap_trigger_alert) else l10nStr(R.string.snap_trigger_dtc),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = dateFmt.format(Date(snap.timestampMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                            Text(
                                text = listOfNotNull(
                                    snap.vehicleName.takeIf { it.isNotBlank() },
                                    snap.dtcSummary
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val metrics = buildList {
                                snap.rpm?.let { add("${it.toInt()} RPM") }
                                snap.speedKmh?.let { add("${it.toInt()} km/h") }
                                snap.coolantTempC?.let { add("ECT ${it.toInt()}°C") }
                                snap.stftPct?.let { add("STFT ${String.format(locale, "%+.1f%%", it)}") }
                                snap.ltftPct?.let { add("LTFT ${String.format(locale, "%+.1f%%", it)}") }
                                snap.batteryVoltage?.let { add("${String.format(locale, "%.1f", it)}V") }
                            }
                            if (metrics.isNotEmpty()) {
                                Text(
                                    text = metrics.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class TripCurvePoint(
    val tSec: Float,
    val speedKmh: Float,
    val rpm: Float
)

private fun parseTripCurveCsv(csv: String): List<TripCurvePoint> {
    if (csv.isBlank()) return emptyList()
    return csv.split('|').mapNotNull { row ->
        val parts = row.split(';')
        if (parts.size < 3) return@mapNotNull null
        val t = parts[0].toFloatOrNull() ?: return@mapNotNull null
        val spd = parts[1].replace(',', '.').toFloatOrNull() ?: return@mapNotNull null
        val rpm = parts[2].replace(',', '.').toFloatOrNull() ?: 0f
        TripCurvePoint(t, spd, rpm)
    }
}

@Composable
fun TripTelemetryCurveDialog(
    trip: TripRecordEntity,
    isEnglish: Boolean,
    onDismiss: () -> Unit
) {
    val points = remember(trip.id, trip.telemetryCurveCsv) {
        parseTripCurveCsv(trip.telemetryCurveCsv)
    }
    val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
    val maxSpeed = remember(points) { (points.maxOfOrNull { it.speedKmh } ?: 100f).coerceAtLeast(40f) }
    val maxRpm = remember(points) { (points.maxOfOrNull { it.rpm } ?: 6000f).coerceAtLeast(2500f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("trip_curve_dialog")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Timeline,
                        contentDescription = null,
                        tint = TelemetryCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = l10nStr(R.string.trip_curve_title, trip.vehicleName),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = String.format(
                        locale,
                        "%.2f km • max %.0f km/h • %d pt",
                        trip.distanceKm,
                        trip.maxSpeedKmh,
                        points.size
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )

                if (points.size < 2) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = CarbonObsidian,
                        border = BorderStroke(1.dp, CockpitBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = l10nStr(R.string.trip_curve_no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Text(
                        text = l10nStr(R.string.trip_curve_legend),
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .background(CarbonObsidian, MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        // Horizontal guide lines
                        for (i in 1..3) {
                            val y = h * (i / 4f)
                            drawLine(
                                color = Color.White.copy(alpha = 0.08f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1f
                            )
                        }

                        val speedPath = Path()
                        val rpmPath = Path()
                        val denom = (points.size - 1).coerceAtLeast(1)
                        points.forEachIndexed { idx, p ->
                            val x = w * (idx.toFloat() / denom)
                            val ySpeed = h - (p.speedKmh / maxSpeed).coerceIn(0f, 1f) * h
                            val yRpm = h - (p.rpm / maxRpm).coerceIn(0f, 1f) * h
                            if (idx == 0) {
                                speedPath.moveTo(x, ySpeed)
                                rpmPath.moveTo(x, yRpm)
                            } else {
                                speedPath.lineTo(x, ySpeed)
                                rpmPath.lineTo(x, yRpm)
                            }
                        }

                        drawPath(
                            path = rpmPath,
                            color = MotorsportAmber.copy(alpha = 0.78f),
                            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = speedPath,
                            color = TelemetryCyan,
                            style = Stroke(width = 4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("trip_curve_close_btn")
                    ) {
                        Text(l10nStr(R.string.action_close), color = TelemetryCyan)
                    }
                }
            }
        }
    }
}
