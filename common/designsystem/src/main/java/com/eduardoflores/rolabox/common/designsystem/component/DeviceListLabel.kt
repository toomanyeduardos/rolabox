package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection

/**
 * The label of a [DeviceList] row, on one line, ending in an ellipsis when it doesn't fit (ADR-017,
 * rule 12). The highlighted row scrolls it instead, or wraps it where the system's animations are off.
 */
@Composable
internal fun RowLabel(
    text: String,
    style: TextStyle,
    highlighted: Boolean,
    animationsOff: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        !highlighted -> Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
        animationsOff -> Text(text, style = style, modifier = modifier)
        else -> ScrollingRowLabel(text, style, modifier)
    }
}

/**
 * The highlighted row's label. It ends in an ellipsis while it rests at its start, and shows its
 * whole text, clipped by the row and moved by [scrollRowText], while it scrolls. Leaving the
 * composition, when the highlight moves, stops it at once.
 */
@Composable
private fun ScrollingRowLabel(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    var offset by remember(text) { mutableFloatStateOf(0f) }
    val moving by remember(text) { derivedStateOf { offset > 0f } }
    BoxWithConstraints(modifier.clipToBounds()) {
        val fullWidth = remember(text, style, measurer) {
            measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        }
        val overflow = (fullWidth - constraints.maxWidth).coerceAtLeast(0).toFloat()
        val pxPerSecond = with(density) { RowScrollSpeed.toPx() }
        LaunchedEffect(text, overflow, pxPerSecond) {
            offset = 0f
            scrollRowText(overflow, pxPerSecond) { offset = it }
        }
        if (moving) {
            Text(
                text,
                style = style,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = direction * offset },
            )
        } else {
            Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val LongRows = listOf(
    DeviceListRow("Overture"),
    DeviceListRow("The Night We Drove the Coast Road Home with the Windows Down"),
    DeviceListRow("Everything I Meant to Say at the Station Before the Last Train Left"),
    DeviceListRow("Sound Check and Volume Limit for Every Song", value = "Off"),
    DeviceListRow("Songs Recorded Live at the Harbor Theater", opensSubmenu = true),
    DeviceListRow("Coda"),
)

/** Long rows at rest: each one ends in an ellipsis, the highlighted one too, before it scrolls. */
@PreviewLightDark
@Composable
private fun DeviceListLongRowsPreview() = DeviceListPreview("Songs", LongRows, highlightedIndex = 1)

/** A long row with its `›` highlighted: the arrow keeps its place. */
@PreviewLightDark
@Composable
private fun DeviceListLongRowsSubmenuPreview() = DeviceListPreview("Songs", LongRows, highlightedIndex = 4)

/** With the system's animations off, the highlighted row wraps its label, and only it. */
@PreviewLightDark
@Composable
private fun DeviceListLongRowsAnimationsOffPreview() =
    DeviceListPreview("Songs", LongRows, highlightedIndex = 1, animationsOff = true)
