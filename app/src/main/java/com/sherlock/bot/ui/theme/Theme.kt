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

private val Ink = Color(0xFF0B1220)
private val Panel = Color(0xFF141C2B)
private val PanelElevated = Color(0xFF1C2738)
private val Amber = Color(0xFFC9A227)
private val AmberSoft = Color(0xFFE8D5A3)
private val Fog = Color(0xFFB7C0CC)
private val Mist = Color(0xFF8A94A6)
private val UserBubble = Color(0xFF243447)
private val BotBubble = Color(0xFF182232)

private val ColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = AmberSoft,
    onSecondary = Ink,
    background = Ink,
    onBackground = Fog,
    surface = Panel,
    onSurface = Fog,
    surfaceVariant = PanelElevated,
    onSurfaceVariant = Mist,
    outline = Color(0xFF334155),
    primaryContainer = UserBubble,
    secondaryContainer = BotBubble,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
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
