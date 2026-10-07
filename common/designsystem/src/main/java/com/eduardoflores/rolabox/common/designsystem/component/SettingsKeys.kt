package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val SettingsKeyShape = RoundedCornerShape(26.dp)
private val SettingsKeyMinHeight = 52.dp
private val SettingsKeyPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
private const val FLAT_KEY_ALPHA = 0.05f

/**
 * A row of a settings list, as a metal key: [title] and, under it, [detail]. With an [onClick] it
 * is raised and opens something. Without one it is flat, and only shows a state, such as who is
 * signed in. Text that doesn't fit on one line wraps, and the key grows with it (ADR-017).
 *
 * The title and the detail are one node for accessibility services, so they are read together.
 */
@Composable
fun RowKey(title: String, modifier: Modifier = Modifier, detail: String? = null, onClick: (() -> Unit)? = null) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    val look = if (onClick != null) {
        Modifier
            .shadow(2.dp, SettingsKeyShape)
            .clip(SettingsKeyShape)
            .background(Brush.verticalGradient(listOf(colors.keyTop, colors.keyBottom)))
            .border(1.dp, colors.keyBorder, SettingsKeyShape)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
            .clip(SettingsKeyShape)
            .background(colors.ink.copy(alpha = FLAT_KEY_ALPHA))
            .border(1.dp, colors.rule, SettingsKeyShape)
            .semantics(mergeDescendants = true) {}
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingsKeyMinHeight)
            .then(look)
            .padding(SettingsKeyPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Text(text = title, style = styles.keyLabel)
        if (detail != null) Text(text = detail, style = styles.caption)
    }
}

/**
 * One of a few choices, of which exactly one is selected. The selected key is filled with the
 * accent, so the choice doesn't rest on color alone, and is announced as selected. Put the keys in
 * a [ChoiceGroup].
 */
@Composable
fun ChoiceKey(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val look = if (selected) {
        Modifier
            .shadow(6.dp, SettingsKeyShape, ambientColor = colors.accent, spotColor = colors.accent)
            .clip(SettingsKeyShape)
            .background(Brush.verticalGradient(listOf(colors.accentTop, colors.accentBottom)))
    } else {
        Modifier
            .shadow(2.dp, SettingsKeyShape)
            .clip(SettingsKeyShape)
            .background(Brush.verticalGradient(listOf(colors.keyTop, colors.keyBottom)))
            .border(1.dp, colors.keyBorder, SettingsKeyShape)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingsKeyMinHeight)
            .then(look)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(SettingsKeyPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (selected) RolaboxType.styles.buttonLabel else RolaboxType.styles.keyLabel,
            textAlign = TextAlign.Center,
        )
    }
}

/** A [label] and the [ChoiceKey]s under it, one above the other so they fit at any font scale (ADR-017). */
@Composable
fun ChoiceGroup(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = RolaboxType.styles.fieldLabel)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@PreviewLightDark
@Composable
private fun SettingsKeysPreview() {
    RolaboxTheme {
        Column(
            Modifier.brushedMetal().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RowKey(title = "Account", detail = "Sign in", onClick = {})
            RowKey(title = "Account", detail = "Signed in as Eduardo")
            ChoiceGroup(label = "Theme") {
                ChoiceKey(text = "Follow system", selected = true, onClick = {})
                ChoiceKey(text = "Light", selected = false, onClick = {})
                ChoiceKey(text = "Dark", selected = false, onClick = {})
            }
        }
    }
}
