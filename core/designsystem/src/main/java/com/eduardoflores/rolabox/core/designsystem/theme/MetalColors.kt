package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The brushed-aluminum look of the account screens: metal body, recessed fields, raised keys and
 * the LCD strip. These are separate from [androidx.compose.material3.ColorScheme] because Material
 * has no role for them. [RolaboxTheme] provides the set for its light or dark mode.
 */
@Immutable
data class MetalColors(
    /** Whether this is the dark palette, for parts that pick between a light and a dark asset. */
    val isDark: Boolean,
    val bodyTop: Color,
    val bodyBottom: Color,
    val ink: Color,
    val muted: Color,
    val rule: Color,
    val error: Color,
    val well: Color,
    val wellBorder: Color,
    val keyTop: Color,
    val keyBottom: Color,
    val keyBorder: Color,
    val bezel: Color,
    val lcd: Color,
    val lcdInk: Color,
    val accent: Color = Color(0xFFE4572E),
    val accentTop: Color = Color(0xFFEE6A43),
    val accentBottom: Color = Color(0xFFD94B23),
    /** The strength meter's color for levels 1 to 4. */
    val strength: List<Color> = listOf(Color(0xFFC8322B), Color(0xFFE0A21B), Color(0xFF6FBF5A), Color(0xFF2E9E57)),
)

/** Light: anodized aluminum. */
val LightMetalColors = MetalColors(
    isDark = false,
    bodyTop = Color(0xFFE7E8EA),
    bodyBottom = Color(0xFFD3D5D8),
    ink = Color(0xFF1C1D1F),
    muted = Color(0xFF5B5E63),
    rule = Color(0x24000000),
    error = Color(0xFFC8322B),
    well = Color(0xFFEEEFF0),
    wellBorder = Color(0x1A000000),
    keyTop = Color(0xFFF6F6F7),
    keyBottom = Color(0xFFDADCDF),
    keyBorder = Color(0x1F000000),
    bezel = Color(0xFF3A3D3A),
    lcd = Color(0xFFC7CFBF),
    lcdInk = Color(0xFF27301F),
)

/** Dark: gunmetal aluminum. */
val DarkMetalColors = MetalColors(
    isDark = true,
    bodyTop = Color(0xFF36383C),
    bodyBottom = Color(0xFF222326),
    ink = Color(0xFFECEEF0),
    muted = Color(0xFFA0A4AB),
    rule = Color(0x24FFFFFF),
    error = Color(0xFFFF6B5E),
    well = Color(0xFF18191B),
    wellBorder = Color(0x80000000),
    keyTop = Color(0xFF4A4C51),
    keyBottom = Color(0xFF323438),
    keyBorder = Color(0x80000000),
    bezel = Color(0xFF0C0D0C),
    lcd = Color(0xFF1D2A22),
    lcdInk = Color(0xFF9FE3B6),
)

// Read through RolaboxMetal.colors, and provided only by RolaboxTheme.
val LocalMetalColors = staticCompositionLocalOf { LightMetalColors }

object RolaboxMetal {
    val colors: MetalColors
        @Composable get() = LocalMetalColors.current
}
