package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.PrePurchaseInspectionResult
import com.example.obd.model.ResetSuspicionLevel
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.CockpitSurface
import com.example.ui.theme.CockpitSurfaceElevated
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary

@Composable
fun PrePurchaseInspectionCard(
    result: PrePurchaseInspectionResult?,
    isConnectionActive: Boolean,
    isEnglish: Boolean,
    onRunInspection: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = result?.isRunning == true
    val riskColor = when (result?.resetSuspicion) {
        ResetSuspicionLevel.HIGH -> RedlineCrimson
        ResetSuspicionLevel.CAUTION -> MotorsportAmber
        ResetSuspicionLevel.NONE -> NominalEmerald
        null -> TelemetryCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pre_purchase_inspection_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurface),
        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = riskColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = l10nStr(R.string.inspect_card_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = l10nStr(R.string.inspect_card_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRunInspection,
                    enabled = !isRunning && isConnectionActive,
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("run_pre_purchase_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CarbonObsidian
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.inspect_running), color = CarbonObsidian)
                    } else {
                        Text(l10nStr(R.string.inspect_run_btn), color = CarbonObsidian)
                    }
                }

                if (result != null && !result.isRunning && result.errorMessage == null) {
                    OutlinedButton(
                        onClick = onExportPdf,
                        border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("export_pre_purchase_pdf_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = MotorsportAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(l10nStr(R.string.inspect_export_pdf_btn), color = MotorsportAmber)
                    }
                }
            }

            if (result != null) {
                if (result.errorMessage != null) {
                    Text(
                        text = result.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RedlineCrimson
                    )
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = riskColor.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val badgeLabel = when (result.resetSuspicion) {
                            ResetSuspicionLevel.HIGH -> l10nStr(R.string.inspect_risk_high)
                            ResetSuspicionLevel.CAUTION -> l10nStr(R.string.inspect_risk_caution)
                            ResetSuspicionLevel.NONE -> l10nStr(R.string.inspect_risk_none)
                        }
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = riskColor,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    val kmUnit = if (isEnglish) "km" else "км"
                    val minUnit = if (isEnglish) "min" else "мин"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InspectionMetricChip(
                            label = l10nStr(R.string.inspect_metric_dist_clear),
                            value = result.distanceSinceCodesClearedKm?.let { "$it $kmUnit" } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                        InspectionMetricChip(
                            label = l10nStr(R.string.inspect_metric_warmups),
                            value = result.warmupsSinceCodesCleared?.toString() ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InspectionMetricChip(
                            label = l10nStr(R.string.inspect_metric_time_clear),
                            value = result.timeSinceCodesClearedMin?.let { "$it $minUnit" } ?: "—",
                            modifier = Modifier.weight(1f)
                        )
                        InspectionMetricChip(
                            label = l10nStr(R.string.inspect_metric_mil_dist),
                            value = result.distanceWithMilOnKm?.let { "$it $kmUnit" } ?: "0 $kmUnit",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "VIN: ${result.vin ?: "—"} • I/M: ${result.imReadyCount}/${result.imSupportedCount} • Mode 06 fail: ${result.mode06FailedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )

                    if (!result.aiVerdict.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = CarbonObsidian,
                            border = BorderStroke(1.dp, CockpitBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = l10nStr(R.string.inspect_ai_verdict_title),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TelemetryCyan
                                )
                                Text(
                                    text = result.aiVerdict,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
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
private fun InspectionMetricChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = CockpitSurfaceElevated,
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = TelemetryCyan)
        }
    }
}
