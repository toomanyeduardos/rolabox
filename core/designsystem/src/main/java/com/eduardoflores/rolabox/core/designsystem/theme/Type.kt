@file:Suppress("MatchingDeclarationName") // Type.kt is the conventional name for the typography file.

package com.eduardoflores.rolabox.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The one place that defines how text looks. [material] is the Material 3 scale, which Material
 * components (top bar, buttons) read through `MaterialTheme.typography`. The other styles are the
 * Rolabox roles that Material has no slot for, or that differ from it. Every role derives from
 * [material], so the font families are the only thing a skin has to swap.
 *
 * The styles carry no color. [RolaboxTextStyles] adds it, because colors follow light/dark.
 */
@Immutable
@Suppress("LongParameterList") // One parameter per role: it is a bag of tokens, like MetalColors.
class RolaboxTypography internal constructor(
    val material: Typography,
    internal val screenTitle: TextStyle,
    internal val screenSubtitle: TextStyle,
    internal val buttonLabel: TextStyle,
    internal val keyLabel: TextStyle,
    internal val brandButtonLabel: TextStyle,
    internal val wordmark: TextStyle,
    internal val lcd: TextStyle,
    internal val fieldInput: TextStyle,
    internal val fieldInputMasked: TextStyle,
    internal val fieldLabel: TextStyle,
    internal val fieldToggle: TextStyle,
    internal val fieldError: TextStyle,
    internal val errorBadge: TextStyle,
    internal val caption: TextStyle,
    internal val linkPrompt: TextStyle,
    internal val linkStrong: TextStyle,
    internal val linkUnderlined: TextStyle,
    internal val action: TextStyle,
    internal val actionCompact: TextStyle,
    internal val message: TextStyle,
)

/**
 * Starts from the Material 3 scale. A skin with its own fonts swaps [fontFamily] for the text and
 * [monoFamily] for the LCD and the other monospaced roles.
 */
internal fun rolaboxTypography(
    fontFamily: FontFamily = FontFamily.Default,
    monoFamily: FontFamily = FontFamily.Monospace,
): RolaboxTypography {
    val material = materialTypography(fontFamily)
    // MaterialTheme makes bodyLarge the ambient style, so text that set only some fields inherited
    // the rest (line height, letter spacing) from it. The roles that did that derive from it here,
    // which keeps them identical to what they were.
    val body = material.bodyLarge.merge(AmbientTextDefaults)
    val fieldInput = TextStyle(fontFamily = fontFamily, fontSize = 15.5.sp, letterSpacing = 0.em)
    return RolaboxTypography(
        material = material,
        screenTitle = body.copy(
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.03).em,
            lineHeight = 32.sp,
        ),
        screenSubtitle = body.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
        buttonLabel = body.copy(fontWeight = FontWeight.Bold),
        // Tabular digits, so a countdown on a key doesn't jitter as the digits change.
        keyLabel = body.copy(fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        // Google's branding rules set this label: 14/20 Medium in its own font, not the skin's.
        brandButtonLabel = AmbientTextDefaults.copy(
            fontFamily = FontFamily.Default,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        wordmark = TextStyle(
            fontFamily = fontFamily,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.04).em,
        ),
        lcd = TextStyle(
            fontFamily = monoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        ),
        fieldInput = fieldInput,
        fieldInputMasked = fieldInput.copy(letterSpacing = 0.18.em),
        fieldLabel = body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.02.em),
        fieldToggle = body.copy(
            fontFamily = monoFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.08.em,
        ),
        fieldError = body.copy(fontSize = 13.sp, lineHeight = 18.sp),
        errorBadge = body.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
        caption = body.copy(fontSize = 12.sp),
        linkPrompt = body.copy(fontSize = 14.sp),
        linkStrong = body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
        linkUnderlined = body.copy(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline,
        ),
        action = body.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
        actionCompact = body.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        message = material.bodyMedium,
    )
}

// What MaterialTheme adds to the ambient text style on top of bodyLarge, so text that inherits it
// lays out the same (line height centered in its line, no font padding).
private val AmbientTextDefaults = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
        mode = LineHeightStyle.Mode.Fixed,
    ),
)

private fun materialTypography(fontFamily: FontFamily): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = TextStyle(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

internal val DefaultTypography = rolaboxTypography(fontFamily = HankenGrotesk)
