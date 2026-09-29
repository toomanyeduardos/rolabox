package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eduardoflores.rolabox.core.designsystem.R
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal

private val WellShape = RoundedCornerShape(12.dp)
private const val INNER_SHADOW_ALPHA = 0.14f

/**
 * An input cut into the metal. Pass [error] to show the red ring and message. [footer] goes under
 * the error, for a strength meter or actions that answer the error. Pass [contentType] so
 * autofill and password managers know what the field is for. A [password] field gets a SHOW/HIDE
 * toggle. A disabled
 * field ignores input, for a form that's being submitted.
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
    footer: @Composable (() -> Unit)? = null,
) {
    val colors = RolaboxMetal.colors
    var reveal by rememberSaveable { mutableStateOf(false) }
    val hidden = password && !reveal
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = colors.ink,
                fontSize = 15.5.sp,
                letterSpacing = if (hidden) 0.18.em else 0.em,
            ),
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
                    Text(
                        text = label,
                        color = colors.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.02.em,
                    )
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
private fun FieldWell(hasError: Boolean, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val colors = RolaboxMetal.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
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
    val colors = RolaboxMetal.colors
    val description = stringResource(
        if (revealed) R.string.ds_hide_password_description else R.string.ds_show_password_description,
    )
    Text(
        text = stringResource(if (revealed) R.string.ds_hide_password else R.string.ds_show_password),
        color = colors.muted,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.08.em,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
    )
}

/** A field's error message, announced when it appears. */
@Composable
fun FieldError(message: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    Row(
        modifier.semantics { liveRegion = LiveRegionMode.Assertive },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .padding(top = 1.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(colors.error),
            contentAlignment = Alignment.Center,
        ) {
            Text("!", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(message, color = colors.error, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

/** A 4-segment password strength meter. [level] is 0 to 4, and 0 lights no segment. */
@Composable
fun StrengthMeter(level: Int, label: String, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
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
                        .background(
                            if (index <
                                level
                            ) {
                                colors.strength[(level - 1).coerceIn(0, SEGMENTS - 1)]
                            } else {
                                colors.rule
                            },
                        ),
                )
            }
        }
        Text(label, color = colors.muted, fontSize = 12.sp)
    }
}

private const val SEGMENTS = 4
