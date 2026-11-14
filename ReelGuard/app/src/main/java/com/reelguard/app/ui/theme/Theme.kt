package com.reelguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object ReelGuardColors {
    val Background = Color(0xFF0B0E14)
    val Surface = Color(0xFF131822)
    val SurfaceElevated = Color(0xFF1B2230)
    val Border = Color(0xFF263042)
    val TextPrimary = Color(0xFFF3F6FB)
    val TextSecondary = Color(0xFF9AA5B8)
    val Accent = Color(0xFF4F8CFF)
    val AccentMuted = Color(0xFF1A2D4D)
    val Emerald = Color(0xFF22C55E)
    val EmeraldSurface = Color(0xFF102A1C)
    val Crimson = Color(0xFFEF4444)
    val CrimsonSurface = Color(0xFF2E1518)
    val Amber = Color(0xFFF59E0B)
    val AmberSurface = Color(0xFF2E2210)
}

private val DarkColorScheme = darkColorScheme(
    primary = ReelGuardColors.Accent,
    onPrimary = Color.White,
    secondary = ReelGuardColors.SurfaceElevated,
    onSecondary = ReelGuardColors.TextPrimary,
    background = ReelGuardColors.Background,
    onBackground = ReelGuardColors.TextPrimary,
    surface = ReelGuardColors.Surface,
    onSurface = ReelGuardColors.TextPrimary,
    surfaceVariant = ReelGuardColors.SurfaceElevated,
    onSurfaceVariant = ReelGuardColors.TextSecondary,
    outline = ReelGuardColors.Border,
    error = ReelGuardColors.Crimson,
    onError = Color.White
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun ReelGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
