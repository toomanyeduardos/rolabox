package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxSkin
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxType

/**
 * Every text role in one place, in a skin, so a change to the type shows up here first. Each row is
 * the role's name, in [RolaboxType.styles]'s caption, then a sample in the role.
 */
@Composable
private fun TypeSpecimen(skin: RolaboxSkin) {
    RolaboxTheme(skin = skin) {
        val styles = RolaboxType.styles
        val colors = RolaboxMetal.colors
        Column(
            Modifier.brushedMetal().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            styles.all.forEach { (name, style) ->
                Column {
                    Text(name, style = styles.caption)
                    // The LCD roles are drawn on the screen they're made for.
                    val onLcd = name == "lcd"
                    val onInk = name == "lcdBadge"
                    Text(
                        text = if (onLcd || onInk) "SIGN IN" else "Rolabox sample text 0123",
                        style = style,
                        modifier = when {
                            onLcd -> Modifier.background(colors.lcd).padding(4.dp)
                            onInk -> Modifier.background(colors.lcdInk).padding(4.dp)
                            else -> Modifier
                        },
                    )
                }
            }
            // The Material slots that Material components read, such as the top bar title and buttons.
            with(MaterialTheme.typography) {
                Text("material.titleLarge (top bar title)", style = titleLarge)
                Text("material.labelLarge (text button)", style = labelLarge)
                Text("material.bodyLarge (ambient text)", style = bodyLarge)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DefaultSkinTypeSpecimenPreview() {
    TypeSpecimen(RolaboxSkin.Default)
}
