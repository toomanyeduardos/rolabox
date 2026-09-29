package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun RolaboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: RolaboxAccent = RolaboxAccent.Blue,
    skin: RolaboxSkin = RolaboxSkin.Default,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalMetalColors provides if (darkTheme) DarkMetalColors else LightMetalColors) {
        MaterialTheme(
            colorScheme = accent.applyTo(skin.colorScheme(darkTheme), darkTheme),
            typography = skin.typography,
            shapes = skin.shapes,
            content = content,
        )
    }
}
