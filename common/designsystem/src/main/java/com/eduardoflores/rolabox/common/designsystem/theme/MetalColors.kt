package com.eduardoflores.rolabox.common.designsystem.theme

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.eduardoflores.rolabox.common.designsystem.R

/**
 * The brushed-aluminum look of the account screens: metal body, recessed fields, raised keys and
 * the LCD strip. These are separate from [androidx.compose.material3.ColorScheme] because Material
 * has no role for them. [RolaboxTheme] provides the set for its light or dark mode.
 */
@Immutable
data class MetalColors(
    /** Whether this is the dark palette, for parts that pick between a light and a dark asset. */
    val isDark: Boolean,
    /** The tiled brushed-aluminum texture that paints the body. */
    @DrawableRes val texture: Int,
    /** The body's flat color, which the texture is matched to. It's a resource so the splash screen can use it too. */
    @ColorRes val body: Int,
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
    /** The wheel's ring. */
    val wheelRing: Color,
    val wheelRingEdge: Color,
    /** The wheel's MENU label and its ⏮ ⏭ ⏯ icons. */
    val wheelGlyph: Color,
    /** The accent for text links. It's darker (light) or lighter (dark) than [accent], so it reads on the metal. */
    val accentText: Color,
    val accent: Color = Color(0xFFE4572E),
    val accentTop: Color = Color(0xFFEE6A43),
    val accentBottom: Color = Color(0xFFD94B23),
    /** Text and icons on the accent fill. */
    val onAccent: Color = Color.White,
    /** Text on the [error] fill, such as the "!" badge. */
    val onError: Color = Color.White,
    /** The glossy selected row of a device display, top to bottom. */
    val selection: List<Color> = listOf(Color(0xFF5AA6EE), Color(0xFF2F86DB), Color(0xFF1F6FC4), Color(0xFF1A62B0)),
    /** Text on the selected row. */
    val onSelection: Color = Color.White,
    /** The frame around the device's display. It's the same in light and dark. */
    val displayBezel: Color = Color(0xFF1D1D1F),
    /**
     * The device's display stays white on both finishes, so its text reads the same on either. These
     * five are its screen, its header, its text, its secondary text and its dividers.
     */
    val displayScreen: Color = Color(0xFFFFFFFF),
    val displayHeader: Color = Color(0xFFECECED),
    val displayInk: Color = Color(0xFF0E0E0F),
    val displayMuted: Color = Color(0xFF5F6368),
    val displayRule: Color = Color(0x1F000000),
    /** The progress bar's fill on the display, in the time look. The track behind it is [displayRule]. */
    val displayFill: Color = Color(0xFF3C3F44),
    /** The aluminum tile of the default cover art, top to bottom: the same as the launcher icon's. */
    val displayArt: Color = Color(0xFFE9EAEC),
    val displayArtEnd: Color = Color(0xFFC3C6CA),
    /** The strength meter's color for levels 1 to 4. */
    val strength: List<Color> = listOf(Color(0xFFC8322B), Color(0xFFE0A21B), Color(0xFF6FBF5A), Color(0xFF2E9E57)),
)

/** Light: anodized aluminum. */
internal val LightMetalColors = MetalColors(
    isDark = false,
    texture = R.drawable.ds_aluminum_silver,
    body = R.color.ds_metal_body,
    ink = Color(0xFF1C1D1F),
    muted = Color(0xFF43464B),
    rule = Color(0x29000000),
    error = Color(0xFFA8261F),
    well = Color(0xFFEEEFF0),
    wellBorder = Color(0x1A000000),
    keyTop = Color(0xFFF6F6F7),
    keyBottom = Color(0xFFDADCDF),
    keyBorder = Color(0x1F000000),
    bezel = Color(0xFF3A3D3A),
    lcd = Color(0xFFC7CFBF),
    lcdInk = Color(0xFF27301F),
    wheelRing = Color(0xFFF7F8FA),
    wheelRingEdge = Color(0x12000000),
    wheelGlyph = Color(0xFFA3AAB3),
    accentText = Color(0xFFA83A18),
)

/** Dark: gunmetal aluminum. */
internal val DarkMetalColors = MetalColors(
    isDark = true,
    texture = R.drawable.ds_aluminum_graphite,
    body = R.color.ds_metal_body,
    ink = Color(0xFFF1F1F2),
    muted = Color(0xFFC6C9CE),
    rule = Color(0x2EFFFFFF),
    error = Color(0xFFFF8A7F),
    well = Color(0xFF18191B),
    wellBorder = Color(0x80000000),
    keyTop = Color(0xFF4A4C51),
    keyBottom = Color(0xFF323438),
    keyBorder = Color(0x80000000),
    bezel = Color(0xFF0C0D0C),
    lcd = Color(0xFF1D2A22),
    lcdInk = Color(0xFF9FE3B6),
    wheelRing = Color(0xFF2F3032),
    wheelRingEdge = Color(0x2E000000),
    wheelGlyph = Color(0xFFF1F1F2),
    accentText = Color(0xFFFF9A6E),
)

// Read through RolaboxMetal.colors, and provided only by RolaboxTheme.
internal val LocalMetalColors = staticCompositionLocalOf { LightMetalColors }

object RolaboxMetal {
    val colors: MetalColors
        @Composable get() = LocalMetalColors.current
}
