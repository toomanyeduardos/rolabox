package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
fun RolaboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: RolaboxAccent = RolaboxAccent.Blue,
    skin: RolaboxSkin = RolaboxSkin.Default,
    content: @Composable () -> Unit,
) {
    val metalColors = if (darkTheme) DarkMetalColors else LightMetalColors
    val colorScheme = accent.applyTo(skin.colorScheme(darkTheme), darkTheme)
    val textStyles = remember(skin.typography, metalColors, colorScheme.onSurfaceVariant) {
        RolaboxTextStyles(skin.typography, metalColors, colorScheme.onSurfaceVariant)
    }
    CompositionLocalProvider(
        LocalMetalColors provides metalColors,
        LocalRolaboxTextStyles provides textStyles,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = skin.typography.material,
            shapes = skin.shapes,
            content = content,
        )
    }
}
