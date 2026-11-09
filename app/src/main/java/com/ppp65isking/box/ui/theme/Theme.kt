package com.ppp65isking.box.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Box colors: orange, green, blue. NO purple anywhere — Anthony's call.
// Primary = vivid orange, secondary = fresh green, tertiary = bright blue.
//
// NOTE: this used to be Material 3 Expressive (MaterialExpressiveTheme /
// MotionScheme.expressive() / expressiveLightColorScheme on the material3
// 1.5.0 line), but that line requires compileSdk 37 and the API-37 platform
// is not downloadable in CI yet (not even on the canary channel), so the
// build could never pass. Stable M3 keeps the same palette and the same big
// rounded shapes — revisit Expressive once the API-37 platform ships.
private val Orange = Color(0xFFF97316)
private val OrangeLight = Color(0xFFFFB74D)
private val Green = Color(0xFF16A34A)
private val GreenLight = Color(0xFF81C784)
private val Blue = Color(0xFF2563EB)
private val BlueLight = Color(0xFF64B5F6)

private val BoxLightColors: ColorScheme = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE0B2),
    onPrimaryContainer = Color(0xFF7A2E00),
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8E6C9),
    onSecondaryContainer = Color(0xFF0D3B1E),
    tertiary = Blue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBBDEFB),
    onTertiaryContainer = Color(0xFF0D2B4D),
    background = Color(0xFFFFFBF5),
    onBackground = Color(0xFF201A15),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF201A15),
    surfaceVariant = Color(0xFFFFEEDD),
    onSurfaceVariant = Color(0xFF5D4037),
    outline = Color(0xFFD7CCC8),
    outlineVariant = Color(0xFFEFEBE9),
)

private val BoxDarkColors: ColorScheme = darkColorScheme(
    primary = OrangeLight,
    onPrimary = Color(0xFF3E1F00),
    primaryContainer = Color(0xFF7A3A00),
    onPrimaryContainer = Color(0xFFFFE0B2),
    secondary = GreenLight,
    onSecondary = Color(0xFF0D3B1E),
    secondaryContainer = Color(0xFF1B5E20),
    onSecondaryContainer = Color(0xFFC8E6C9),
    tertiary = BlueLight,
    onTertiary = Color(0xFF0D2B4D),
    tertiaryContainer = Color(0xFF1565C0),
    onTertiaryContainer = Color(0xFFBBDEFB),
    background = Color(0xFF141210),
    onBackground = Color(0xFFF5EFE8),
    surface = Color(0xFF1E1B18),
    onSurface = Color(0xFFF5EFE8),
    surfaceVariant = Color(0xFF2E2823),
    onSurfaceVariant = Color(0xFFD7CCC8),
    outline = Color(0xFF4E463F),
    outlineVariant = Color(0xFF2E2823),
)

// Big, round shape scale — the expressive look, on stable shapes.
private val BoxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BoxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) BoxDarkColors else BoxLightColors,
        shapes = BoxShapes,
        content = content,
    )
}
