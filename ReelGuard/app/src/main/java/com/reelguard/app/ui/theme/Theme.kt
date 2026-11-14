package com.reelguard.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ReelGuardColors {
    val Background = Color(0xFFE5E8ED)
    val Surface = Color(0xFFE8EBF0)
    val SurfaceRaised = Color(0xFFF0F2F5)
    val SurfacePressed = Color(0xFFDCE1E7)
    val Border = Color(0xFFD0D6DE)
    val Highlight = Color(0xFFFFFFFF)
    val Shadow = Color(0xFFB9C1CB)

    val TextPrimary = Color(0xFF303943)
    val TextSecondary = Color(0xFF737E8B)
    val TextMuted = Color(0xFF929BA6)

    val Accent = Color(0xFF566474)
    val AccentDark = Color(0xFF3D4B5A)
    val AccentMuted = Color(0xFFD8DFE7)

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
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.1).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 35.sp,
        letterSpacing = (-0.65).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.35).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
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
    Surface(
        modifier = modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = ReelGuardColors.Shadow.copy(alpha = 0.72f),
            spotColor = ReelGuardColors.Shadow.copy(alpha = 0.8f)
        ),
        shape = shape,
        color = Color.Transparent,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    ReelGuardColors.Highlight.copy(alpha = 0.92f),
                    ReelGuardColors.Border.copy(alpha = 0.88f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(ReelGuardColors.SurfaceRaised, ReelGuardColors.Surface)
                    ),
                    shape
                )
                .padding(padding),
            content = content
        )
    }
}
