package com.eduardoflores.rolabox.auth.ui.impl.signin.google

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.auth.ui.impl.R
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

// Google's Sign in with Google button specs (developers.google.com/identity/branding-guidelines). The
// button doesn't use the app's theme: it takes Google's Light or Dark theme, whichever matches the app.
// These are Google's brand, not Rolabox's, so they stay here instead of in the design system (ADR-015 rule 6).
private const val DISABLED_ALPHA = 0.38f

// Google's spec for Android: 40 high, 12 before the "G", a 20 "G", 10 after it, 12 after the label,
// which is Google Sans Medium 14/20 (the design system's brandButtonLabel).
private val GoogleButtonHeight = 40.dp

@Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's brand colors.
private val GoogleLightFill = Color(0xFFFFFFFF)

@Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's brand colors.
private val GoogleLightStroke = Color(0xFF747775)

@Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's brand colors.
private val GoogleLightText = Color(0xFF1F1F1F)

@Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's brand colors.
private val GoogleDarkFill = Color(0xFF131314)

@Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's brand colors.
private val GoogleDarkStroke = Color(0xFF8E918F)

@Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's brand colors.
private val GoogleDarkText = Color(0xFFE3E3E3)

/**
 * "Continue with Google", one of the labels Google approves. The "G" is Google's official artwork
 * (`ic_google_g`), rendered from their SVG without changes. Google's rules don't allow recoloring or
 * redrawing it, or putting it on another background.
 *
 * Google's rules allow one way to make the button bigger: scaling all of it, in proportion. So at a
 * larger font scale the "G", the gaps and the height grow by the same factor as the label, and the
 * label stays on one line. Google's rules come before ADR-017 rule 5 (ADR-000 rule 5), which would
 * wrap it.
 */
@Composable
internal fun GoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = RolaboxMetal.colors.isDark
    val fill = if (dark) GoogleDarkFill else GoogleLightFill
    val stroke = if (dark) GoogleDarkStroke else GoogleLightStroke
    val text = if (dark) GoogleDarkText else GoogleLightText
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)

    @Suppress("ForbiddenMethodCall") // ADR-015 rule 6: Google's spec makes the button a pill.
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GoogleButtonHeight * scale)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp * scale),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = null,
            modifier = Modifier.size(20.dp * scale),
        )
        Spacer(Modifier.width(10.dp * scale))
        Text(
            text = stringResource(R.string.auth_ui_google_continue),
            style = RolaboxType.styles.brandButtonLabel,
            // Google's spec sets this color, not the theme.
            color = text,
            // ADR-000 rule 5 over ADR-017 rule 5: Google's spec has a one-line label, so it doesn't wrap.
            maxLines = 1,
        )
    }
}

@PreviewLightDark
@Composable
private fun GoogleButtonPreview() {
    RolaboxTheme {
        Column(
            Modifier.brushedMetal().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            GoogleButton(onClick = {})
            GoogleButton(onClick = {}, enabled = false)
        }
    }
}
