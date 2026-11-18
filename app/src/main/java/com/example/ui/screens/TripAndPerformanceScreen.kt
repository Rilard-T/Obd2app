package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.obd.data.MaintenanceItemEntity
import com.example.obd.data.TripRecordEntity
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AccelDiscipline
import com.example.obd.model.AccelTimerPhase
import com.example.obd.model.MaintenanceStatus
import com.example.obd.model.PhotoAnalysisMode
import com.example.obd.model.PhotoAnalysisState
import com.example.ui.ObdUiState
import com.example.ui.components.AccelCurveCard
import com.example.ui.components.parseCurveCsv
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripAndPerformanceScreen(
    uiState: ObdUiState,
    onOpenGarage: () -> Unit,
    onOpenHud: () -> Unit,
    onResetTrip: () -> Unit,
    onSaveTrip: () -> Unit,
    onDeleteTrip: (Long) -> Unit,
    onClearTrips: () -> Unit,
    onBuildTripsCsv: (List<TripRecordEntity>) -> String,
    onSelectAccelDiscipline: (AccelDiscipline) -> Unit,
    onArmAccelTimer: () -> Unit,
    onResetAccelTimer: () -> Unit,
    onClearAccelRuns: () -> Unit,
    onSetAutoSaveTrip: (Boolean) -> Unit,
    onSetFuelCalibrationFactor: (Float) -> Unit,
    onSetSpeedCalibrationFactor: (Float) -> Unit,
    onApplyFuelFillup: (actualLiters: Float, computerLiters: Float, (Float?) -> Unit) -> Unit,
    onStartGpsSpeedCheck: () -> Unit,
    onStopGpsSpeedCheck: () -> Unit,
    onApplySuggestedGpsFactor: () -> Unit,
    onAddMaintenanceItem: (title: String, category: String, intervalKm: Int, intervalHours: Int, notes: String?) -> Unit,
    onMarkMaintenanceDone: (Long) -> Unit,
    onDeleteMaintenanceItem: (Long) -> Unit,
    onAnalyzePhoto: (Uri, PhotoAnalysisMode) -> Unit = { _, _ -> },
    onClearPhotoAnalysis: () -> Unit = {},
    onUpdateFuelPrice: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val l10nCtx = com.example.obd.i18n.l10nContext()
    val isEng = uiState.settings.isEnglish
    val locale = if (isEng) Locale.US else Locale.forLanguageTag("ru-RU")
    val dateFmt = remember(isEng) { SimpleDateFormat("dd.MM HH:mm", locale) }
    var csvExportStatus by remember { mutableStateOf<String?>(null) }
    var showFillupDialog by remember { mutableStateOf(false) }
    var showAddMaintDialog by remember { mutableStateOf(false) }
    var pendingPhotoMode by remember { mutableStateOf(PhotoAnalysisMode.FUEL_RECEIPT) }

    val tripPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAnalyzePhoto(uri, pendingPhotoMode)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartGpsSpeedCheck()
        }
    }

    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val ok = runCatching {
                val csv = onBuildTripsCsv(uiState.tripHistory)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
            }.isSuccess
            csvExportStatus = if (ok) {
                l10nCtx.getString(R.string.report_save_ok)
            } else {
                l10nCtx.getString(R.string.report_save_fail)
            }
        }
    }

    val trip = uiState.tripStats
    val accel = uiState.accelTimer
    val activeCar = uiState.activeVehicleProfile

    if (showFillupDialog) {
        var actualText by remember { mutableStateOf("42.0") }
        var computerText by remember {
            mutableStateOf(
                if (trip.fuelUsedLiters > 1f) String.format(Locale.US, "%.1f", trip.fuelUsedLiters)
                else "40.0"
            )
        }
        AlertDialog(
            onDismissRequest = { showFillupDialog = false },
            title = { Text(l10nStr(R.string.cal_fillup_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = actualText,
                        onValueChange = { actualText = it },
                        label = { Text(l10nStr(R.string.cal_actual_liters)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = computerText,
                        onValueChange = { computerText = it },
                        label = { Text(l10nStr(R.string.cal_computer_liters)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val actual = actualText.replace(',', '.').toFloatOrNull() ?: 0f
                        val comp = computerText.replace(',', '.').toFloatOrNull() ?: 0f
                        if (actual > 0f && comp > 0f) {
                            onApplyFuelFillup(actual, comp) { }
                        }
                        showFillupDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                ) {
                    Text(l10nStr(R.string.action_save), color = CarbonObsidian)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFillupDialog = false }) {
                    Text(l10nStr(R.string.action_cancel))
                }
            }
        )
    }

    if (showAddMaintDialog) {
        var title by remember { mutableStateOf("") }
        var intervalKmText by remember { mutableStateOf("10000") }
        var intervalHrsText by remember { mutableStateOf("0") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddMaintDialog = false },
            title = { Text(l10nStr(R.string.maint_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40) },
                        label = { Text(l10nStr(R.string.maint_item_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = intervalKmText,
                            onValueChange = { intervalKmText = it },
                            label = { Text(l10nStr(R.string.maint_interval_km_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = intervalHrsText,
                            onValueChange = { intervalHrsText = it },
                            label = { Text(l10nStr(R.string.maint_interval_hours_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it.take(80) },
                        label = { Text(l10nStr(R.string.maint_notes_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onAddMaintenanceItem(
                                title.trim(),
                                "CUSTOM",
                                (intervalKmText.toIntOrNull() ?: 10000).coerceAtLeast(500),
                                (intervalHrsText.toIntOrNull() ?: 0).coerceAtLeast(0),
                                notes.trim().takeIf { it.isNotBlank() }
                            )
                        }
                        showAddMaintDialog = false
                    },
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                ) {
                    Text(l10nStr(R.string.action_save), color = CarbonObsidian)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMaintDialog = false }) {
                    Text(l10nStr(R.string.action_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("trip_performance_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Vehicle + HUD Launch Bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = l10nStr(
                                R.string.garage_active_car,
                                activeCar?.name ?: (if (isEng) "My Car" else "Авто")
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val vinText = uiState.vehicleInfo.vin?.let { "VIN: $it" }
                            ?: "${activeCar?.displacementLiters ?: 2.0f}L • ${activeCar?.fuelTypeEnum?.localizedLabel(isEng) ?: ""}"
                        Text(
                            text = vinText,
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onOpenGarage,
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("trip_open_garage_button")
                        ) {
                            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.garage_manage_btn), color = TelemetryCyan)
                        }
                        Button(
                            onClick = onOpenHud,
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                            modifier = Modifier.testTag("trip_open_hud_button")
                        ) {
                            Icon(Icons.Filled.FlipToFront, contentDescription = null, tint = CarbonObsidian, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("HUD", color = CarbonObsidian)
                        }
                    }
                }
            }
        }

        // Real-time Trip Computer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("trip_computer_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, CockpitBorder)
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
                                imageVector = Icons.Filled.LocalGasStation,
                                contentDescription = null,
                                tint = TelemetryCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = l10nStr(R.string.trip_computer_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        TextButton(onClick = onResetTrip, modifier = Modifier.testTag("reset_trip_button")) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.trip_reset_btn), color = TextSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripMetricBox(
                            label = l10nStr(R.string.trip_distance),
                            value = String.format(locale, "%.2f %s", trip.distanceKm, if (isEng) "km" else "км"),
                            color = TelemetryCyan,
                            modifier = Modifier.weight(1f)
                        )
                        TripMetricBox(
                            label = l10nStr(R.string.trip_elapsed),
                            value = formatDuration(trip.elapsedSec),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TripMetricBox(
                            label = l10nStr(R.string.trip_instant_fuel),
                            value = trip.instantLPer100Km?.let {
                                String.format(locale, "%.1f %s", it, if (isEng) "L/100" else "л/100")
                            } ?: trip.instantLPerHour?.let {
                                String.format(locale, "%.1f %s", it, if (isEng) "L/h" else "л/ч")
                            } ?: "--",
                            color = MotorsportAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripMetricBox(
                            label = l10nStr(R.string.trip_avg_fuel),
                            value = trip.avgLPer100Km?.let {
                                String.format(locale, "%.1f %s", it, if (isEng) "L/100" else "л/100")
                            } ?: "--",
                            color = NominalEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        TripMetricBox(
                            label = l10nStr(R.string.trip_fuel_used),
                            value = String.format(locale, "%.2f %s", trip.fuelUsedLiters, if (isEng) "L" else "л"),
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TripMetricBox(
                            label = l10nStr(R.string.trip_cost),
                            value = String.format(locale, "%.0f %s", trip.estimatedCost, if (isEng) "" else "₽").trim(),
                            color = MotorsportAmber,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripMetricBox(
                            label = l10nStr(R.string.trip_avg_speed),
                            value = "${trip.avgSpeedKmh.toInt()} ${if (isEng) "km/h" else "км/ч"}",
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        TripMetricBox(
                            label = l10nStr(R.string.trip_max_speed),
                            value = "${trip.maxSpeedKmh.toInt()} ${if (isEng) "km/h" else "км/ч"}",
                            color = TelemetryCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = onSaveTrip,
                        enabled = uiState.connectionState.isActiveTelemetry || trip.distanceKm > 0.01f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_trip_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = CarbonObsidian, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.trip_save_btn), color = CarbonObsidian)
                    }

                    HorizontalDivider(color = CockpitBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = l10nStr(R.string.trip_auto_save_title),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = l10nStr(R.string.trip_auto_save_sub),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = uiState.settings.autoSaveTripOnDisconnect,
                            onCheckedChange = onSetAutoSaveTrip,
                            colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan),
                            modifier = Modifier.testTag("auto_save_trip_switch")
                        )
                    }
                }
            }
        }

        // Virtual Dyno (HP & Torque), Eco Care Score & AI Receipt/Invoice Scanner
        item {
            val ecoColor = when {
                trip.ecoScore >= 80 -> NominalEmerald
                trip.ecoScore >= 55 -> MotorsportAmber
                else -> RedlineCrimson
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("virtual_dyno_eco_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = MotorsportAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = l10nStr(R.string.dyno_card_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = ecoColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = l10nStr(R.string.eco_score_label, trip.ecoScore),
                                style = MaterialTheme.typography.labelLarge,
                                color = ecoColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TripMetricBox(
                            label = l10nStr(R.string.dyno_power_label),
                            value = trip.instantHorsepower?.let {
                                String.format(locale, "%.0f %s", it, if (isEng) "hp" else "л.с.")
                            } ?: "--",
                            color = MotorsportAmber,
                            modifier = Modifier.weight(1f)
                        )
                        TripMetricBox(
                            label = l10nStr(R.string.dyno_torque_label),
                            value = trip.instantTorqueNm?.let {
                                String.format(locale, "%.0f %s", it, if (isEng) "N·m" else "Н·м")
                            } ?: "--",
                            color = TelemetryCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = l10nStr(
                                R.string.dyno_peak_hp,
                                trip.peakHorsepower.toInt(),
                                trip.peakTorqueNm.toInt()
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MotorsportAmber
                        )
                        Text(
                            text = l10nStr(
                                R.string.eco_details_fmt,
                                trip.harshEventsCount,
                                trip.coldRevSeconds
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    HorizontalDivider(color = CockpitBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pendingPhotoMode = PhotoAnalysisMode.FUEL_RECEIPT
                                tripPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vision_fuel_receipt_button")
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.vision_fuel_receipt_btn), color = TelemetryCyan)
                        }
                        OutlinedButton(
                            onClick = {
                                pendingPhotoMode = PhotoAnalysisMode.SERVICE_INVOICE
                                tripPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.65f)),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("vision_service_invoice_button")
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MotorsportAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(l10nStr(R.string.vision_service_invoice_btn), color = MotorsportAmber)
                        }
                    }

                    when (val photoState = uiState.photoAnalysisState) {
                        is PhotoAnalysisState.Analyzing -> {
                            if (photoState.mode != PhotoAnalysisMode.DASH_WARNING_LIGHT) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = TelemetryCyan
                                    )
                                    Text(
                                        text = l10nStr(R.string.vision_analyzing),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TelemetryCyan
                                    )
                                }
                            }
                        }
                        is PhotoAnalysisState.FuelReceiptResult -> {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = CockpitSurfaceElevated,
                                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vision_fuel_receipt_result")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = photoState.rawSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (photoState.receipt.pricePerLiter > 0f) {
                                            Button(
                                                onClick = {
                                                    onUpdateFuelPrice(photoState.receipt.pricePerLiter)
                                                    onClearPhotoAnalysis()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("vision_apply_receipt_button")
                                            ) {
                                                Text(
                                                    text = l10nStr(
                                                        R.string.vision_apply_receipt_btn,
                                                        photoState.receipt.pricePerLiter
                                                    ),
                                                    color = CarbonObsidian
                                                )
                                            }
                                        }
                                        TextButton(onClick = onClearPhotoAnalysis) {
                                            Text(l10nStr(R.string.action_close), color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                        is PhotoAnalysisState.ServiceInvoiceResult -> {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = CockpitSurfaceElevated,
                                border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("vision_service_invoice_result")
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = photoState.rawSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                onAddMaintenanceItem(
                                                    photoState.invoice.title,
                                                    photoState.invoice.category,
                                                    10000,
                                                    0,
                                                    photoState.invoice.notes
                                                )
                                                onClearPhotoAnalysis()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("vision_apply_invoice_button")
                                        ) {
                                            Text(l10nStr(R.string.vision_apply_invoice_btn), color = CarbonObsidian)
                                        }
                                        TextButton(onClick = onClearPhotoAnalysis) {
                                            Text(l10nStr(R.string.action_close), color = TextSecondary)
                                        }
                                    }
                                }
                            }
                        }
                        is PhotoAnalysisState.Error -> {
                            Text(
                                text = photoState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = RedlineCrimson
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }

        // Fuel & Speed Calibration + Live GPS Reference Card
        item {
            val gps = uiState.gpsSpeedState
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("calibration_gps_card"),
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
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.cal_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Fuel calibration slider + fill-up button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = l10nStr(
                                R.string.cal_fuel_factor,
                                String.format(locale, "%.2fx", uiState.settings.fuelCalibrationFactor)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = { showFillupDialog = true },
                            border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                            modifier = Modifier.testTag("cal_fillup_button")
                        ) {
                            Text(l10nStr(R.string.cal_fillup_btn), color = MotorsportAmber)
                        }
                    }
                    Slider(
                        value = uiState.settings.fuelCalibrationFactor,
                        onValueChange = onSetFuelCalibrationFactor,
                        valueRange = 0.70f..1.35f,
                        colors = SliderDefaults.colors(
                            thumbColor = TelemetryCyan,
                            activeTrackColor = TelemetryCyan,
                            inactiveTrackColor = CockpitBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Speed calibration slider + GPS verification
                    Text(
                        text = l10nStr(
                            R.string.cal_speed_factor,
                            String.format(locale, "%.2fx", uiState.settings.speedCalibrationFactor)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = uiState.settings.speedCalibrationFactor,
                        onValueChange = onSetSpeedCalibrationFactor,
                        valueRange = 0.85f..1.15f,
                        colors = SliderDefaults.colors(
                            thumbColor = TelemetryCyan,
                            activeTrackColor = TelemetryCyan,
                            inactiveTrackColor = CockpitBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (gps.isTracking) {
                                    onStopGpsSpeedCheck()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        onStartGpsSpeedCheck()
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                }
                            },
                            border = BorderStroke(
                                1.dp,
                                if (gps.isTracking) RedlineCrimson else TelemetryCyan.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("gps_speed_check_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GpsFixed,
                                contentDescription = null,
                                tint = if (gps.isTracking) RedlineCrimson else TelemetryCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (gps.isTracking) l10nStr(R.string.cal_gps_btn_stop)
                                else l10nStr(R.string.cal_gps_btn_start),
                                color = if (gps.isTracking) RedlineCrimson else TelemetryCyan
                            )
                        }

                        gps.suggestedSpeedFactor?.let { suggested ->
                            Button(
                                onClick = onApplySuggestedGpsFactor,
                                colors = ButtonDefaults.buttonColors(containerColor = NominalEmerald),
                                modifier = Modifier.testTag("gps_apply_factor_button")
                            ) {
                                Text(
                                    text = l10nStr(
                                        R.string.cal_gps_apply_btn,
                                        String.format(locale, "%.2fx", suggested)
                                    ),
                                    color = CarbonObsidian
                                )
                            }
                        }
                    }

                    if (gps.isTracking) {
                        val gpsStr = gps.gpsSpeedKmh?.let { String.format(locale, "%.0f", it) }
                        val obdStr = gps.obdRawSpeedKmh?.let { String.format(locale, "%.0f", it) } ?: "--"
                        val acc = gps.accuracyMeters?.toInt() ?: 0
                        Text(
                            text = if (gpsStr != null) l10nStr(R.string.cal_gps_live, gpsStr, obdStr, acc)
                            else l10nStr(R.string.cal_gps_waiting),
                            style = MaterialTheme.typography.labelMedium,
                            color = TelemetryCyan
                        )
                    }
                    gps.errorMessage?.let { err ->
                        Text(text = err, style = MaterialTheme.typography.labelSmall, color = RedlineCrimson)
                    }
                }
            }
        }

        // Maintenance Tracker (TO) Card
        item {
            val odometerKm = activeCar?.odometerKm ?: 0f
            val engineHours = activeCar?.engineHours ?: 0f
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("maintenance_tracker_card"),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.Build,
                                contentDescription = null,
                                tint = MotorsportAmber,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = l10nStr(R.string.maint_card_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = l10nStr(
                                        R.string.maint_vehicle_stats,
                                        odometerKm.toInt().toString(),
                                        String.format(locale, "%.1f", engineHours)
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { showAddMaintDialog = true },
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.7f)),
                            modifier = Modifier.testTag("add_maintenance_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.maint_add_btn), color = TelemetryCyan)
                        }
                    }

                    uiState.maintenanceItems.forEach { item ->
                        MaintenanceRowItem(
                            item = item,
                            currentOdometerKm = odometerKm,
                            onMarkDone = { onMarkMaintenanceDone(item.id) },
                            onDelete = { onDeleteMaintenanceItem(item.id) }
                        )
                    }
                }
            }
        }

        // Performance / Acceleration Timer Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("perf_timer_card"),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
                border = BorderStroke(
                    1.dp,
                    when (accel.phase) {
                        AccelTimerPhase.ARMED -> MotorsportAmber
                        AccelTimerPhase.RUNNING -> RedlineCrimson
                        AccelTimerPhase.COMPLETED -> NominalEmerald
                        AccelTimerPhase.IDLE -> CockpitBorder
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = null,
                            tint = MotorsportAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.perf_timer_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AccelDiscipline.entries.forEach { disc ->
                            FilterChip(
                                selected = accel.discipline == disc,
                                onClick = { onSelectAccelDiscipline(disc) },
                                label = { Text(disc.localizedLabel(isEng)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MotorsportAmber.copy(alpha = 0.22f),
                                    selectedLabelColor = MotorsportAmber
                                ),
                                modifier = Modifier.testTag("perf_disc_${disc.id}")
                            )
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = CarbonObsidian,
                        border = BorderStroke(1.dp, CockpitBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = String.format(locale, "%.2f %s", accel.elapsedSec, if (isEng) "s" else "с"),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = when (accel.phase) {
                                    AccelTimerPhase.COMPLETED -> NominalEmerald
                                    AccelTimerPhase.RUNNING -> TelemetryCyan
                                    AccelTimerPhase.ARMED -> MotorsportAmber
                                    AccelTimerPhase.IDLE -> TextSecondary
                                }
                            )
                            val phaseMsg = when (accel.phase) {
                                AccelTimerPhase.IDLE -> l10nStr(R.string.perf_phase_idle)
                                AccelTimerPhase.ARMED -> l10nStr(R.string.perf_phase_armed)
                                AccelTimerPhase.RUNNING -> l10nStr(R.string.perf_phase_running, "${accel.coveredMeters.toInt()}")
                                AccelTimerPhase.COMPLETED -> l10nStr(
                                    R.string.perf_phase_done,
                                    String.format(locale, "%.2f", accel.lastResultSec ?: accel.elapsedSec),
                                    accel.peakSpeedKmh.toInt()
                                )
                            }
                            Text(text = phaseMsg, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onArmAccelTimer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("perf_arm_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MotorsportAmber)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = CarbonObsidian, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.perf_arm_btn), color = CarbonObsidian)
                        }
                        OutlinedButton(
                            onClick = onResetAccelTimer,
                            modifier = Modifier.weight(1f),
                            border = BorderStroke(1.dp, CockpitBorder)
                        ) {
                            Text(l10nStr(R.string.perf_reset_btn), color = TextSecondary)
                        }
                    }

                    if (accel.samples.size >= 2) {
                        AccelCurveCard(
                            disciplineTitle = accel.discipline.localizedLabel(isEng),
                            samples = accel.samples,
                            peakHorsepower = accel.peakHorsepower,
                            peakTorqueNm = accel.peakTorqueNm
                        )
                    } else {
                        val latestCurveRun = uiState.accelerationRuns.firstOrNull()
                        val parsedSamples = remember(latestCurveRun?.id) {
                            latestCurveRun?.let { parseCurveCsv(it.curveCsv) }.orEmpty()
                        }
                        if (latestCurveRun != null && parsedSamples.size >= 2) {
                            AccelCurveCard(
                                disciplineTitle = latestCurveRun.discipline,
                                samples = parsedSamples,
                                peakHorsepower = latestCurveRun.peakHorsepower,
                                peakTorqueNm = latestCurveRun.peakTorqueNm
                            )
                        }
                    }

                    if (uiState.accelerationRuns.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = l10nStr(R.string.perf_runs_title),
                                style = MaterialTheme.typography.labelLarge,
                                color = TelemetryCyan
                            )
                            TextButton(onClick = onClearAccelRuns) {
                                Text(l10nStr(R.string.action_clear), color = TextMuted)
                            }
                        }
                        uiState.accelerationRuns.take(5).forEach { run ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val hpSuffix = if (run.peakHorsepower > 1f) {
                                    " • ${run.peakHorsepower.toInt()} ${if (isEng) "hp" else "л.с."}"
                                } else ""
                                Text(
                                    text = "${run.discipline} • ${run.vehicleName}$hpSuffix",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = String.format(locale, "%.2f %s", run.elapsedSec, if (isEng) "s" else "с"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NominalEmerald
                                )
                            }
                        }
                    }
                }
            }
        }

        // Trip History + CSV Export
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = l10nStr(R.string.trip_history_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (uiState.tripHistory.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                csvLauncher.launch("obd2_trips_$stamp.csv")
                            },
                            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(l10nStr(R.string.trip_export_csv_btn), color = TelemetryCyan)
                        }
                        TextButton(onClick = onClearTrips) {
                            Text(l10nStr(R.string.action_clear), color = TextMuted)
                        }
                    }
                }
            }
            csvExportStatus?.let {
                Text(text = it, style = MaterialTheme.typography.labelMedium, color = NominalEmerald)
            }
        }

        if (uiState.tripHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Text(
                        text = l10nStr(R.string.trip_history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(uiState.tripHistory, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = CockpitSurface),
                    border = BorderStroke(1.dp, CockpitBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${item.vehicleName} • ${dateFmt.format(Date(item.endedAtMs))}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val avgStr = item.avgLPer100Km?.let { String.format(locale, "%.1f", it) } ?: "--"
                            Text(
                                text = String.format(
                                    locale,
                                    "%.2f km • %s • %s L/100 • max %d km/h",
                                    item.distanceKm,
                                    formatDuration(item.durationSec),
                                    avgStr,
                                    item.maxSpeedKmh.toInt()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        IconButton(onClick = { onDeleteTrip(item.id) }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = l10nStr(R.string.cd_delete), tint = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripMetricBox(
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

@Composable
private fun MaintenanceRowItem(
    item: MaintenanceItemEntity,
    currentOdometerKm: Float,
    onMarkDone: () -> Unit,
    onDelete: () -> Unit
) {
    val kmElapsed = (currentOdometerKm - item.lastServiceOdometerKm).coerceAtLeast(0f)
    val interval = item.intervalKm.coerceAtLeast(1)
    val remaining = interval - kmElapsed.toInt()
    val progress = (kmElapsed / interval.toFloat()).coerceIn(0f, 1f)
    val status = when {
        remaining <= 0 -> MaintenanceStatus.OVERDUE
        remaining <= (interval * 0.15f).toInt().coerceAtLeast(500) -> MaintenanceStatus.DUE_SOON
        else -> MaintenanceStatus.OK
    }
    val statusColor = when (status) {
        MaintenanceStatus.OVERDUE -> RedlineCrimson
        MaintenanceStatus.DUE_SOON -> MotorsportAmber
        MaintenanceStatus.OK -> NominalEmerald
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = CockpitSurfaceElevated,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("maint_item_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (remaining >= 0) {
                            l10nStr(R.string.maint_remaining_km, remaining)
                        } else {
                            l10nStr(R.string.maint_overdue_km, -remaining)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                    item.notes?.let { note ->
                        Text(text = note, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = onMarkDone,
                        border = BorderStroke(1.dp, NominalEmerald.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("maint_done_${item.id}")
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = NominalEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(l10nStr(R.string.maint_done_btn), color = NominalEmerald)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = l10nStr(R.string.cd_delete), tint = TextMuted)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                color = statusColor,
                trackColor = CarbonObsidian,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}
