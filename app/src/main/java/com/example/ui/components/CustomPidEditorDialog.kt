package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.obd.data.CustomPidEntity
import com.example.obd.i18n.l10nStr
import com.example.obd.model.LivePidProbeResult
import com.example.obd.protocol.TorqueEquationEvaluator
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
import kotlinx.coroutines.launch
import java.util.Locale

private data class TorquePreset(
    val label: String,
    val labelEn: String,
    val name: String,
    val nameEn: String,
    val shortName: String,
    val shortNameEn: String,
    val modeAndPid: String,
    val header: String,
    val equation: String,
    val unit: String,
    val unitEn: String,
    val minValue: String,
    val maxValue: String,
    val decimals: Int,
    val warnHigh: String = "",
    val critHigh: String = ""
)

private val TORQUE_PRESETS = listOf(
    TorquePreset(
        label = "АКПП °C (221E1C)",
        labelEn = "ATF °C (221E1C)",
        name = "Температура АКПП",
        nameEn = "Transmission Fluid Temp",
        shortName = "АКПП °C",
        shortNameEn = "ATF °C",
        modeAndPid = "221E1C",
        header = "7E1",
        equation = "A-40",
        unit = "°C",
        unitEn = "°C",
        minValue = "-20",
        maxValue = "140",
        decimals = 0,
        warnHigh = "105",
        critHigh = "118"
    ),
    TorquePreset(
        label = "Буст (010B)",
        labelEn = "Boost (010B)",
        name = "Давление наддува",
        nameEn = "Turbo Boost Pressure",
        shortName = "Буст",
        shortNameEn = "Boost",
        modeAndPid = "010B",
        header = "",
        equation = "(A-101)*0.01",
        unit = "бар",
        unitEn = "bar",
        minValue = "-1",
        maxValue = "2.5",
        decimals = 2,
        warnHigh = "1.6",
        critHigh = "2.0"
    ),
    TorquePreset(
        label = "Масло ДВС (015C)",
        labelEn = "Engine Oil (015C)",
        name = "Темп. масла ДВС",
        nameEn = "Engine Oil Temp",
        shortName = "Масло",
        shortNameEn = "Oil Temp",
        modeAndPid = "015C",
        header = "",
        equation = "A-40",
        unit = "°C",
        unitEn = "°C",
        minValue = "-20",
        maxValue = "150",
        decimals = 0,
        warnHigh = "118",
        critHigh = "130"
    ),
    TorquePreset(
        label = "АКБ ЭБУ (0142)",
        labelEn = "ECU Volt (0142)",
        name = "Напряжение ЭБУ",
        nameEn = "ECU Module Voltage",
        shortName = "АКБ ЭБУ",
        shortNameEn = "ECU Volt",
        modeAndPid = "0142",
        header = "",
        equation = "((A*256)+B)/1000",
        unit = "В",
        unitEn = "V",
        minValue = "10",
        maxValue = "16",
        decimals = 2
    )
)

@Composable
fun CustomPidEditorDialog(
    initialEntity: CustomPidEntity?,
    onTestLivePid: suspend (modeAndPid: String, header: String, equation: String) -> LivePidProbeResult,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        shortName: String,
        modeAndPid: String,
        header: String,
        equation: String,
        unit: String,
        minValue: Float,
        maxValue: Float,
        decimals: Int,
        warningHigh: Float?,
        criticalHigh: Float?,
        warningLow: Float?,
        criticalLow: Float?,
        alertsEnabled: Boolean
    ) -> Unit,
    isEnglish: Boolean = false
) {
    fun fmtOpt(v: Float?): String = when {
        v == null -> ""
        v % 1f == 0f -> v.toInt().toString()
        else -> v.toString()
    }

    var name by remember(initialEntity) { mutableStateOf(initialEntity?.name ?: "") }
    var shortName by remember(initialEntity) { mutableStateOf(initialEntity?.shortName ?: "") }
    var modeAndPid by remember(initialEntity) { mutableStateOf(initialEntity?.modeAndPid ?: "") }
    var header by remember(initialEntity) { mutableStateOf(initialEntity?.header ?: "") }
    var equation by remember(initialEntity) { mutableStateOf(initialEntity?.equation ?: "((A*256)+B)/4") }
    var unit by remember(initialEntity) { mutableStateOf(initialEntity?.unit ?: "") }
    var minText by remember(initialEntity) { mutableStateOf(initialEntity?.minValue?.toString() ?: "0") }
    var maxText by remember(initialEntity) { mutableStateOf(initialEntity?.maxValue?.toString() ?: "100") }
    var decimals by remember(initialEntity) { mutableIntStateOf(initialEntity?.decimals ?: 1) }
    var warnHighText by remember(initialEntity) { mutableStateOf(fmtOpt(initialEntity?.warningHigh)) }
    var critHighText by remember(initialEntity) { mutableStateOf(fmtOpt(initialEntity?.criticalHigh)) }
    var warnLowText by remember(initialEntity) { mutableStateOf(fmtOpt(initialEntity?.warningLow)) }
    var critLowText by remember(initialEntity) { mutableStateOf(fmtOpt(initialEntity?.criticalLow)) }
    var alertsEnabled by remember(initialEntity) { mutableStateOf(initialEntity?.alertsEnabled ?: true) }
    var sampleHex by remember { mutableStateOf("1A F8") }

    var isProbing by remember { mutableStateOf(false) }
    var liveProbeResult by remember { mutableStateOf<LivePidProbeResult?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val numLocale = if (isEnglish) Locale.US else Locale.forLanguageTag("ru-RU")
    val normalizedPid = TorqueEquationEvaluator.normalizeModeAndPid(modeAndPid)
    val expectedReply = TorqueEquationEvaluator.expectedResponseHeader(normalizedPid)
    val eqValidation = TorqueEquationEvaluator.validateEquation(equation, isEnglish)
    val sampleBytes = TorqueEquationEvaluator.parseHexSampleBytes(sampleHex)
    val sampleResult = if (eqValidation.isSuccess && sampleBytes.isNotEmpty()) {
        TorqueEquationEvaluator.evaluate(equation, sampleBytes)
    } else {
        null
    }

    val minVal = minText.replace(',', '.').toFloatOrNull() ?: 0f
    val maxVal = maxText.replace(',', '.').toFloatOrNull() ?: (minVal + 100f)
    val canSave = name.isNotBlank() && normalizedPid.length >= 4 && eqValidation.isSuccess

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 20.dp)
                .testTag("custom_pid_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, CockpitBorder)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 660.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (initialEntity == null) l10nStr(R.string.pid_dlg_new) else l10nStr(R.string.pid_dlg_edit),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_custom_pid_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = l10nStr(R.string.cd_close_dialog),
                            tint = TextSecondary
                        )
                    }
                }

                if (initialEntity == null) {
                    Text(
                        text = l10nStr(R.string.pid_quick_presets),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(TORQUE_PRESETS) { preset ->
                            FilterChip(
                                selected = normalizedPid == preset.modeAndPid && equation == preset.equation,
                                onClick = {
                                    name = if (isEnglish) preset.nameEn else preset.name
                                    shortName = if (isEnglish) preset.shortNameEn else preset.shortName
                                    modeAndPid = preset.modeAndPid
                                    header = preset.header
                                    equation = preset.equation
                                    unit = if (isEnglish) preset.unitEn else preset.unit
                                    minText = preset.minValue
                                    maxText = preset.maxValue
                                    decimals = preset.decimals
                                    warnHighText = preset.warnHigh
                                    critHighText = preset.critHigh
                                },
                                label = { Text(if (isEnglish) preset.labelEn else preset.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = TelemetryCyan
                                )
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(l10nStr(R.string.pid_name_label)) },
                    placeholder = { Text(l10nStr(R.string.pid_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pid_name_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = shortName,
                        onValueChange = { shortName = it.take(14) },
                        label = { Text(l10nStr(R.string.pid_short_label)) },
                        placeholder = { Text(l10nStr(R.string.pid_short_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pid_short_input")
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(8) },
                        label = { Text(l10nStr(R.string.pid_unit_label)) },
                        placeholder = { Text(l10nStr(R.string.pid_unit_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(0.75f)
                            .testTag("pid_unit_input")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = modeAndPid,
                        onValueChange = { modeAndPid = it.uppercase().take(12) },
                        label = { Text(l10nStr(R.string.pid_hex_label)) },
                        placeholder = { Text(l10nStr(R.string.pid_hex_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pid_hex_input")
                    )
                    OutlinedTextField(
                        value = header,
                        onValueChange = { header = it.uppercase().take(8) },
                        label = { Text(l10nStr(R.string.pid_hdr_label)) },
                        placeholder = { Text(l10nStr(R.string.pid_hdr_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pid_header_input")
                    )
                }

                if (expectedReply != null) {
                    Text(
                        text = l10nStr(R.string.pid_expected_reply, expectedReply),
                        style = MaterialTheme.typography.labelSmall,
                        color = TelemetryCyan
                    )
                } else {
                    Text(
                        text = l10nStr(R.string.pid_hex_help),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                OutlinedTextField(
                    value = equation,
                    onValueChange = { equation = it },
                    label = { Text(l10nStr(R.string.pid_eq_label)) },
                    placeholder = { Text(l10nStr(R.string.pid_eq_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pid_equation_input")
                )

                Text(
                    text = l10nStr(R.string.pid_eq_help),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )

                // Live ECU Probe Button & Response Box
                OutlinedButton(
                    onClick = {
                        if (!isProbing) {
                            isProbing = true
                            coroutineScope.launch {
                                liveProbeResult = onTestLivePid(modeAndPid, header, equation)
                                isProbing = false
                            }
                        }
                    },
                    enabled = !isProbing && normalizedPid.length >= 4 && eqValidation.isSuccess,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_probe_pid_button"),
                    border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f))
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MotorsportAmber
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(l10nStr(R.string.pid_probing), color = MotorsportAmber)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = MotorsportAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.pid_probe_btn), color = MotorsportAmber)
                    }
                }

                liveProbeResult?.let { probe ->
                    val boxBorder = if (probe.isSuccess) NominalEmerald.copy(alpha = 0.6f) else RedlineCrimson.copy(alpha = 0.6f)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CockpitSurfaceElevated,
                        border = BorderStroke(1.dp, boxBorder),
                        modifier = Modifier.testTag("live_probe_result_card")
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
                                Text(
                                    text = if (probe.isSuccess) {
                                        l10nStr(R.string.pid_probe_ok, probe.latencyMs)
                                    } else {
                                        l10nStr(R.string.pid_probe_fail)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (probe.isSuccess) NominalEmerald else RedlineCrimson
                                )
                                if (probe.extractedBytes.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            sampleHex = probe.extractedBytes.joinToString(" ") {
                                                String.format(Locale.US, "%02X", it)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                    ) {
                                        Text(l10nStr(R.string.pid_use_bytes), color = TelemetryCyan)
                                    }
                                }
                            }
                            if (probe.rawResponse.isNotBlank()) {
                                Text(
                                    text = "RAW: ${probe.rawResponse}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (probe.extractedBytes.isNotEmpty()) {
                                val bytesSummary = probe.extractedBytes.take(8).mapIndexed { idx, b ->
                                    "${('A' + idx)}=${String.format(Locale.US, "%02X", b)}($b)"
                                }.joinToString(" ")
                                Text(
                                    text = l10nStr(R.string.pid_bytes_prefix, bytesSummary),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan
                                )
                            }
                            if (probe.evaluatedValue != null) {
                                val valFmt = if (decimals == 0) {
                                    "${kotlin.math.round(probe.evaluatedValue).toInt()}"
                                } else {
                                    String.format(numLocale, "%.${decimals}f", probe.evaluatedValue)
                                }
                                Text(
                                    text = l10nStr(R.string.pid_value_prefix, valFmt, unit),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NominalEmerald
                                )
                            } else if (probe.errorMessage != null) {
                                Text(
                                    text = probe.errorMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RedlineCrimson
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = { Text(l10nStr(R.string.pid_min_scale)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pid_min_input")
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        label = { Text(l10nStr(R.string.pid_max_scale)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pid_max_input")
                    )
                }

                // Custom alert thresholds inside PID editor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = l10nStr(R.string.pid_thresholds_toggle),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Switch(
                        checked = alertsEnabled,
                        onCheckedChange = { alertsEnabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan)
                    )
                }

                if (alertsEnabled) {
                    val optLabel = l10nStr(R.string.hint_optional)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = warnHighText,
                            onValueChange = { warnHighText = it },
                            label = { Text(l10nStr(R.string.alert_warn_high)) },
                            placeholder = { Text(optLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = critHighText,
                            onValueChange = { critHighText = it },
                            label = { Text(l10nStr(R.string.alert_crit_high)) },
                            placeholder = { Text(optLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = warnLowText,
                            onValueChange = { warnLowText = it },
                            label = { Text(l10nStr(R.string.alert_warn_low)) },
                            placeholder = { Text(optLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = critLowText,
                            onValueChange = { critLowText = it },
                            label = { Text(l10nStr(R.string.alert_crit_low)) },
                            placeholder = { Text(optLabel) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = l10nStr(R.string.pid_decimals_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    listOf(0, 1, 2).forEach { dec ->
                        FilterChip(
                            selected = decimals == dec,
                            onClick = { decimals = dec },
                            label = { Text(l10nStr(R.string.pid_decimals_chip, dec)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                                selectedLabelColor = TelemetryCyan
                            )
                        )
                    }
                }

                // Live equation verification & hex byte test calculator
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = CockpitSurfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (eqValidation.isSuccess) NominalEmerald.copy(alpha = 0.45f) else RedlineCrimson.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (eqValidation.isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = if (eqValidation.isSuccess) NominalEmerald else RedlineCrimson,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (eqValidation.isSuccess) {
                                    l10nStr(R.string.pid_eq_valid)
                                } else {
                                    eqValidation.exceptionOrNull()?.message ?: l10nStr(R.string.pid_eq_invalid)
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (eqValidation.isSuccess) NominalEmerald else RedlineCrimson
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = sampleHex,
                                onValueChange = { sampleHex = it.uppercase() },
                                label = { Text(l10nStr(R.string.pid_test_bytes_label)) },
                                placeholder = { Text("1A F8") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pid_test_bytes_input")
                            )
                            Column(modifier = Modifier.weight(0.9f)) {
                                Text(
                                    text = l10nStr(R.string.pid_result_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = if (sampleResult != null) {
                                        val formatted = if (decimals == 0) {
                                            "${kotlin.math.round(sampleResult).toInt()}"
                                        } else {
                                            String.format(numLocale, "%.${decimals}f", sampleResult)
                                        }
                                        "$formatted $unit"
                                    } else {
                                        "--"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TelemetryCyan,
                                    modifier = Modifier.testTag("pid_test_result_text")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, CockpitBorder)
                    ) {
                        Text(l10nStr(R.string.action_cancel), color = TextSecondary)
                    }
                    Button(
                        onClick = {
                            onSave(
                                name.trim(),
                                shortName.trim().ifBlank { name.trim().take(12) },
                                normalizedPid,
                                TorqueEquationEvaluator.normalizeHeader(header),
                                equation.trim(),
                                unit.trim(),
                                minVal,
                                if (maxVal > minVal) maxVal else minVal + 10f,
                                decimals,
                                warnHighText.replace(',', '.').toFloatOrNull(),
                                critHighText.replace(',', '.').toFloatOrNull(),
                                warnLowText.replace(',', '.').toFloatOrNull(),
                                critLowText.replace(',', '.').toFloatOrNull(),
                                alertsEnabled
                            )
                        },
                        enabled = canSave,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_custom_pid_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                    ) {
                        Text(l10nStr(R.string.action_save), color = CarbonObsidian)
                    }
                }
            }
        }
    }
}
