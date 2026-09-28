package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The user's choice of primary color. It replaces only the primary roles of whichever skin is in
 * use, so every accent works with every skin. [Blue] matches the default skin's own primary colors.
 */
enum class RolaboxAccent(private val tones: AccentTones) {
    Blue(AccentTones(Blue10, Blue20, Blue30, Blue40, Blue80, Blue90)),
    Green(AccentTones(Green10, Green20, Green30, Green40, Green80, Green90)),
    Purple(AccentTones(Purple10, Purple20, Purple30, Purple40, Purple80, Purple90)),
    Pink(AccentTones(Pink10, Pink20, Pink30, Pink40, Pink80, Pink90)),
    ;

    internal fun applyTo(colorScheme: ColorScheme, darkTheme: Boolean): ColorScheme = with(tones) {
        if (darkTheme) {
            colorScheme.copy(
                primary = tone80,
                onPrimary = tone20,
                primaryContainer = tone30,
                onPrimaryContainer = tone90,
                inversePrimary = tone40,
            )
        } else {
            colorScheme.copy(
                primary = tone40,
                onPrimary = Color.White,
                primaryContainer = tone90,
                onPrimaryContainer = tone10,
                inversePrimary = tone80,
            )
        }
    }
}

internal class AccentTones(
    val tone10: Color,
    val tone20: Color,
    val tone30: Color,
    val tone40: Color,
    val tone80: Color,
    val tone90: Color,
)
