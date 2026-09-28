package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun RolaboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: RolaboxAccent = RolaboxAccent.Blue,
    skin: RolaboxSkin = RolaboxSkin.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = accent.applyTo(skin.colorScheme(darkTheme), darkTheme),
        typography = skin.typography,
        shapes = skin.shapes,
        content = content,
    )
}
