package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.obd.i18n.l10nStr
import com.example.obd.model.AlertLevel
import com.example.obd.model.ObdPid
import com.example.ui.ObdUiState
import com.example.ui.theme.CarbonObsidian
import com.example.ui.theme.CockpitBorder
import com.example.ui.theme.MotorsportAmber
import com.example.ui.theme.NominalEmerald
import com.example.ui.theme.RedlineCrimson
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun HudOverlayDialog(
    uiState: ObdUiState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var mirrorMode by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val isEng = uiState.settings.isEnglish
    val locale = if (isEng) Locale.US else Locale.forLanguageTag("ru-RU")
    val rpmReading = uiState.readings[ObdPid.ENGINE_RPM.modeAndPid]
    val speedReading = uiState.readings[ObdPid.VEHICLE_SPEED.modeAndPid]
    val coolantReading = uiState.readings[ObdPid.COOLANT_TEMP.modeAndPid]
    val voltageReading = uiState.readings[ObdPid.CONTROL_MODULE_VOLTAGE.modeAndPid]

    val rpm = rpmReading?.value ?: 0f
    val speed = speedReading?.value ?: 0f
    val shiftActive = uiState.settings.shiftLightEnabled && rpm >= uiState.settings.shiftLightRpm
    val rpmFraction = (rpm / (uiState.activeVehicleProfile?.redlineRpm?.toFloat() ?: 7000f)).coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (shiftActive) Color(0xFF28050A) else Color(0xFF000000))
                .padding(18.dp)
                .testTag("hud_overlay_dialog")
        ) {
            // Controls row (always non-mirrored so user can tap comfortably)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = mirrorMode,
                    onClick = { mirrorMode = !mirrorMode },
                    label = { Text(l10nStr(R.string.hud_mirror_toggle)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Flip,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TelemetryCyan.copy(alpha = 0.25f),
                        selectedLabelColor = TelemetryCyan,
                        selectedLeadingIconColor = TelemetryCyan
                    ),
                    modifier = Modifier.testTag("hud_mirror_toggle_chip")
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("hud_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = l10nStr(R.string.action_close),
                        tint = Color.White
                    )
                }
            }

            // Projected telemetry content (flips vertically in windshield mirror mode)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleY = if (mirrorMode) -1f else 1f
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (shiftActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RedlineCrimson
                    ) {
                        Text(
                            text = l10nStr(R.string.hud_shift_badge),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }
                }

                // Giant Speed Display
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (speedReading != null) "${speed.roundToInt()}" else "---",
                        fontSize = 112.sp,
                        fontWeight = FontWeight.Black,
                        color = if (speedReading?.alertLevel == AlertLevel.CRITICAL) RedlineCrimson else Color(0xFF00F0FF)
                    )
                    Text(
                        text = if (isEng) "KM/H" else "КМ/Ч",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextSecondary
                    )
                }

                // High-contrast RPM bar
                Column(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RPM",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = if (rpmReading != null) "${rpm.roundToInt()}" else "----",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                shiftActive -> RedlineCrimson
                                rpmFraction > 0.78f -> MotorsportAmber
                                else -> Color.White
                            }
                        )
                    }
                    LinearProgressIndicator(
                        progress = { rpmFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp),
                        color = when {
                            shiftActive -> RedlineCrimson
                            rpmFraction > 0.78f -> MotorsportAmber
                            else -> TelemetryCyan
                        },
                        trackColor = Color(0xFF1A2230)
                    )
                }

                // Bottom secondary metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    HudMetricPill(
                        label = if (isEng) "COOLANT" else "ОЖ",
                        value = coolantReading?.let {
                            "${ObdPid.COOLANT_TEMP.formatValue(it.value, uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)}${ObdPid.COOLANT_TEMP.displayUnit(uiState.settings.useFahrenheit, uiState.settings.pressureUnit, isEng)}"
                        } ?: "--",
                        color = if (coolantReading?.alertLevel == AlertLevel.CRITICAL) RedlineCrimson else NominalEmerald
                    )
                    HudMetricPill(
                        label = if (isEng) "VOLT" else "БОРТ",
                        value = voltageReading?.let {
                            String.format(locale, "%.1f %s", it.value, if (isEng) "V" else "В")
                        } ?: "--",
                        color = TelemetryCyan
                    )
                    val trip = uiState.tripStats
                    HudMetricPill(
                        label = if (isEng) "FUEL" else "РАСХ.",
                        value = trip.instantLPer100Km?.let {
                            String.format(locale, "%.1f", it)
                        } ?: trip.instantLPerHour?.let {
                            String.format(locale, "%.1f/h", it)
                        } ?: "--",
                        color = MotorsportAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun HudMetricPill(
    label: String,
    value: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0E131C),
        border = BorderStroke(1.dp, CockpitBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
