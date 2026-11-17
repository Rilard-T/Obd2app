package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AutomotiveDarkColorScheme = darkColorScheme(
    primary = TelemetryCyan,
    onPrimary = CarbonObsidian,
    primaryContainer = TelemetryCyanContainer,
    onPrimaryContainer = TelemetryCyan,
    secondary = MotorsportAmber,
    onSecondary = CarbonObsidian,
    secondaryContainer = MotorsportAmberContainer,
    onSecondaryContainer = MotorsportAmber,
    tertiary = NominalEmerald,
    onTertiary = CarbonObsidian,
    tertiaryContainer = NominalEmeraldContainer,
    onTertiaryContainer = NominalEmerald,
    background = CarbonObsidian,
    onBackground = TextPrimary,
    surface = CockpitSurface,
    onSurface = TextPrimary,
    surfaceVariant = CockpitSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CockpitBorder,
    outlineVariant = CockpitBorder.copy(alpha = 0.6f),
    error = RedlineCrimson,
    onError = TextPrimary,
    errorContainer = RedlineCrimsonContainer,
    onErrorContainer = RedlineCrimson
)

val ObdShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ObdAutoTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AutomotiveDarkColorScheme,
        typography = ObdTypography,
        shapes = ObdShapes,
        content = content
    )
}
