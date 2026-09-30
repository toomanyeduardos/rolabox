package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme

/** A "Prompt? Action" line, with the action in the accent color, for the foot of a screen. */
@Composable
fun FooterLink(prompt: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(prompt, color = colors.muted, fontSize = 14.sp)
        Text(action, color = colors.accentText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** An underlined link in the ink color, for a quiet alternative such as continuing without an account. */
@Composable
fun UnderlinedLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = RolaboxMetal.colors.ink,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textDecoration = TextDecoration.Underline,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

/**
 * A small action in the accent color, for the answers under an error or beside a field's label. It
 * takes up 48dp of layout. Pass [reserveTouchTarget] = false where that doesn't fit, such as on the
 * label row of a field: Compose still extends the touch area to 48dp, without moving anything around it.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    reserveTouchTarget: Boolean = true,
) {
    Text(
        text = text,
        color = RolaboxMetal.colors.accentText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier
            .then(if (reserveTouchTarget) Modifier.minimumInteractiveComponentSize() else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            // Without the reserved target the text lines up with the edge of whatever it sits beside.
            .padding(horizontal = if (reserveTouchTarget) 4.dp else 0.dp),
    )
}

@PreviewLightDark
@Composable
private fun LinksPreview() {
    RolaboxTheme {
        Column {
            FooterLink(prompt = "New here?", action = "Create account", onClick = {})
            UnderlinedLink(text = "Use offline without an account", onClick = {})
            TextAction(text = "Forgot password?", onClick = {})
        }
    }
}
