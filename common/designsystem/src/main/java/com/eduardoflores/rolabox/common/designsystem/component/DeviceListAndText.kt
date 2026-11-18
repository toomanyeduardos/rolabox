package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType
import kotlin.math.ceil
import kotlin.math.roundToInt

private val TextTopGap = 6.dp
private val TextTopPadding = 10.dp
private val TextStartPadding = 12.dp
private val TextEndPadding = 16.dp
private val TextBottomPadding = 12.dp
private val MetaGap = 6.dp
private val RuleWidth = 1.dp
private val DisplayPreviewHeight = 318.dp

/**
 * A few rows of a device's list with text to read under them, such as an episode's Play and its description
 * (ADR-018). Under the rows, past a rule, are the [meta] line, such as a date and a length, and the [text]. Either
 * can be `null`, and is then left out.
 *
 * The rows and the text are one column, which scrolls a line of the text at a time. [scrolledLines] is how many lines
 * it is scrolled down, from 0 with the rows in view, and the screen moves it with the wheel: the component takes no
 * input. The text wraps at every font scale. How many lines the column can be scrolled depends on the text, the
 * width and the font scale, so the component measures it and reports it to [onScrollableLinesChange], 0 when all of
 * it fits. A [scrolledLines] past that shows the end. The scrollbar is shown while the column doesn't fit.
 *
 * The row at [highlightedIndex] has the selection color, and stays highlighted while the text is scrolled.
 */
@Composable
fun DeviceListAndText(
    rows: List<DeviceListRow>,
    highlightedIndex: Int,
    meta: String?,
    text: String?,
    scrolledLines: Int,
    onScrollableLinesChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RolaboxMetal.colors
    val textStyle = RolaboxType.styles.displayText
    val linePx = lineHeightPx(textStyle, LocalDensity.current)
    val target = (scrolledLines.coerceAtLeast(0) * linePx).roundToInt()
    // The first position is given at once, so a screen that comes back scrolled is drawn scrolled.
    val scroll = rememberScrollState(initial = target)
    val currentOnScrollableLinesChange by rememberUpdatedState(onScrollableLinesChange)
    LaunchedEffect(scroll, target) { scroll.scrollTo(target) }
    LaunchedEffect(scroll, linePx) {
        snapshotFlow { scroll.maxValue }.collect { max ->
            currentOnScrollableLinesChange(if (max == Int.MAX_VALUE) 0 else ceil(max / linePx).toInt())
        }
    }
    val animationsOff = rememberAnimationsOff()
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val max = scroll.maxValue
                if (max in 1 until Int.MAX_VALUE) {
                    val content = size.height + max
                    drawScrollbar(
                        shown = size.height / content,
                        scrolled = scroll.value.toFloat() / max,
                        track = colors.displayRule,
                        thumb = colors.displayMuted,
                    )
                }
            },
    ) {
        // The wheel scrolls it, through scrolledLines: the column itself takes no touch (ADR-018, rule 3).
        Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = false)) {
            rows.forEachIndexed { index, row ->
                DeviceListItem(row, selected = index == highlightedIndex, animationsOff = animationsOff)
            }
            if (meta != null || text != null) {
                ListText(meta, text, textStyle)
            }
        }
    }
}

/** The rule, then the [meta] line and the [text], as the design lays them out under the rows. */
@Composable
private fun ListText(meta: String?, text: String?, textStyle: TextStyle) {
    val rule = RolaboxMetal.colors.displayRule
    Column(
        Modifier
            .padding(top = TextTopGap)
            .fillMaxWidth()
            .drawBehind {
                drawLine(rule, Offset.Zero, Offset(size.width, 0f), strokeWidth = RuleWidth.toPx())
            }
            .padding(start = TextStartPadding, end = TextEndPadding, top = TextTopPadding, bottom = TextBottomPadding),
        verticalArrangement = Arrangement.spacedBy(MetaGap),
    ) {
        if (meta != null) Text(meta, style = RolaboxType.styles.displayMeta)
        if (text != null) Text(text, style = textStyle)
    }
}

/** The height of a line of [style] in pixels at this font scale: one step of the wheel in the text. */
private fun lineHeightPx(style: TextStyle, density: Density): Float = with(density) {
    val textPx = style.fontSize.toPx()
    if (style.lineHeight.isEm) textPx * style.lineHeight.value else style.lineHeight.toPx()
}

@Composable
private fun DeviceListAndTextPreview(rows: List<DeviceListRow>, scrolledLines: Int, text: String? = DESCRIPTION) {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("The Daily", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceListAndText(
                    rows = rows,
                    // Turning into the text leaves the last row highlighted.
                    highlightedIndex = if (scrolledLines > 0) rows.lastIndex else 0,
                    meta = "NOV 8 · 32 MIN · 20 MIN LEFT",
                    text = text,
                    scrolledLines = scrolledLines,
                    onScrollableLinesChange = {},
                )
            }
        }
    }
}

private val TwoRows = listOf(DeviceListRow("Resume"), DeviceListRow("Play from Start"))

private const val DESCRIPTION =
    "Dockworkers on both coasts walked off the job for nine days. We look at what the strike won, what it cost " +
        "the ports, and why the next contract talks may be harder. Then: what the deal means for shipping " +
        "prices this winter, for the warehouses inland that wait on the ports, and for the workers who will " +
        "be back at the table in three years, with automation the question no one could settle this time."

@PreviewLightDark
@Composable
private fun DeviceListAndTextPreview() = DeviceListAndTextPreview(TwoRows, scrolledLines = 0)

@PreviewLightDark
@Composable
private fun DeviceListAndTextScrolledPreview() = DeviceListAndTextPreview(TwoRows, scrolledLines = 4)

@PreviewLightDark
@Composable
private fun DeviceListAndTextNoTextPreview() =
    DeviceListAndTextPreview(listOf(DeviceListRow("Play")), scrolledLines = 0, text = null)
