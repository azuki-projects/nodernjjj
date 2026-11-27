package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = NodeEmerald,
    onPrimary = Color(0xFF041F0E),
    primaryContainer = NodeEmeraldContainer,
    onPrimaryContainer = NodeEmeraldBright,
    secondary = CyanPulse,
    onSecondary = Color(0xFF05202E),
    secondaryContainer = CyanContainer,
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AmberGlow,
    onTertiary = Color(0xFF281A00),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = CarbonSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = CarbonSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = CarbonSurface,
    surfaceContainerHigh = CarbonSurfaceVariant,
    surfaceContainerHighest = CarbonElevated,
    outline = SlateBorder,
    outlineVariant = Color(0xFF1E293B),
    error = RoseError,
    onError = Color(0xFF3A080C),
    errorContainer = RoseContainer,
    onErrorContainer = Color(0xFFFECACA)
)

private val StudioLightColorScheme = lightColorScheme(
    primary = NodeEmeraldDark,
    onPrimary = Color.White,
    primaryContainer = NodeEmeraldLightContainer,
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = Color(0xFFD97706),
    onTertiary = Color.White,
    background = PorcelainBg,
    onBackground = TextPrimaryLight,
    surface = PaperSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperSurfaceVariant,
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = LightBorder,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

val LocalSyntaxColors = staticCompositionLocalOf { DarkSyntaxColors }

@Composable
fun NodeStudioTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }

    val colorScheme = if (useDark) StudioDarkColorScheme else StudioLightColorScheme
    val syntaxColors = if (useDark) DarkSyntaxColors else LightSyntaxColors

    CompositionLocalProvider(LocalSyntaxColors provides syntaxColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = StudioShapes,
            content = content
        )
    }
}
