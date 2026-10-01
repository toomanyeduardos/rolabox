package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.R
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxType

private val WellShape = RoundedCornerShape(12.dp)
private const val INNER_SHADOW_ALPHA = 0.14f

/**
 * An input cut into the metal. Pass [error] to show the red ring and message. [footer] goes under
 * the error, for a strength meter or actions that answer the error. [labelAction] goes opposite the
 * label, for something like "Forgot password?". Pass [contentType] so autofill and password managers
 * know what the field is for. A [password] field gets a SHOW/HIDE toggle. A disabled field ignores
 * input, for a form that's being submitted.
 *
 * The well grows with the text, and [labelAction] goes under the label when the two don't fit side
 * by side (ADR-017).
 */
@Composable
fun RecessedField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    password: Boolean = false,
    contentType: ContentType? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    error: String? = null,
    labelAction: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    var reveal by rememberSaveable { mutableStateOf(false) }
    val hidden = password && !reveal
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = if (hidden) styles.fieldInputMasked else styles.fieldInput,
            cursorBrush = SolidColor(colors.accent),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = capitalization,
                autoCorrectEnabled = !password && keyboardType == KeyboardType.Text,
                keyboardType = if (password) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    if (contentType != null) this.contentType = contentType
                    if (error != null) error(error)
                },
            // The label is inside the decoration box so that it merges into the field's semantics, and a
            // screen reader reads it with the text.
            decorationBox = { inner ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = spaceBetween(gap = 12.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = label, style = styles.fieldLabel)
                        labelAction?.invoke()
                    }
                    FieldWell(hasError = error != null) {
                        Box(Modifier.weight(1f)) { inner() }
                        if (password) {
                            RevealToggle(revealed = reveal, onClick = { reveal = !reveal })
                        }
                    }
                }
            },
        )
        if (error != null) FieldError(error)
        footer?.invoke()
    }
}

@Composable
private fun FieldWell(hasError: Boolean, content: @Composable RowScope.() -> Unit) {
    val colors = RolaboxMetal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(WellShape)
            .background(colors.well)
            // A shadow along the top edge, so the well reads as recessed.
            .drawWithContent {
                drawContent()
                val shadowHeight = 5.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = INNER_SHADOW_ALPHA), Color.Transparent),
                        endY = shadowHeight,
                    ),
                    size = Size(size.width, shadowHeight),
                )
            }
            .border(if (hasError) 1.5.dp else 1.dp, if (hasError) colors.error else colors.wellBorder, WellShape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RevealToggle(revealed: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        if (revealed) R.string.ds_hide_password_description else R.string.ds_show_password_description,
    )
    Text(
        text = stringResource(if (revealed) R.string.ds_hide_password else R.string.ds_show_password),
        style = RolaboxType.styles.fieldToggle,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    )
}

/** A field's error message, announced when it appears. */
@Suppress("FixedHeightAroundText") // ADR-017 rule 3: the badge's size is multiplied by the font scale.
@Composable
fun FieldError(message: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    // The badge is a circle, so it can't grow in one direction only: it scales with its "!".
    val badgeSize = 16.dp * LocalDensity.current.fontScale
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(badgeSize)
                .clip(CircleShape)
                .background(colors.error),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", style = styles.errorBadge)
        }
        Text(message, style = styles.fieldError)
    }
}

/** A 4-segment password strength meter. [level] is 0 to 4, and 0 lights no segment and shows no label. */
@Composable
fun StrengthMeter(level: Int, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val lit = level.coerceIn(0, SEGMENTS)
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(SEGMENTS) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < lit) colors.strength[lit - 1] else colors.rule),
                )
            }
        }
        StrengthLabels.getOrNull(lit - 1)?.let { Text(stringResource(it), style = RolaboxType.styles.caption) }
    }
}

// The label for each level from 1 to 4.
private val StrengthLabels = listOf(
    R.string.ds_strength_weak,
    R.string.ds_strength_fair,
    R.string.ds_strength_good,
    R.string.ds_strength_strong,
)

private const val SEGMENTS = 4

@Composable
private fun FieldPreviewSurface(content: @Composable ColumnScope.() -> Unit) {
    RolaboxTheme {
        Column(
            Modifier.brushedMetal().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@PreviewLightDark
@Composable
private fun RecessedFieldPreview() {
    FieldPreviewSurface {
        RecessedField(label = "Email", value = "", onValueChange = {})
        RecessedField(label = "Email", value = "alex@mail.com", onValueChange = {})
        RecessedField(label = "Email", value = "alex@mail.com", onValueChange = {}, enabled = false)
        RecessedField(label = "Email", value = "alex@", onValueChange = {}, error = "Enter a valid email address.")
    }
}

@PreviewLightDark
@Composable
private fun PasswordFieldPreview() {
    FieldPreviewSurface {
        RecessedField(
            label = "Password",
            value = "hunter2hunter2",
            onValueChange = {},
            password = true,
            labelAction = { TextAction("Forgot password?", onClick = {}, reserveTouchTarget = false) },
        )
        RecessedField(
            label = "Password",
            value = "hunter2hunter2",
            onValueChange = {},
            password = true,
            error = "That password doesn't match this email.",
        )
        RecessedField(
            label = "Password",
            value = "kdjfhqPwzm4x",
            onValueChange = {},
            password = true,
            footer = { StrengthMeter(level = 3) },
        )
    }
}

@PreviewLightDark
@Composable
private fun StrengthMeterPreview() {
    FieldPreviewSurface {
        StrengthMeter(level = 0)
        StrengthMeter(level = 1)
        StrengthMeter(level = 2)
        StrengthMeter(level = 3)
        StrengthMeter(level = 4)
    }
}
