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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AiVehicleSuggestionState
import com.example.obd.model.AppLanguage
import com.example.obd.model.CatalogVehicleTemplate
import com.example.obd.model.FuelType
import com.example.obd.model.VehicleCatalog
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingWizardDialog(
    initialLanguage: AppLanguage,
    isEnglish: Boolean,
    aiVehicleSuggestionState: AiVehicleSuggestionState,
    onSetLanguage: (AppLanguage) -> Unit,
    onRequestAiSuggestion: (vin: String?, query: String) -> Unit,
    onClearAiSuggestion: () -> Unit,
    onFinish: (
        language: AppLanguage,
        customVehicleName: String,
        vin: String?,
        template: CatalogVehicleTemplate?,
        displacementLiters: Float,
        fuelType: FuelType,
        redlineRpm: Int,
        curbWeightKg: Int,
        importRecommendedOemPids: Boolean
    ) -> Unit,
    onSkip: () -> Unit
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var selectedLang by remember { mutableStateOf(initialLanguage) }
    var vinInput by rememberSaveable { mutableStateOf("") }
    var selectedBrand by rememberSaveable { mutableStateOf(VehicleCatalog.brands.first()) }
    var selectedTemplateId by rememberSaveable {
        mutableStateOf(VehicleCatalog.modelsForBrand(VehicleCatalog.brands.first()).firstOrNull()?.id ?: "")
    }
    var carName by rememberSaveable { mutableStateOf("") }
    var dispText by rememberSaveable { mutableStateOf("2.0") }
    var selectedFuel by remember { mutableStateOf(FuelType.PETROL) }
    var redlineText by rememberSaveable { mutableStateOf("6500") }
    var weightText by rememberSaveable { mutableStateOf("1450") }
    var importOemAndMaint by rememberSaveable { mutableStateOf(true) }

    val selectedTemplate = remember(selectedTemplateId) {
        VehicleCatalog.findById(selectedTemplateId)
    }

    fun applyTemplateDefaults(tmpl: CatalogVehicleTemplate) {
        selectedTemplateId = tmpl.id
        carName = "${tmpl.brand.substringBefore(" (").substringBefore(" /")} ${tmpl.modelName.substringBefore(" /")}"
        dispText = tmpl.displacementLiters.toString()
        selectedFuel = tmpl.fuelType
        redlineText = tmpl.redlineRpm.toString()
        weightText = tmpl.curbWeightKg.toString()
    }

    LaunchedEffect(Unit) {
        if (carName.isBlank() && selectedTemplate != null) {
            applyTemplateDefaults(selectedTemplate)
        }
    }

    val detectedMatch = remember(vinInput) { VehicleCatalog.detectFromIdentifier(vinInput) }
    LaunchedEffect(detectedMatch) {
        val match = detectedMatch ?: return@LaunchedEffect
        selectedBrand = match.brand
        val targetTemplate = match.templateId?.let { VehicleCatalog.findById(it) }
            ?: VehicleCatalog.modelsForBrand(match.brand).firstOrNull()
        if (targetTemplate != null) {
            applyTemplateDefaults(targetTemplate)
        }
        match.suggestedName?.let { carName = it }
        match.displacementLiters?.let { dispText = it.toString() }
        match.fuelType?.let { selectedFuel = it }
    }

    LaunchedEffect(aiVehicleSuggestionState) {
        val ready = aiVehicleSuggestionState as? AiVehicleSuggestionState.Ready ?: return@LaunchedEffect
        val s = ready.suggestion
        if (s.displayName.isNotBlank()) carName = s.displayName
        dispText = s.displacementLiters.toString()
        selectedFuel = s.fuelType
        redlineText = s.redlineRpm.toString()
        weightText = s.curbWeightKg.toString()
    }

    val oemCountForTemplate = remember(selectedTemplate) {
        selectedTemplate?.let { VehicleCatalog.countOemPidsForTemplate(it) } ?: 0
    }

    Dialog(
        onDismissRequest = { onSkip() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = CockpitSurface),
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.65f)),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .testTag("onboarding_wizard_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = l10nStr(R.string.wizard_step_fmt, step),
                        style = MaterialTheme.typography.labelLarge,
                        color = TelemetryCyan
                    )
                    TextButton(
                        onClick = {
                            onClearAiSuggestion()
                            onSkip()
                        },
                        modifier = Modifier.testTag("skip_wizard_button")
                    ) {
                        Text(l10nStr(R.string.wizard_skip_btn), color = TextSecondary)
                    }
                }

                LinearProgressIndicator(
                    progress = { step / 3f },
                    color = TelemetryCyan,
                    trackColor = CarbonObsidian,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when (step) {
                        1 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Language, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = l10nStr(R.string.wizard_step1_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = l10nStr(R.string.wizard_step1_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            val langs = listOf(
                                AppLanguage.SYSTEM to l10nStr(R.string.lang_option_system),
                                AppLanguage.RU to l10nStr(R.string.lang_option_ru),
                                AppLanguage.EN to l10nStr(R.string.lang_option_en)
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                langs.forEach { (lang, label) ->
                                    FilterChip(
                                        selected = selectedLang == lang,
                                        onClick = {
                                            selectedLang = lang
                                            onSetLanguage(lang)
                                        },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = TelemetryCyan
                                        ),
                                        modifier = Modifier.testTag("wizard_lang_${lang.name}")
                                    )
                                }
                            }
                        }

                        2 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = TelemetryCyan, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = l10nStr(R.string.wizard_step2_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            OutlinedTextField(
                                value = vinInput,
                                onValueChange = { raw ->
                                    vinInput = raw.uppercase()
                                        .filter { it.isLetterOrDigit() || it == '-' }
                                        .take(24)
                                },
                                label = { Text(l10nStr(R.string.wizard_step2_vin_label)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wizard_vin_input")
                            )

                            detectedMatch?.let { match ->
                                val badgeText = if (match.isFrameNumber && match.suggestedName != null) {
                                    l10nStr(R.string.wizard_frame_detected_fmt, match.suggestedName, match.brand)
                                } else {
                                    l10nStr(R.string.wizard_vin_detected_fmt, match.brand)
                                }
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NominalEmerald,
                                    modifier = Modifier.testTag("wizard_vin_detected_badge")
                                )
                            }

                            Text(
                                text = l10nStr(R.string.wizard_brand_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = TelemetryCyan
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                VehicleCatalog.brands.forEach { brand ->
                                    FilterChip(
                                        selected = selectedBrand == brand,
                                        onClick = {
                                            selectedBrand = brand
                                            VehicleCatalog.modelsForBrand(brand).firstOrNull()?.let { firstModel ->
                                                applyTemplateDefaults(firstModel)
                                            }
                                        },
                                        label = { Text(brand) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TelemetryCyan.copy(alpha = 0.22f),
                                            selectedLabelColor = TelemetryCyan
                                        ),
                                        modifier = Modifier.testTag("wizard_brand_$brand")
                                    )
                                }
                            }

                            Text(
                                text = l10nStr(R.string.wizard_model_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = TelemetryCyan
                            )
                            val models = remember(selectedBrand) { VehicleCatalog.modelsForBrand(selectedBrand) }
                            models.forEach { tmpl ->
                                val isSel = tmpl.id == selectedTemplateId
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = if (isSel) TelemetryCyan.copy(alpha = 0.14f) else CockpitSurfaceElevated,
                                    border = BorderStroke(1.dp, if (isSel) TelemetryCyan else CockpitBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { applyTemplateDefaults(tmpl) }
                                        .testTag("wizard_model_${tmpl.id}")
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = tmpl.modelName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isSel) TelemetryCyan else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${if (isEnglish) tmpl.engineSpecEn else tmpl.engineSpecRu} • ${tmpl.displacementLiters}L • ${tmpl.redlineRpm} RPM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = carName,
                                onValueChange = { carName = it.take(48) },
                                label = { Text(l10nStr(R.string.wizard_car_name_label)) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wizard_car_name_input")
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = dispText,
                                    onValueChange = { dispText = it.take(5) },
                                    label = { Text(l10nStr(R.string.garage_disp_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = redlineText,
                                    onValueChange = { redlineText = it.filter { c -> c.isDigit() }.take(5) },
                                    label = { Text(l10nStr(R.string.garage_redline_label)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = weightText,
                                    onValueChange = { weightText = it.filter { c -> c.isDigit() }.take(5) },
                                    label = { Text("kg") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(0.9f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FuelType.entries.forEach { fuel ->
                                    FilterChip(
                                        selected = selectedFuel == fuel,
                                        onClick = { selectedFuel = fuel },
                                        label = { Text(fuel.localizedLabel(isEnglish)) }
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    onRequestAiSuggestion(
                                        vinInput.trim().takeIf { it.isNotBlank() },
                                        carName.trim().ifBlank { selectedBrand }
                                    )
                                },
                                enabled = aiVehicleSuggestionState !is AiVehicleSuggestionState.Loading &&
                                    (vinInput.isNotBlank() || carName.isNotBlank()),
                                border = BorderStroke(1.dp, MotorsportAmber.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("wizard_ai_vin_btn")
                            ) {
                                if (aiVehicleSuggestionState is AiVehicleSuggestionState.Loading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MotorsportAmber)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(l10nStr(R.string.garage_ai_loading), color = MotorsportAmber)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MotorsportAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(l10nStr(R.string.garage_ai_autofill_btn), color = MotorsportAmber)
                                }
                            }
                            if (aiVehicleSuggestionState is AiVehicleSuggestionState.Error) {
                                Text(
                                    text = aiVehicleSuggestionState.message,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RedlineCrimson
                                )
                            }
                        }

                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = NominalEmerald, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = l10nStr(R.string.wizard_step3_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = CockpitSurfaceElevated,
                                border = BorderStroke(1.dp, CockpitBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = carName.ifBlank { selectedTemplate?.modelName ?: "My Car" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TelemetryCyan
                                    )
                                    if (vinInput.isNotBlank()) {
                                        Text(text = "VIN: $vinInput", style = MaterialTheme.typography.labelMedium, color = NominalEmerald)
                                    }
                                    Text(
                                        text = "${dispText}L • ${selectedFuel.localizedLabel(isEnglish)} • ${redlineText} RPM • ${weightText} kg",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = l10nStr(R.string.wizard_import_oem_switch, oemCountForTemplate),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Switch(
                                    checked = importOemAndMaint,
                                    onCheckedChange = { importOemAndMaint = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = TelemetryCyan)
                                )
                            }

                            Text(
                                text = l10nStr(R.string.wizard_summary_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (step > 1) {
                        OutlinedButton(
                            onClick = { step -= 1 },
                            border = BorderStroke(1.dp, CockpitBorder),
                            modifier = Modifier.testTag("wizard_back_button")
                        ) {
                            Text(l10nStr(R.string.wizard_back_btn), color = TextSecondary)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (step < 3) {
                        Button(
                            onClick = { step += 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                            modifier = Modifier.testTag("wizard_next_button")
                        ) {
                            Text(l10nStr(R.string.wizard_next_btn), color = CarbonObsidian)
                        }
                    } else {
                        Button(
                            onClick = {
                                val disp = dispText.replace(',', '.').toFloatOrNull()?.coerceIn(0.6f, 8.5f) ?: 2.0f
                                val redline = redlineText.toIntOrNull()?.coerceIn(3000, 10000) ?: 6500
                                val weight = weightText.toIntOrNull()?.coerceIn(600, 5000) ?: 1450
                                onClearAiSuggestion()
                                onFinish(
                                    selectedLang,
                                    carName.trim().ifBlank { selectedTemplate?.modelName ?: "My Car" },
                                    vinInput.trim().takeIf { it.isNotBlank() },
                                    selectedTemplate,
                                    disp,
                                    selectedFuel,
                                    redline,
                                    weight,
                                    importOemAndMaint
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NominalEmerald),
                            modifier = Modifier.testTag("finish_wizard_button")
                        ) {
                            Text(l10nStr(R.string.wizard_finish_btn), color = CarbonObsidian)
                        }
                    }
                }
            }
        }
    }
}
