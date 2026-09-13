package com.memento.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Warm, keepsake-inspired palette: deep indigo ink, terracotta and amber accents on
 * paper-like surfaces. Reads like a hand-kept analog journal rather than a corporate app.
 */
private val MementoLightColors = lightColorScheme(
    primary = Color(0xFF4A3F8F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE3DEFF),
    onPrimaryContainer = Color(0xFF160A4E),
    secondary = Color(0xFFB5673A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCB),
    onSecondaryContainer = Color(0xFF3C1400),
    tertiary = Color(0xFF8A6D2F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF2A1D00),
    background = Color(0xFFFBF7F0),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFBF7F0),
    onSurface = Color(0xFF1D1B16),
    surfaceVariant = Color(0xFFE7E0D6),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCAC4CF),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceTint = Color(0xFF4A3F8F),
    surfaceBright = Color(0xFFFBF7F0),
    surfaceDim = Color(0xFFDCD7CD),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2E9),
    surfaceContainer = Color(0xFFF1ECE2),
    surfaceContainerHigh = Color(0xFFEBE6DC),
    surfaceContainerHighest = Color(0xFFE5E0D6),
)

private val MementoDarkColors = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF2A1E6B),
    primaryContainer = Color(0xFF41338A),
    onPrimaryContainer = Color(0xFFE3DEFF),
    secondary = Color(0xFFFFB68F),
    onSecondary = Color(0xFF5A1F00),
    secondaryContainer = Color(0xFF7A2E00),
    onSecondaryContainer = Color(0xFFFFDBCB),
    tertiary = Color(0xFFE4C36A),
    onTertiary = Color(0xFF3D2E00),
    tertiaryContainer = Color(0xFF564400),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFE7E2D8),
    surface = Color(0xFF14130F),
    onSurface = Color(0xFFE7E2D8),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4CF),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFFC7BFFF),
    surfaceBright = Color(0xFF3A3833),
    surfaceDim = Color(0xFF0F0E0B),
    surfaceContainerLowest = Color(0xFF0F0E0B),
    surfaceContainerLow = Color(0xFF1B1A15),
    surfaceContainer = Color(0xFF1F1E19),
    surfaceContainerHigh = Color(0xFF2A2823),
    surfaceContainerHighest = Color(0xFF35322D),
)

/**
 * Serif headings evoke a printed journal; body copy stays in the platform sans for legibility.
 */
private val MementoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 52.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
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
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

private val MementoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MementoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MementoDarkColors else MementoLightColors,
        typography = MementoTypography,
        shapes = MementoShapes,
        content = content,
    )
}
