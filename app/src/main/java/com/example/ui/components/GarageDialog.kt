package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.obd.data.VehicleProfileEntity
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AiVehicleSuggestion
import com.example.obd.model.AiVehicleSuggestionState
import com.example.obd.model.FuelType
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

@Composable
fun GarageManagerDialog(
    uiState: ObdUiState,
    onSelectProfile: (Long) -> Unit,
    onSaveProfile: (
        existingId: Long?,
        name: String,
        vin: String?,
        displacementLiters: Float,
        fuelType: FuelType,
        redlineRpm: Int,
        shiftLightRpm: Int,
        fuelPricePerLiter: Float,
        preferredPresetId: String,
        odometerKm: Float?,
        curbWeightKg: Int
    ) -> Unit,
    onDeleteProfile: (Long) -> Unit,
    onRequestAiSuggestion: (vin: String?, query: String) -> Unit = { _, _ -> },
    onClearAiSuggestion: () -> Unit = {},
    onApplyAiSuggestionExtras: (AiVehicleSuggestion) -> Unit = {},
    onDismiss: () -> Unit
) {
    val isEng = uiState.settings.isEnglish
    var editingProfile by remember { mutableStateOf<VehicleProfileEntity?>(null) }
    var isAddingNew by remember { mutableStateOf(false) }

    if (isAddingNew || editingProfile != null) {
        VehicleProfileEditorDialog(
            initial = editingProfile,
            connectedVin = uiState.vehicleInfo.vin,
            defaultPresetId = uiState.settings.activePresetId,
            aiSuggestionState = uiState.aiVehicleSuggestionState,
            isEnglish = isEng,
            onRequestAiSuggestion = onRequestAiSuggestion,
            onApplyAiSuggestionExtras = onApplyAiSuggestionExtras,
            onSave = { name, vin, disp, fuel, redline, shift, price, preset, odometerKm, curbWeightKg ->
                onSaveProfile(
                    editingProfile?.id,
                    name,
                    vin,
                    disp,
                    fuel,
                    redline,
                    shift,
                    price,
                    preset,
                    odometerKm,
                    curbWeightKg
                )
                onClearAiSuggestion()
                editingProfile = null
                isAddingNew = false
            },
            onDismiss = {
                onClearAiSuggestion()
                editingProfile = null
                isAddingNew = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .testTag("garage_manager_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = CockpitSurface,
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
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = l10nStr(R.string.garage_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = l10nStr(R.string.action_close), tint = TextSecondary)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.vehicleProfiles, key = { it.id }) { profile ->
                        val isActive = profile.id == uiState.activeVehicleProfile?.id
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectProfile(profile.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isActive) TelemetryCyan.copy(alpha = 0.16f) else CockpitSurfaceElevated,
                            border = BorderStroke(1.dp, if (isActive) TelemetryCyan else CockpitBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = null,
                                                tint = TelemetryCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        Text(
                                            text = profile.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "${profile.displacementLiters}L • ${profile.fuelTypeEnum.localizedLabel(isEng)} • ${profile.odometerKm.toInt()} ${if (isEng) "km" else "км"} • ${profile.engineHours.toInt()}${if (isEng) "h" else "ч"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    profile.vin?.let { vin ->
                                        Text(
                                            text = "VIN: $vin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TelemetryCyan
                                        )
                                    }
                                }
                                Row {
                                    IconButton(
                                        onClick = { editingProfile = profile },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = l10nStr(R.string.cd_edit),
                                            tint = TelemetryCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (uiState.vehicleProfiles.size > 1) {
                                        IconButton(
                                            onClick = { onDeleteProfile(profile.id) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = l10nStr(R.string.cd_delete),
                                                tint = RedlineCrimson,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { isAddingNew = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garage_add_vehicle_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = CarbonObsidian, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(l10nStr(R.string.garage_add_btn), color = CarbonObsidian)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VehicleProfileEditorDialog(
    initial: VehicleProfileEntity?,
    connectedVin: String?,
    defaultPresetId: String,
    aiSuggestionState: AiVehicleSuggestionState,
    isEnglish: Boolean,
    onRequestAiSuggestion: (vin: String?, query: String) -> Unit,
    onApplyAiSuggestionExtras: (AiVehicleSuggestion) -> Unit,
    onSave: (
        name: String,
        vin: String?,
        displacementLiters: Float,
        fuelType: FuelType,
        redlineRpm: Int,
        shiftLightRpm: Int,
        fuelPricePerLiter: Float,
        preferredPresetId: String,
        odometerKm: Float?,
        curbWeightKg: Int
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var vin by remember { mutableStateOf(initial?.vin ?: "") }
    var dispText by remember { mutableStateOf((initial?.displacementLiters ?: 2.0f).toString()) }
    var fuelType by remember { mutableStateOf(initial?.fuelTypeEnum ?: FuelType.PETROL) }
    var redlineText by remember { mutableStateOf((initial?.redlineRpm ?: 6500).toString()) }
    var shiftText by remember { mutableStateOf((initial?.shiftLightRpm ?: 6000).toString()) }
    var priceText by remember { mutableStateOf((initial?.fuelPricePerLiter ?: 58.0f).toString()) }
    var odometerText by remember { mutableStateOf((initial?.odometerKm?.toInt() ?: 0).toString()) }
    var curbWeightText by remember { mutableStateOf((initial?.curbWeightKg ?: 1450).toString()) }
    val presetId = initial?.preferredPresetId ?: defaultPresetId

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(20.dp),
            color = CockpitSurface,
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (initial == null) l10nStr(R.string.garage_add_btn) else initial.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(28) },
                    label = { Text(l10nStr(R.string.garage_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vin,
                    onValueChange = { raw ->
                        vin = raw.uppercase()
                            .filter { it.isLetterOrDigit() || it == '-' }
                            .take(24)
                    },
                    label = { Text(l10nStr(R.string.garage_vin_label)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garage_vin_input")
                )

                val frameMatch = remember(vin) {
                    com.example.obd.model.VehicleCatalog.detectFromIdentifier(vin)
                }
                if (frameMatch != null && frameMatch.isFrameNumber && frameMatch.suggestedName != null) {
                    OutlinedButton(
                        onClick = {
                            name = frameMatch.suggestedName.take(28)
                            frameMatch.displacementLiters?.let { dispText = it.toString() }
                            frameMatch.fuelType?.let { fuelType = it }
                            frameMatch.templateId?.let { id ->
                                com.example.obd.model.VehicleCatalog.findById(id)?.let { tmpl ->
                                    redlineText = tmpl.redlineRpm.toString()
                                    shiftText = tmpl.shiftLightRpm.toString()
                                    curbWeightText = tmpl.curbWeightKg.toString()
                                }
                            }
                        },
                        border = BorderStroke(1.dp, NominalEmerald.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("garage_frame_autofill_button")
                    ) {
                        Text(
                            text = l10nStr(R.string.garage_frame_autofill_btn, frameMatch.suggestedName),
                            color = NominalEmerald
                        )
                    }
                }

                if (!connectedVin.isNullOrBlank() && vin != connectedVin) {
                    TextButton(onClick = { vin = connectedVin }) {
                        Text("${l10nStr(R.string.garage_bind_vin_btn)} ($connectedVin)", color = TelemetryCyan)
                    }
                }

                // AI Auto-Fill by VIN / Model
                OutlinedButton(
                    onClick = {
                        onRequestAiSuggestion(vin.ifBlank { connectedVin }, name)
                    },
                    enabled = aiSuggestionState !is AiVehicleSuggestionState.Loading &&
                        (name.isNotBlank() || vin.isNotBlank() || !connectedVin.isNullOrBlank()),
                    border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("garage_ai_autofill_button")
                ) {
                    if (aiSuggestionState is AiVehicleSuggestionState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MotorsportAmber)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.garage_ai_loading), color = MotorsportAmber)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MotorsportAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(l10nStr(R.string.garage_ai_autofill_btn), color = MotorsportAmber)
                    }
                }

                when (aiSuggestionState) {
                    is AiVehicleSuggestionState.Ready -> {
                        val s = aiSuggestionState.suggestion
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = CarbonObsidian,
                            border = BorderStroke(1.dp, NominalEmerald.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "${s.displayName} • ${s.displacementLiters}L • ${s.curbWeightKg} kg • ${s.redlineRpm} RPM",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = NominalEmerald
                                )
                                Text(
                                    text = "TO: ${s.maintenanceItems.size}${s.recommendedOemPackId?.let { " • OEM: $it" } ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Button(
                                    onClick = {
                                        name = s.displayName.take(28)
                                        dispText = s.displacementLiters.toString()
                                        fuelType = s.fuelType
                                        redlineText = s.redlineRpm.toString()
                                        shiftText = s.shiftLightRpm.toString()
                                        curbWeightText = s.curbWeightKg.toString()
                                        onApplyAiSuggestionExtras(s)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NominalEmerald),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(l10nStr(R.string.garage_ai_apply_btn), color = CarbonObsidian)
                                }
                            }
                        }
                    }
                    is AiVehicleSuggestionState.Error -> {
                        Text(text = aiSuggestionState.message, style = MaterialTheme.typography.labelSmall, color = RedlineCrimson)
                    }
                    else -> Unit
                }

                Text(
                    text = l10nStr(R.string.garage_fuel_type_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FuelType.entries.forEach { ft ->
                        FilterChip(
                            selected = fuelType == ft,
                            onClick = { fuelType = ft },
                            label = { Text(ft.localizedLabel(isEnglish)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                                selectedLabelColor = TelemetryCyan
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dispText,
                        onValueChange = { dispText = it },
                        label = { Text(l10nStr(R.string.garage_disp_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text(l10nStr(R.string.garage_fuel_price_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = redlineText,
                        onValueChange = { redlineText = it },
                        label = { Text(l10nStr(R.string.garage_redline_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = shiftText,
                        onValueChange = { shiftText = it },
                        label = { Text(l10nStr(R.string.garage_shift_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = odometerText,
                        onValueChange = { odometerText = it },
                        label = { Text(l10nStr(R.string.garage_odometer_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = curbWeightText,
                        onValueChange = { curbWeightText = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text(l10nStr(R.string.garage_curb_weight_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, CockpitBorder)
                    ) {
                        Text(l10nStr(R.string.action_cancel), color = TextMuted)
                    }
                    Button(
                        onClick = {
                            val disp = dispText.replace(',', '.').toFloatOrNull() ?: 2.0f
                            val price = priceText.replace(',', '.').toFloatOrNull() ?: 58.0f
                            val redline = redlineText.toIntOrNull() ?: 6500
                            val shift = shiftText.toIntOrNull() ?: 6000
                            val odo = odometerText.replace(',', '.').toFloatOrNull()
                            val weightKg = curbWeightText.toIntOrNull()?.coerceIn(600, 6000) ?: 1450
                            onSave(
                                name.ifBlank { if (isEnglish) "Vehicle" else "Автомобиль" },
                                vin.ifBlank { null },
                                disp,
                                fuelType,
                                redline,
                                shift,
                                price,
                                presetId,
                                odo,
                                weightKg
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                    ) {
                        Text(l10nStr(R.string.action_save), color = CarbonObsidian)
                    }
                }
            }
        }
    }
}
