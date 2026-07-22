package com.sherlock.bot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Dark cabinet / official console palette.
 * Ink navy surfaces, paper-white text, restrained signal red.
 */
object Cabinet {
    val Bg = Color(0xFF050A12)
    val BgElevated = Color(0xFF0A1220)
    val Panel = Color(0xFF101A2B)
    val PanelHigh = Color(0xFF172338)
    val Line = Color(0xFF1E2D44)
    val LineStrong = Color(0xFF2C3F5C)

    val Text = Color(0xFFF7F9FC)
    val TextSecondary = Color(0xFFA8B6C8)
    val TextMuted = Color(0xFF6E7F96)

    /** Signal red — CTAs, active mode, focus ring. */
    val Accent = Color(0xFFE11D30)
    val AccentSoft = Color(0xFF2A1218)
    val AccentOn = Color(0xFFFFF7F7)

    val Success = Color(0xFF2A9B6E)
    val Danger = Color(0xFFD06565)
    val Warning = Color(0xFFC9A227)
}

private val ColorScheme = darkColorScheme(
    primary = Cabinet.Accent,
    onPrimary = Cabinet.AccentOn,
    secondary = Cabinet.TextSecondary,
    onSecondary = Cabinet.Bg,
    background = Cabinet.Bg,
    onBackground = Cabinet.Text,
    surface = Cabinet.Panel,
    onSurface = Cabinet.Text,
    surfaceVariant = Cabinet.PanelHigh,
    onSurfaceVariant = Cabinet.TextMuted,
    outline = Cabinet.Line,
    primaryContainer = Cabinet.AccentSoft,
    onPrimaryContainer = Cabinet.Text,
    secondaryContainer = Cabinet.PanelHigh,
    onSecondaryContainer = Cabinet.TextSecondary,
    error = Cabinet.Danger,
    onError = Cabinet.Text,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = 1.2.sp,
        color = Cabinet.Text,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 0.6.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.3.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.8.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.0.sp,
    ),
)

@Composable
fun SherlockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = AppTypography,
        content = content,
    )
}
