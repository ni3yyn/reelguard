package com.reelguard.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ReelGuardColors {
    val Background = Color(0xFFE4E8EE)
    val Surface = Color(0xFFE7EBF1)
    val SurfaceRaised = Color(0xFFF4F6F9)
    val SurfacePressed = Color(0xFFD8E0E9)
    val Border = Color(0xFFC9D2DD)
    val Highlight = Color(0xFFFFFFFF)
    val Shadow = Color(0xFFAEB9C6)

    val TextPrimary = Color(0xFF2E3946)
    val TextSecondary = Color(0xFF6C7887)
    val TextMuted = Color(0xFF8994A1)

    val Accent = Color(0xFF52677B)
    val AccentDark = Color(0xFF34485A)
    val AccentMuted = Color(0xFFD4DEE8)

    val Emerald = Color(0xFF36836A)
    val EmeraldSurface = Color(0xFFDCEBE4)
    val Amber = Color(0xFF9B743A)
    val AmberSurface = Color(0xFFF0E8D9)
    val Crimson = Color(0xFFAD5D5D)
    val CrimsonSurface = Color(0xFFF0DEDE)
}

private val AppColorScheme = lightColorScheme(
    primary = ReelGuardColors.Accent,
    onPrimary = Color.White,
    secondary = ReelGuardColors.Emerald,
    onSecondary = Color.White,
    background = ReelGuardColors.Background,
    onBackground = ReelGuardColors.TextPrimary,
    surface = ReelGuardColors.Surface,
    onSurface = ReelGuardColors.TextPrimary,
    surfaceVariant = ReelGuardColors.SurfaceRaised,
    onSurfaceVariant = ReelGuardColors.TextSecondary,
    outline = ReelGuardColors.Border,
    error = ReelGuardColors.Crimson,
    onError = Color.White
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.1).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.65).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp
    )
)

@Composable
fun ReelGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content
    )
}

@Composable
fun NeumorphicPanel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(18.dp),
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    elevation: Dp = 13.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        // Offset graphite and pearl shadows create the light-source contrast
        // that makes the surface feel moulded into the same soft material.
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 6.dp, y = 7.dp)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.76f),
                    spotColor = ReelGuardColors.Shadow.copy(alpha = 0.88f)
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = (-5).dp, y = (-5).dp)
                .shadow(
                    elevation = elevation * 0.78f,
                    shape = shape,
                    clip = false,
                    ambientColor = Color.White.copy(alpha = 0.96f),
                    spotColor = Color.White.copy(alpha = 0.94f)
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFFF7F9FB),
                            0.48f to ReelGuardColors.SurfaceRaised,
                            1f to Color(0xFFE2E7EE)
                        )
                    ),
                    shape
                )
                .border(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.98f),
                            ReelGuardColors.Border.copy(alpha = 0.88f),
                            Color.White.copy(alpha = 0.58f)
                        )
                    ),
                    shape
                )
                .padding(padding),
            content = content
        )
    }
}
