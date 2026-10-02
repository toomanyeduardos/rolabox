package com.eduardoflores.rolabox.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxType

private val RowMinHeight = 32.dp
private val RowStartPadding = 12.dp
private val RowVerticalPadding = 4.dp
private val RowGap = 8.dp
private val ScrollbarEndPadding = 16.dp
private val TrackWidth = 4.dp
private val TrackEdgeInset = 3.dp
private val TrackVerticalInset = 4.dp
private val ThumbMinHeight = 12.dp
private const val CHEVRON_ALPHA = 0.75f
private const val SELECTION_UPPER_STOP = 0.48f
private const val SELECTION_LOWER_STOP = 0.52f

/** A row of a [DeviceList]: its [label], and whether it [opensSubmenu], which draws a `›` after it. */
@Immutable
data class DeviceListRow(val label: String, val opensSubmenu: Boolean = false)

/**
 * The list of a device's display (ADR-018): one row per item, the row at [highlightedIndex] in the
 * selection color, a `›` on the rows that open a submenu, and a thin scrollbar on the right when the
 * list doesn't fit. Put it in the content of a [DeviceDisplay].
 *
 * The list doesn't take taps, drags or scrolls: the highlight is the screen's state, moved by the
 * wheel, and the list scrolls only to keep it in view. A row is as tall as its text needs and wraps
 * the text, so at large font scales fewer rows fit and the highlight still reaches every one.
 */
@Composable
fun DeviceList(rows: List<DeviceListRow>, highlightedIndex: Int, modifier: Modifier = Modifier) {
    val colors = RolaboxMetal.colors
    val state =
        rememberLazyListState(
            initialFirstVisibleItemIndex = highlightedIndex.coerceIn(0, rows.lastIndex.coerceAtLeast(0)),
        )
    KeepInView(state, highlightedIndex)
    Box(modifier.fillMaxSize()) {
        LazyColumn(state = state, userScrollEnabled = false, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(rows) { index, row -> DeviceListItem(row, selected = index == highlightedIndex) }
        }
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawScrollbar(state, track = colors.displayRule, thumb = colors.displayMuted)
            },
        )
    }
}

@Composable
private fun DeviceListItem(row: DeviceListRow, selected: Boolean) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    val background = if (selected) {
        Modifier.background(
            Brush.verticalGradient(
                0f to colors.selection[0],
                SELECTION_UPPER_STOP to colors.selection[1],
                SELECTION_LOWER_STOP to colors.selection[2],
                1f to colors.selection[3],
            ),
        )
    } else {
        Modifier
    }
    val ink = if (selected) colors.onSelection else styles.displayRow.color
    Row(
        Modifier
            .fillMaxWidth()
            .then(background)
            .heightIn(min = RowMinHeight)
            .padding(
                start = RowStartPadding,
                end = ScrollbarEndPadding,
                top = RowVerticalPadding,
                bottom = RowVerticalPadding,
            )
            .semantics { this.selected = selected },
        horizontalArrangement = Arrangement.spacedBy(RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = styles.displayRow.copy(color = ink), modifier = Modifier.weight(1f))
        if (row.opensSubmenu) {
            // Decorative: the row opens something whether or not the arrow is read out.
            Text(
                "›",
                style = styles.displayChevron.copy(color = ink.copy(alpha = CHEVRON_ALPHA)),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/**
 * Scrolls [state] just enough for the row at [index] to be fully in view, and does nothing when it
 * already is. A row that comes from far away is put at the edge it came from, so a list that is
 * turned forward keeps the highlight at the bottom, as the wheel is moving down.
 */
@Composable
private fun KeepInView(state: LazyListState, index: Int) {
    LaunchedEffect(state, index) {
        val cameFromAbove = index > state.firstVisibleItemIndex
        var item = state.visibleRow(index)
        if (item == null) {
            state.scrollToItem(index)
            item = state.visibleRow(index) ?: return@LaunchedEffect
            val viewport = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
            if (cameFromAbove && item.offset <= state.layoutInfo.viewportStartOffset && item.size < viewport) {
                state.scrollBy(-(viewport - item.size).toFloat())
            }
            return@LaunchedEffect
        }
        val info = state.layoutInfo
        when {
            item.offset < info.viewportStartOffset -> state.scrollBy((item.offset - info.viewportStartOffset).toFloat())

            item.offset + item.size > info.viewportEndOffset ->
                state.scrollBy((item.offset + item.size - info.viewportEndOffset).toFloat())
        }
    }
}

private fun LazyListState.visibleRow(index: Int): LazyListItemInfo? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

/** Draws the track and the thumb, only while the list has more rows than the viewport shows. */
private fun DrawScope.drawScrollbar(state: LazyListState, track: Color, thumb: Color) {
    val info = state.layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || !(state.canScrollBackward || state.canScrollForward)) return
    val first = visible.first()
    val averageRow = visible.sumOf { it.size }.toFloat() / visible.size
    val contentHeight = averageRow * info.totalItemsCount
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
    val scrolled = first.index * averageRow - first.offset
    val inset = TrackVerticalInset.toPx()
    val trackHeight = size.height - 2f * inset
    val trackLeft = size.width - TrackEdgeInset.toPx() - TrackWidth.toPx()
    val corner = CornerRadius(TrackWidth.toPx() / 2f)
    drawRoundRect(track, Offset(trackLeft, inset), Size(TrackWidth.toPx(), trackHeight), corner)
    val thumbHeight = (viewport / contentHeight * trackHeight).coerceIn(ThumbMinHeight.toPx(), trackHeight)
    val thumbTop = (scrolled / (contentHeight - viewport) * (trackHeight - thumbHeight)).coerceIn(
        0f,
        trackHeight - thumbHeight,
    )
    drawRoundRect(thumb, Offset(trackLeft, inset + thumbTop), Size(TrackWidth.toPx(), thumbHeight), corner)
}

private val DisplayPreviewHeight = 318.dp

private val MainMenu = listOf(
    DeviceListRow("Music", opensSubmenu = true),
    DeviceListRow("Podcasts", opensSubmenu = true),
    DeviceListRow("Audiobooks", opensSubmenu = true),
    DeviceListRow("Shuffle Songs"),
    DeviceListRow("Settings", opensSubmenu = true),
)

private val Artists = listOf(
    "Adeline Park", "Blue Harbor", "Cass Monroe", "Dune Collective", "Ellis Grey", "Fern & Iron", "Hollow Pines",
    "Juno Vale", "Kite Theory", "Maren Holt", "Northbound", "Otis Ray", "Pale Lanterns", "Quiet Fleet",
).map { DeviceListRow(it, opensSubmenu = true) }

@Composable
private fun DeviceListPreview(title: String, rows: List<DeviceListRow>, highlightedIndex: Int) {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay(title, batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(rows, highlightedIndex)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceListShortPreview() = DeviceListPreview("rolabox", MainMenu, highlightedIndex = 0)

@PreviewLightDark
@Composable
private fun DeviceListLongFirstPreview() = DeviceListPreview("Artists", Artists, highlightedIndex = 0)

@PreviewLightDark
@Composable
private fun DeviceListLongMiddlePreview() = DeviceListPreview("Artists", Artists, highlightedIndex = 7)

@PreviewLightDark
@Composable
private fun DeviceListLongLastPreview() = DeviceListPreview("Artists", Artists, highlightedIndex = Artists.lastIndex)
