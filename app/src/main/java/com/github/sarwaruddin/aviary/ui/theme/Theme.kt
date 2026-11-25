package com.github.sarwaruddin.aviary.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Field-guide look: paper pages, moss and fern greens, ochre and rust accents,
 * serif headings like an old printed guide, italic scientific names.
 */
@Immutable
data class FieldColors(
    val paper: Color,        // page background
    val card: Color,         // plate / card surface
    val ink: Color,          // body text
    val inkSoft: Color,      // secondary text
    val rule: Color,         // hairlines, borders
    val moss: Color,         // primary
    val fern: Color,         // secondary green
    val ochre: Color,        // highlights, winter visitors
    val rust: Color,         // threatened species
    val sky: Color,          // water birds / passage, used sparingly
)

val LightField = FieldColors(
    paper = Color(0xFFF3ECDB), card = Color(0xFFFBF7EC), ink = Color(0xFF2A2A22),
    inkSoft = Color(0xFF6B6655), rule = Color(0xFFD9CFB6), moss = Color(0xFF3F5A36),
    fern = Color(0xFF6E8F4E), ochre = Color(0xFFB7832F), rust = Color(0xFF9C4A2F), sky = Color(0xFF4F7480),
)

val DarkField = FieldColors(
    paper = Color(0xFF1B1F18), card = Color(0xFF242A20), ink = Color(0xFFE9E3D2),
    inkSoft = Color(0xFFA9A28C), rule = Color(0xFF3A4234), moss = Color(0xFF9DB98A),
    fern = Color(0xFF7FA35E), ochre = Color(0xFFD9A54E), rust = Color(0xFFD27A5A), sky = Color(0xFF86AEB8),
)

val LocalField = staticCompositionLocalOf { LightField }

private fun scheme(f: FieldColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = f.moss, onPrimary = f.paper,
        primaryContainer = f.fern.copy(alpha = 0.25f), onPrimaryContainer = f.ink,
        secondary = f.ochre, onSecondary = f.paper,
        secondaryContainer = f.ochre.copy(alpha = 0.22f), onSecondaryContainer = f.ink,
        tertiary = f.rust, onTertiary = f.paper,
        background = f.paper, onBackground = f.ink,
        surface = f.paper, onSurface = f.ink,
        surfaceVariant = f.card, onSurfaceVariant = f.inkSoft,
        surfaceContainer = f.card, surfaceContainerLow = f.card, surfaceContainerHigh = f.card,
        surfaceContainerLowest = f.paper, surfaceContainerHighest = f.card,
        outline = f.rule, outlineVariant = f.rule,
        error = f.rust,
    )
}

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

private val FieldTypography = Typography(
    displaySmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 23.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Serif, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)

/** Italic style for binomials: "Copsychus saularis". */
val SciName = TextStyle(fontFamily = Serif, fontStyle = FontStyle.Italic)

@Composable
fun AviaryTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val f = if (dark) DarkField else LightField
    androidx.compose.runtime.CompositionLocalProvider(LocalField provides f) {
        MaterialTheme(colorScheme = scheme(f, dark), typography = FieldTypography, content = content)
    }
}

object Field {
    val colors: FieldColors
        @Composable get() = LocalField.current
}
