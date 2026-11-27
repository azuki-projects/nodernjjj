package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Primary Brand & IDE Surfaces (Dark Obsidian Palette)
val ObsidianBg = Color(0xFF0B0F14)
val CarbonSurface = Color(0xFF121821)
val CarbonSurfaceVariant = Color(0xFF1A222E)
val CarbonElevated = Color(0xFF212B3A)
val SlateBorder = Color(0xFF293548)

val NodeEmerald = Color(0xFF22C55E)
val NodeEmeraldBright = Color(0xFF4ADE80)
val NodeEmeraldDark = Color(0xFF15803D)
val NodeEmeraldContainer = Color(0xFF0F2E1E)

val CyanPulse = Color(0xFF38BDF8)
val CyanContainer = Color(0xFF0F293A)

val AmberGlow = Color(0xFFFBBF24)
val RoseError = Color(0xFFF87171)
val RoseContainer = Color(0xFF3A161B)

val TextPrimaryDark = Color(0xFFE6EDF3)
val TextSecondaryDark = Color(0xFF8B949E)

// Light Palette (Porcelain Developer)
val PorcelainBg = Color(0xFFF6F8FA)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceVariant = Color(0xFFEAEEF2)
val LightBorder = Color(0xFFD0D7DE)
val NodeEmeraldLightContainer = Color(0xFFDCFCE7)
val CyanLightContainer = Color(0xFFE0F2FE)
val TextPrimaryLight = Color(0xFF1F2328)
val TextSecondaryLight = Color(0xFF656D76)

@Immutable
data class SyntaxColors(
    val editorBackground: Color,
    val gutterBackground: Color,
    val gutterText: Color,
    val activeLineBackground: Color,
    val plainText: Color,
    val keyword: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val function: Color,
    val typeOrClass: Color,
    val propertyKey: Color,
    val punctuation: Color,
    val terminalBg: Color,
    val terminalStdout: Color,
    val terminalStderr: Color,
    val terminalCommand: Color,
    val terminalInfo: Color,
    val terminalSuccess: Color
)

val DarkSyntaxColors = SyntaxColors(
    editorBackground = Color(0xFF0D1117),
    gutterBackground = Color(0xFF0A0E13),
    gutterText = Color(0xFF4B5563),
    activeLineBackground = Color(0xFF161E29),
    plainText = Color(0xFFE6EDF3),
    keyword = Color(0xFFFF7B72),
    string = Color(0xFFA5D6FF),
    number = Color(0xFF79C0FF),
    comment = Color(0xFF8B949E),
    function = Color(0xFFD2A8FF),
    typeOrClass = Color(0xFFFFA657),
    propertyKey = Color(0xFF7EE787),
    punctuation = Color(0xFF9CA3AF),
    terminalBg = Color(0xFF070A0E),
    terminalStdout = Color(0xFFE2E8F0),
    terminalStderr = Color(0xFFF87171),
    terminalCommand = Color(0xFF38BDF8),
    terminalInfo = Color(0xFFFBBF24),
    terminalSuccess = Color(0xFF4ADE80)
)

val LightSyntaxColors = SyntaxColors(
    editorBackground = Color(0xFFFFFFFF),
    gutterBackground = Color(0xFFF6F8FA),
    gutterText = Color(0xFF8C959F),
    activeLineBackground = Color(0xFFF0F6FC),
    plainText = Color(0xFF1F2328),
    keyword = Color(0xFFCF222E),
    string = Color(0xFF0A3069),
    number = Color(0xFF0550AE),
    comment = Color(0xFF6E7781),
    function = Color(0xFF8250DF),
    typeOrClass = Color(0xFF953800),
    propertyKey = Color(0xFF116329),
    punctuation = Color(0xFF57606A),
    terminalBg = Color(0xFF0F172A),
    terminalStdout = Color(0xFFE2E8F0),
    terminalStderr = Color(0xFFF87171),
    terminalCommand = Color(0xFF38BDF8),
    terminalInfo = Color(0xFFFBBF24),
    terminalSuccess = Color(0xFF4ADE80)
)
