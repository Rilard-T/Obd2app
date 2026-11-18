package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AccelSamplePoint
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

fun parseCurveCsv(csv: String): List<AccelSamplePoint> {
    if (csv.isBlank()) return emptyList()
    return csv.split("|").mapNotNull { chunk ->
        val parts = chunk.split(";")
        if (parts.size < 3) return@mapNotNull null
        val t = parts[0].toFloatOrNull() ?: return@mapNotNull null
        val spd = parts[1].toFloatOrNull() ?: return@mapNotNull null
        val rpm = parts[2].toFloatOrNull() ?: 0f
        val hp = parts.getOrNull(3)?.toFloatOrNull() ?: 0f
        AccelSamplePoint(elapsedSec = t, speedKmh = spd, rpm = rpm, horsepower = hp)
    }
}

@Composable
fun AccelCurveCard(
    disciplineTitle: String,
    samples: List<AccelSamplePoint>,
    peakHorsepower: Float,
    peakTorqueNm: Float,
    modifier: Modifier = Modifier
) {
    if (samples.size < 2) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("accel_curve_card"),
        shape = MaterialTheme.shapes.small,
        color = CarbonObsidian,
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = l10nStr(R.string.accel_curve_title, disciplineTitle),
                    style = MaterialTheme.typography.labelMedium,
                    color = TelemetryCyan
                )
                if (peakHorsepower > 1f) {
                    Text(
                        text = l10nStr(
                            R.string.dyno_peak_hp,
                            peakHorsepower.toInt(),
                            peakTorqueNm.toInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MotorsportAmber
                    )
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
            ) {
                val maxT = samples.maxOf { it.elapsedSec }.coerceAtLeast(0.5f)
                val maxSpd = samples.maxOf { it.speedKmh }.coerceAtLeast(20f)
                val maxHp = samples.maxOf { it.horsepower }.coerceAtLeast(10f)
                val hasHp = samples.any { it.horsepower > 2f }

                // Baseline & mid grid
                drawLine(
                    color = CockpitBorder,
                    start = Offset(0f, size.height * 0.5f),
                    end = Offset(size.width, size.height * 0.5f),
                    strokeWidth = 1.dp.toPx()
                )

                val spdPath = Path()
                samples.forEachIndexed { idx, pt ->
                    val x = (pt.elapsedSec / maxT) * size.width
                    val y = size.height - ((pt.speedKmh / maxSpd).coerceIn(0f, 1f) * (size.height * 0.88f)) - 4f
                    if (idx == 0) spdPath.moveTo(x, y) else spdPath.lineTo(x, y)
                }
                drawPath(
                    path = spdPath,
                    color = TelemetryCyan,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                if (hasHp) {
                    val hpPath = Path()
                    samples.forEachIndexed { idx, pt ->
                        val x = (pt.elapsedSec / maxT) * size.width
                        val y = size.height - ((pt.horsepower / maxHp).coerceIn(0f, 1f) * (size.height * 0.82f)) - 4f
                        if (idx == 0) hpPath.moveTo(x, y) else hpPath.lineTo(x, y)
                    }
                    drawPath(
                        path = hpPath,
                        color = MotorsportAmber,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            Text(
                text = "${samples.first().speedKmh.toInt()} -> ${samples.last().speedKmh.toInt()} km/h (${String.format(java.util.Locale.US, "%.2f", samples.last().elapsedSec)}s)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
