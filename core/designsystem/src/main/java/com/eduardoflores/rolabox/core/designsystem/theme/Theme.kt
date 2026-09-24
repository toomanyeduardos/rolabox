package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun RolaboxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    skin: RolaboxSkin = RolaboxSkin.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = skin.colorScheme(darkTheme),
        typography = skin.typography,
        shapes = skin.shapes,
        content = content,
    )
}
