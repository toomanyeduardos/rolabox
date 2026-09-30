package com.eduardoflores.rolabox.core.auth.ui.google

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduardoflores.rolabox.core.auth.ui.R
import com.eduardoflores.rolabox.core.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.core.designsystem.component.brushedMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

// Google's Sign in with Google button specs (developers.google.com/identity/branding-guidelines). The
// button doesn't use the app's theme: it takes Google's Light or Dark theme, whichever matches the app.
private const val DISABLED_ALPHA = 0.38f
private val GoogleLightFill = Color(0xFFFFFFFF)
private val GoogleLightStroke = Color(0xFF747775)
private val GoogleLightText = Color(0xFF1F1F1F)
private val GoogleDarkFill = Color(0xFF131314)
private val GoogleDarkStroke = Color(0xFF8E918F)
private val GoogleDarkText = Color(0xFFE3E3E3)

/**
 * "Continue with Google", one of the labels Google approves. The "G" is Google's official artwork
 * (`ic_google_g`), rendered from their SVG without changes. Google's rules don't allow recoloring or
 * redrawing it, or putting it on another background.
 */
@Composable
internal fun GoogleButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val dark = RolaboxMetal.colors.isDark
    val fill = if (dark) GoogleDarkFill else GoogleLightFill
    val stroke = if (dark) GoogleDarkStroke else GoogleLightStroke
    val text = if (dark) GoogleDarkText else GoogleLightText
    val shape = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(shape)
            .background(fill)
            .border(1.dp, stroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google_g),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.auth_ui_google_continue),
            color = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
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
