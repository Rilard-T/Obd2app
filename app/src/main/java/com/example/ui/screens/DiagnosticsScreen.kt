package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.R
import com.example.obd.data.LogLevel
import com.example.obd.i18n.l10nContext
import com.example.obd.i18n.l10nStr
import com.example.obd.model.DtcSeverity
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DiagnosticsScreen(
    uiState: ObdUiState,
    onScanDtcs: () -> Unit,
    onClearDtcs: () -> Unit,
    onClearHistory: () -> Unit,
    onBuildErrorReportText: () -> String,
    onCreateShareableReportFile: () -> File,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val l10nCtx = l10nContext()
    val isEng = uiState.settings.isEnglish
    val numLocale = if (isEng) Locale.US else Locale.forLanguageTag("ru-RU")
    var showConfirmClearDialog by remember { mutableStateOf(false) }
    var logExportStatus by remember { mutableStateOf<String?>(null) }

    val dtc = uiState.dtcState
    val dateFormatter = remember(isEng) { SimpleDateFormat("dd.MM HH:mm", numLocale) }
    val logTimeFormatter = remember(isEng) { SimpleDateFormat("HH:mm:ss", numLocale) }

    val saveLogTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val saved = runCatching {
                val report = onBuildErrorReportText()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(report.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            logExportStatus = if (saved) {
                l10nCtx.getString(R.string.report_save_ok)
            } else {
                l10nCtx.getString(R.string.report_save_fail)
            }
        }
    }

    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text(l10nStr(R.string.dtc_clear_title)) },
            text = {
                Text(l10nStr(R.string.dtc_clear_confirm_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClearDialog = false
                        onClearDtcs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedlineCrimson),
                    modifier = Modifier.testTag("confirm_clear_dtc_button")
                ) {
                    Text(l10nStr(R.string.dtc_clear_btn))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmClearDialog = false },
                    modifier = Modifier.testTag("cancel_clear_dtc_button")
                ) {
                    Text(l10nStr(R.string.action_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("diagnostics_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            val milColor = if (dtc.milOn) RedlineCrimson else NominalEmerald
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = if (dtc.milOn) RedlineCrimson.copy(alpha = 0.12f) else CockpitSurface
                ),
                border = BorderStroke(1.dp, milColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (dtc.milOn) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                            contentDescription = l10nStr(R.string.cd_mil_status),
                            tint = milColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (dtc.milOn) l10nStr(R.string.mil_active) else l10nStr(R.string.mil_normal),
                                style = MaterialTheme.typography.titleLarge,
                                color = milColor
                            )
                            Text(
                                text = dtc.statusMessage ?: l10nStr(R.string.dtc_scan_hint),
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
                            onClick = onScanDtcs,
                            enabled = !dtc.isScanning && uiState.connectionState.isActiveTelemetry,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scan_dtc_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                        ) {
                            if (dtc.isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.dtc_scan_btn), color = MaterialTheme.colorScheme.onPrimary)
                        }

                        OutlinedButton(
                            onClick = { showConfirmClearDialog = true },
                            enabled = !dtc.isScanning && uiState.connectionState.isActiveTelemetry,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("clear_dtc_button"),
                            border = BorderStroke(1.dp, RedlineCrimson.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = null,
                                tint = RedlineCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.dtc_clear_btn), color = RedlineCrimson)
                        }
                    }
                }
            }
        }

        if (dtc.codes.isNotEmpty()) {
            item {
                Text(
                    text = l10nStr(R.string.dtc_active_codes, dtc.codes.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(dtc.codes, key = { it.code }) { code ->
                val badgeColor = when (code.severity) {
                    DtcSeverity.CRITICAL -> RedlineCrimson
                    DtcSeverity.MODERATE -> MotorsportAmber
                    DtcSeverity.INFO -> TelemetryCyan
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = code.code,
                                style = MaterialTheme.typography.headlineMedium,
                                color = badgeColor
                            )
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = badgeColor.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = code.severity.localizedLabel(isEng),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = code.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = code.system,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Error report and .txt log export card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("error_report_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                border = BorderStroke(1.dp, CockpitBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = MotorsportAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = l10nStr(R.string.report_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = l10nStr(R.string.report_entries_count, uiState.logEntries.size),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                        TextButton(
                            onClick = {
                                onClearLogs()
                                logExportStatus = l10nCtx.getString(R.string.report_logs_cleared)
                            },
                            modifier = Modifier.testTag("clear_logs_button")
                        ) {
                            Text(l10nStr(R.string.action_clear), color = TextSecondary)
                        }
                    }

                    Text(
                        text = l10nStr(R.string.report_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                saveLogTxtLauncher.launch("obd2_auto_log_$stamp.txt")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_logs_txt_button"),
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.7f))
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.btn_export_txt), color = TelemetryCyan)
                        }

                        Button(
                            onClick = {
                                runCatching {
                                    val reportFile = onCreateShareableReportFile()
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        reportFile
                                    )
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, l10nCtx.getString(R.string.share_report_subject))
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            l10nCtx.getString(R.string.share_report_body)
                                        )
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(shareIntent, l10nCtx.getString(R.string.share_report_chooser))
                                    )
                                }.onFailure { err ->
                                    logExportStatus = l10nCtx.getString(R.string.share_report_error, err.message ?: "")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("share_error_report_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = null,
                                tint = CarbonObsidian,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.btn_share_report), color = CarbonObsidian)
                        }
                    }

                    logExportStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelMedium,
                            color = NominalEmerald,
                            modifier = Modifier.testTag("log_export_status_text")
                        )
                    }

                    // Recent log viewer
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = CarbonObsidian,
                        border = BorderStroke(1.dp, CockpitBorder)
                    ) {
                        val recentLogs = uiState.logEntries.takeLast(60).reversed()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 90.dp, max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (recentLogs.isEmpty()) {
                                Text(
                                    text = l10nStr(R.string.log_empty),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            } else {
                                recentLogs.forEach { entry ->
                                    val lineColor = when (entry.level) {
                                        LogLevel.ERROR -> RedlineCrimson
                                        LogLevel.WARN -> MotorsportAmber
                                        LogLevel.OBD_TX -> TelemetryCyan
                                        LogLevel.OBD_RX -> NominalEmerald
                                        LogLevel.INFO -> TextSecondary
                                    }
                                    val timeStr = logTimeFormatter.format(Date(entry.timestampMs))
                                    Text(
                                        text = "$timeStr [${entry.tag}] ${entry.message}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = lineColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = l10nStr(R.string.scan_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (uiState.scanHistory.isNotEmpty()) {
                    TextButton(
                        onClick = onClearHistory,
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Text(l10nStr(R.string.action_clear), color = TextSecondary)
                    }
                }
            }
        }

        if (uiState.scanHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Text(
                        text = l10nStr(R.string.scan_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(uiState.scanHistory, key = { it.id }) { scan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = scan.adapterName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = dateFormatter.format(Date(scan.timestampMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = l10nStr(R.string.scan_result_line, scan.dtcSummary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (scan.dtcCount > 0) MotorsportAmber else NominalEmerald
                        )
                        val voltUnit = if (isEng) "V" else "В"
                        val voltStr = scan.batteryVoltage?.let { " • ${String.format(numLocale, "%.1f", it)} $voltUnit" } ?: ""
                        Text(
                            text = "${scan.protocol}$voltStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
