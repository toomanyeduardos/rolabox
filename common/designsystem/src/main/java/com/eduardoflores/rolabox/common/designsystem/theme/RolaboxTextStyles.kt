package com.eduardoflores.rolabox.common.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/**
 * [RolaboxTypography]'s roles with their colors, resolved for the current light or dark mode.
 * Screens pick a role by name, as in `Text(title, style = RolaboxType.styles.screenTitle)`, and set
 * nothing else about how the text looks. [RolaboxTheme] builds and provides this.
 */
@Immutable
class RolaboxTextStyles internal constructor(
    typography: RolaboxTypography,
    colors: MetalColors,
    onSurfaceVariant: Color,
) {
    /** The heading of an account screen. */
    val screenTitle = typography.screenTitle.copy(color = colors.ink)

    /** The line under [screenTitle]. */
    val screenSubtitle = typography.screenSubtitle.copy(color = colors.muted)

    /**
     * The label of a button whose look a third party dictates, such as Google's. It ignores the skin's
     * font. Its color is the ink, and a brand with its own colors passes `color` to `Text`.
     */
    val brandButtonLabel = typography.brandButtonLabel.copy(color = colors.ink)

    internal val buttonLabel = typography.buttonLabel.copy(color = colors.onAccent)
    internal val keyLabel = typography.keyLabel.copy(color = colors.ink)
    internal val wordmark = typography.wordmark.copy(color = colors.ink)
    internal val lcd = typography.lcd.copy(color = colors.lcdInk)

    /** The LCD's error tag, which is the screen color on an ink background. */
    internal val lcdBadge = typography.lcd.copy(color = colors.lcd)
    internal val fieldInput = typography.fieldInput.copy(color = colors.ink)
    internal val fieldInputMasked = typography.fieldInputMasked.copy(color = colors.ink)
    internal val fieldLabel = typography.fieldLabel.copy(color = colors.muted)
    internal val fieldToggle = typography.fieldToggle.copy(color = colors.muted)
    internal val fieldError = typography.fieldError.copy(color = colors.error)
    internal val errorBadge = typography.errorBadge.copy(color = colors.onError)
    internal val caption = typography.caption.copy(color = colors.muted)
    internal val linkPrompt = typography.linkPrompt.copy(color = colors.muted)
    internal val linkStrong = typography.linkStrong.copy(color = colors.accentText)
    internal val linkUnderlined = typography.linkUnderlined.copy(color = colors.ink)
    internal val action = typography.action.copy(color = colors.accentText)
    internal val actionCompact = typography.actionCompact.copy(color = colors.accentText)

    /** A status line on a Material surface, such as the loading message. */
    internal val message = typography.message.copy(color = onSurfaceVariant)

    /** The wheel's MENU label. It doesn't follow the font scale: the wheel draws it at this size in dp. */
    internal val wheelLabel = typography.wheelLabel.copy(color = colors.wheelGlyph)

    /** The title in the header of the device's display. It is on the display's white, in both themes. */
    internal val displayTitle = typography.displayTitle.copy(color = colors.displayInk)

    /** A row of a list on the display. The selected row's text is [MetalColors.onSelection]. */
    internal val displayRow = typography.displayRow.copy(color = colors.displayInk)

    /** The `›` on a row that opens a submenu. */
    internal val displayChevron = typography.displayChevron.copy(color = colors.displayInk)

    /** Small monospaced text on the display, such as the position and the times. It's the display's secondary ink. */
    val displayMeta = typography.displayMeta.copy(color = colors.displayMuted)

    /** The title of the song on Now Playing. */
    val displayHeadline = typography.displayHeadline.copy(color = colors.displayInk)

    /** The artist on Now Playing. */
    val displayBody = typography.displayBody.copy(color = colors.displayInk)

    /** The album on Now Playing. It's the display's secondary ink. */
    val displayCaption = typography.displayCaption.copy(color = colors.displayMuted)

    /** Every role with its name, for the type specimen. */
    internal val all: List<Pair<String, TextStyle>>
        get() = listOf(
            "screenTitle" to screenTitle,
            "screenSubtitle" to screenSubtitle,
            "buttonLabel" to buttonLabel,
            "keyLabel" to keyLabel,
            "brandButtonLabel" to brandButtonLabel,
            "wordmark" to wordmark,
            "fieldInput" to fieldInput,
            "fieldInputMasked" to fieldInputMasked,
            "fieldLabel" to fieldLabel,
            "fieldToggle" to fieldToggle,
            "fieldError" to fieldError,
            "errorBadge" to errorBadge,
            "caption" to caption,
            "linkPrompt" to linkPrompt,
            "linkStrong" to linkStrong,
            "linkUnderlined" to linkUnderlined,
            "action" to action,
            "actionCompact" to actionCompact,
            "message" to message,
            "lcd" to lcd,
            "lcdBadge" to lcdBadge,
            "wheelLabel" to wheelLabel,
            "displayTitle" to displayTitle,
            "displayRow" to displayRow,
            "displayChevron" to displayChevron,
            "displayMeta" to displayMeta,
            "displayHeadline" to displayHeadline,
            "displayBody" to displayBody,
            "displayCaption" to displayCaption,
        )
}

internal val LocalRolaboxTextStyles = staticCompositionLocalOf {
    RolaboxTextStyles(DefaultTypography, LightMetalColors, DefaultLightColorScheme.onSurfaceVariant)
}

/** Reads the styles [RolaboxTheme] provides. */
object RolaboxType {
    val styles: RolaboxTextStyles
        @Composable get() = LocalRolaboxTextStyles.current
}
