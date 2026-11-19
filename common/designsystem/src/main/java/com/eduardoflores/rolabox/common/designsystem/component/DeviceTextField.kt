package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val FieldHorizontalMargin = 8.dp
private val FieldVerticalMargin = 6.dp
private val FieldMinHeight = 30.dp
private val FieldHorizontalPadding = 9.dp
private val FieldVerticalPadding = 4.dp
private val FieldGap = 7.dp
private val FieldShape = RoundedCornerShape(7.dp)
private val FieldBorder = 1.dp
private const val HINT_ON_SELECTION_ALPHA = 0.8f

// The search glyph is sized with the text, as the list's marks are: these are its sizes at font scale 1.
private const val GLYPH_SIZE = 13f
private const val GLYPH_STROKE = 1.7f
private const val GLYPH_CENTER = 5.5f
private const val GLYPH_RADIUS = 4.2f
private const val GLYPH_HANDLE_START = 8.7f
private const val GLYPH_HANDLE_END = 12f

/**
 * The field of the device's display (ADR-032): a row at the top of a [DeviceList], with a search glyph, the
 * text of [state], and its [hint] while it is empty. It wraps [BasicTextField], the one place the text is
 * edited, which TalkBack reads as an edit field labeled with the [hint].
 *
 * The field is one line and follows the font scale. The [hint] is the app's text and is never cut: where it
 * doesn't fit on one line, the [shortHint] replaces it (ADR-017, rule 13). While [typing], the field is on the
 * display's white with a caret, and its text scrolls sideways to keep the caret in view. Otherwise it is on the
 * header's gray, and text that doesn't fit ends in an ellipsis. [highlighted] draws it in the selection color,
 * as a highlighted row.
 *
 * The display isn't touched (ADR-018), so the field takes no tap. Whoever places it gives the text field focus,
 * which shows the keyboard, through [textFieldModifier], which is the [BasicTextField]'s. The keyboard's action
 * key is [imeAction], and it calls [onKeyboardAction].
 */
@Composable
fun DeviceTextField(
    state: TextFieldState,
    hint: String,
    shortHint: String,
    typing: Boolean,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    textFieldModifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Search,
    onKeyboardAction: () -> Unit = {},
) {
    val colors = RolaboxMetal.colors
    val ink = if (highlighted) colors.onSelection else RolaboxType.styles.displayField.color
    Box(modifier.fillMaxWidth().padding(horizontal = FieldHorizontalMargin, vertical = FieldVerticalMargin)) {
        BasicTextField(
            state = state,
            modifier = textFieldModifier.fillMaxWidth().semantics { contentDescription = hint },
            textStyle = RolaboxType.styles.displayField.copy(color = ink),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Text,
                imeAction = imeAction,
            ),
            onKeyboardAction = { onKeyboardAction() },
            lineLimits = TextFieldLineLimits.SingleLine,
            cursorBrush = SolidColor(colors.selection[1]),
            decorator = { innerTextField ->
                FieldDecoration(state.text.toString(), hint, shortHint, typing, highlighted, innerTextField)
            },
        )
        // Over the field, so a tap never reaches it: the display isn't touched (ADR-018).
        Box(Modifier.matchParentSize().pointerInput(Unit) { consumeEveryTouch() })
    }
}

/** What is drawn around the edited line: the field's box, its glyph, and its hint or its ellipsized text. */
@Composable
private fun FieldDecoration(
    text: String,
    hint: String,
    shortHint: String,
    typing: Boolean,
    highlighted: Boolean,
    innerTextField: @Composable () -> Unit,
) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    val background = when {
        highlighted -> Modifier.background(selectionBrush(colors.selection))
        typing -> Modifier.background(colors.displayScreen)
        else -> Modifier.background(colors.displayHeader)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .then(background)
            .border(FieldBorder, colors.displayRule, FieldShape)
            .heightIn(min = FieldMinHeight)
            .padding(horizontal = FieldHorizontalPadding, vertical = FieldVerticalPadding),
        horizontalArrangement = Arrangement.spacedBy(FieldGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(color = if (highlighted) colors.onSelection else colors.displayMuted)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            // Off the keyboard the text ends in an ellipsis, which an edited line can't, so it is drawn apart and
            // the edited line stays in place, unseen, for focus and TalkBack.
            val ellipsized = !typing && text.isNotEmpty()
            Box(if (ellipsized) Modifier.alpha(0f) else Modifier) { innerTextField() }
            when {
                ellipsized -> Text(
                    text,
                    style = styles.displayField.copy(
                        color = if (highlighted) colors.onSelection else styles.displayField.color,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {},
                )

                text.isEmpty() -> {
                    val hintInk = if (highlighted) {
                        colors.onSelection.copy(alpha = HINT_ON_SELECTION_ALPHA)
                    } else {
                        styles.displayFieldHint.color
                    }
                    FieldHint(hint, shortHint, styles.displayFieldHint.copy(color = hintInk))
                }
            }
        }
    }
}

private suspend fun PointerInputScope.consumeEveryTouch() {
    awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial).consume() }
}

/**
 * The [hint] on one line, or the [shortHint] when the hint doesn't fit: the hint is never cut (ADR-017,
 * rule 13). Read by TalkBack as the field's label, not here.
 */
@Composable
private fun FieldHint(hint: String, shortHint: String, style: TextStyle) {
    Layout(
        content = {
            Text(hint, style = style, maxLines = 1, softWrap = false)
            Text(shortHint, style = style, maxLines = 1, softWrap = false)
        },
        modifier = Modifier.clearAndSetSemantics {},
    ) { (long, short), constraints ->
        val free = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val longPlaceable = long.measure(free)
        val shown = if (longPlaceable.width <= constraints.maxWidth) longPlaceable else short.measure(free)
        val width = shown.width.coerceAtMost(constraints.maxWidth)
        layout(width, shown.height) { shown.place(0, 0) }
    }
}

/** A magnifying glass, from the design's SVG (player-designs/device, frame 29). */
@Composable
private fun SearchGlyph(color: Color) {
    val scale = LocalDensity.current.fontScale
    // Decorative: the field is read by its label.
    Canvas(Modifier.size(GLYPH_SIZE.dp * scale).clearAndSetSemantics {}) {
        val unit = size.width / GLYPH_SIZE
        val stroke = Stroke(GLYPH_STROKE * unit, cap = StrokeCap.Round)
        val center = Offset(GLYPH_CENTER, GLYPH_CENTER) * unit
        drawCircle(color, radius = GLYPH_RADIUS * unit, center = center, style = stroke)
        drawLine(
            color,
            start = Offset(GLYPH_HANDLE_START, GLYPH_HANDLE_START) * unit,
            end = Offset(GLYPH_HANDLE_END, GLYPH_HANDLE_END) * unit,
            strokeWidth = stroke.width,
            cap = StrokeCap.Round,
        )
    }
}

private const val PREVIEW_HINT = "Shows, creators, episodes"
private const val PREVIEW_SHORT_HINT = "Search"
private val PreviewDisplayHeight = 318.dp
private val PreviewNarrowWidth = 260.dp

@Composable
private fun DeviceTextFieldPreviewCase(query: String, typing: Boolean, highlighted: Boolean) {
    DeviceTextField(
        state = rememberTextFieldState(query),
        hint = PREVIEW_HINT,
        shortHint = PREVIEW_SHORT_HINT,
        typing = typing,
        highlighted = highlighted,
    )
}

/**
 * The field's cases (ADR-032, rule 7), on a display as wide as a phone's: empty with its hint (the short hint
 * at 1.5x), with a query, and with one too long to fit, each on the wheel, highlighted, and while typing.
 */
@PreviewLightDark
@Composable
private fun DeviceTextFieldPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay(title = "Search", batteryLevel = 0.7f) {
                Column {
                    DeviceTextFieldPreviewCase(query = "", typing = false, highlighted = false)
                    DeviceTextFieldPreviewCase(query = "", typing = false, highlighted = true)
                    DeviceTextFieldPreviewCase(query = "", typing = true, highlighted = false)
                    DeviceTextFieldPreviewCase(query = "the daily", typing = false, highlighted = false)
                    DeviceTextFieldPreviewCase(query = "the daily", typing = false, highlighted = true)
                    DeviceTextFieldPreviewCase(query = "the daily", typing = true, highlighted = false)
                    DeviceTextFieldPreviewCase(
                        query = "a very long query about the history of the transatlantic telegraph",
                        typing = false,
                        highlighted = false,
                    )
                    // As narrow as a small phone's display: at 1.5x the hint doesn't fit, and the short one
                    // replaces it.
                    Box(Modifier.width(PreviewNarrowWidth)) {
                        DeviceTextFieldPreviewCase(query = "", typing = false, highlighted = false)
                    }
                }
            }
        }
    }
}

private val SearchResults = listOf(
    DeviceListSection("Shows"),
    DeviceListRow("The Daily", secondaryValue = "NY Times"),
    DeviceListRow("The Daily Brief", secondaryValue = "Harbor Audio"),
    DeviceListRow("Daily Dose of Design", secondaryValue = "Field Notes Media"),
    DeviceListSection("Episodes"),
    DeviceListRow("Why the Daily Commute Changed", opensSubmenu = true),
    DeviceListRow("A Daily Habit, Ten Years Later", opensSubmenu = true),
    DeviceListRow("The Daily Grind of a Night Shift", opensSubmenu = true),
    DeviceListRow("Daily Bread, Weekly Wages", opensSubmenu = true),
)

/** A [DeviceList] with the field over its rows, the field highlighted or not. */
@Composable
private fun FieldListPreviewCase(highlightedIndex: Int, fieldHighlighted: Boolean) {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Search", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = PreviewDisplayHeight)) {
                DeviceList(SearchResults, highlightedIndex) {
                    DeviceTextField(
                        state = rememberTextFieldState("the daily"),
                        hint = PREVIEW_HINT,
                        shortHint = PREVIEW_SHORT_HINT,
                        typing = false,
                        highlighted = fieldHighlighted,
                    )
                }
            }
        }
    }
}

/** Frame 31: the keyboard is gone, and the wheel is on the first result, under its section's label. */
@PreviewLightDark
@Composable
private fun FieldListFirstResultPreview() = FieldListPreviewCase(highlightedIndex = 1, fieldHighlighted = false)

/** MENU on a result: the field is highlighted, one center away from typing. */
@PreviewLightDark
@Composable
private fun FieldListFieldHighlightedPreview() = FieldListPreviewCase(highlightedIndex = -1, fieldHighlighted = true)
