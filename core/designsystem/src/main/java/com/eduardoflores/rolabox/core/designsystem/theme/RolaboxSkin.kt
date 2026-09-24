package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable

/**
 * A complete set of theme tokens. [RolaboxTheme] renders whichever skin it is given, so a new look
 * (e.g. the retro click-wheel skin) is a new [RolaboxSkin] instance rather than changes to screens.
 */
@Immutable
data class RolaboxSkin(
    val lightColorScheme: ColorScheme,
    val darkColorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
) {
    fun colorScheme(darkTheme: Boolean): ColorScheme = if (darkTheme) darkColorScheme else lightColorScheme

    companion object {
        val Default = RolaboxSkin(
            lightColorScheme = DefaultLightColorScheme,
            darkColorScheme = DefaultDarkColorScheme,
            typography = DefaultTypography,
            shapes = DefaultShapes,
        )
    }
}
