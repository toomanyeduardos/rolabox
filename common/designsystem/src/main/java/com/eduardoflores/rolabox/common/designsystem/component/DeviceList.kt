package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme

private val TrackWidth = 4.dp
private val TrackEdgeInset = 3.dp
private val TrackVerticalInset = 4.dp
private val ThumbMinHeight = 12.dp
private val MessageHorizontalPadding = 22.dp
private val MessageVerticalPadding = 12.dp

/**
 * The list of a device's display (ADR-018): one row per item, the row at [highlightedIndex] in the
 * selection color, a `›` on the rows that open a submenu, a row's value on the right, and a thin
 * scrollbar on the right when the list doesn't fit. Put it in the content of a [DeviceDisplay].
 *
 * The list doesn't take taps, drags or scrolls: the highlight is the screen's state, moved by the
 * wheel, and the list scrolls only to keep it in view. A row is one line at every font scale, so at
 * large font scales fewer rows fit and the highlight still reaches every one (ADR-018, rule 14). A
 * label that doesn't fit ends in an ellipsis, and on the highlighted row it scrolls sideways after a
 * moment, to its end and around again, so its full text can be read. Where the system's animations
 * are off, the highlighted row wraps its label instead. A value or a `›` keeps its place, and a long
 * label gives way to it. A [highlightedIndex] below 0 highlights no row, for a screen that has
 * nothing to open or change, such as About, and so does one on a [DeviceListSection].
 *
 * A [message] sits under the rows, centered in the space they leave. When the rows don't fit with it,
 * they scroll above it.
 *
 * A [field], a [DeviceTextField], is the first row of a list that takes text (ADR-032). It stays at the top
 * while the rows under it scroll, and the scrollbar starts under it. It draws its own highlight, and
 * [highlightedIndex] counts the [rows] only.
 */
@Composable
fun DeviceList(
    rows: List<DeviceListEntry>,
    highlightedIndex: Int,
    modifier: Modifier = Modifier,
    message: DeviceListMessage? = null,
    field: (@Composable () -> Unit)? = null,
) {
    if (field == null) {
        ListBody(rows, highlightedIndex, message, modifier.fillMaxSize())
        return
    }
    Column(modifier.fillMaxSize()) {
        field()
        ListBody(rows, highlightedIndex, message, Modifier.weight(1f).fillMaxWidth())
    }
}

/** The rows and the [message] under them, in the space the list gives them. */
@Composable
private fun ListBody(
    rows: List<DeviceListEntry>,
    highlightedIndex: Int,
    message: DeviceListMessage?,
    modifier: Modifier = Modifier,
) {
    if (message == null) {
        ListRows(rows, highlightedIndex, fill = true, modifier = modifier)
        return
    }
    Layout(
        content = {
            ListRows(rows, highlightedIndex, fill = false)
            DeviceMessageLines(
                message.title,
                message.body,
                Modifier.padding(horizontal = MessageHorizontalPadding, vertical = MessageVerticalPadding),
            )
        },
        modifier = modifier,
    ) { (list, text), constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val messagePlaceable = text.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = height))
        val listMax = (height - messagePlaceable.height).coerceAtLeast(0)
        val listPlaceable = list.measure(Constraints(minWidth = width, maxWidth = width, maxHeight = listMax))
        layout(width, height) {
            listPlaceable.place(0, 0)
            val free = height - listPlaceable.height
            messagePlaceable.place(0, listPlaceable.height + ((free - messagePlaceable.height) / 2).coerceAtLeast(0))
        }
    }
}

/** The rows and the scrollbar. [fill] makes them as tall as the space they get, and otherwise as tall as the rows. */
@Composable
private fun ListRows(rows: List<DeviceListEntry>, highlightedIndex: Int, fill: Boolean, modifier: Modifier = Modifier) {
    val animationsOff = rememberAnimationsOff()
    val colors = RolaboxMetal.colors
    // The first row of a section brings its label into view with it.
    val top = if (rows.getOrNull(highlightedIndex - 1) is DeviceListSection) highlightedIndex - 1 else highlightedIndex
    val state =
        rememberLazyListState(
            initialFirstVisibleItemIndex = top.coerceIn(0, rows.lastIndex.coerceAtLeast(0)),
        )
    KeepInView(state, highlightedIndex, top)
    Box(modifier) {
        LazyColumn(
            state = state,
            userScrollEnabled = false,
            modifier = if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(rows) { index, entry ->
                when (entry) {
                    is DeviceListRow ->
                        DeviceListItem(entry, selected = index == highlightedIndex, animationsOff = animationsOff)

                    is DeviceListSection -> DeviceListSectionLabel(entry.label)
                }
            }
        }
        Box(
            Modifier.matchParentSize().drawBehind {
                drawScrollbar(state, track = colors.displayRule, thumb = colors.displayMuted)
            },
        )
    }
}

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
    drawScrollbar(viewport / contentHeight, scrolled / (contentHeight - viewport), track, thumb)
}

/**
 * Draws the display's scrollbar on the right: the track, and a thumb [shown] of it long (the share of the content
 * in view) at [scrolled] of the way down. A list's rows and a screen of text draw the same one.
 */
internal fun DrawScope.drawScrollbar(shown: Float, scrolled: Float, track: Color, thumb: Color) {
    val inset = TrackVerticalInset.toPx()
    val trackHeight = size.height - 2f * inset
    val trackLeft = size.width - TrackEdgeInset.toPx() - TrackWidth.toPx()
    val corner = CornerRadius(TrackWidth.toPx() / 2f)
    drawRoundRect(track, Offset(trackLeft, inset), Size(TrackWidth.toPx(), trackHeight), corner)
    val thumbHeight = (shown * trackHeight).coerceIn(ThumbMinHeight.toPx(), trackHeight)
    val thumbTop = (scrolled * (trackHeight - thumbHeight)).coerceIn(0f, trackHeight - thumbHeight)
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

private val Settings = listOf(
    DeviceListRow("Shuffle", value = "Off"),
    DeviceListRow("Repeat", value = "All"),
    DeviceListRow("Clicker", value = "On"),
    DeviceListRow("Sound Check and Volume Limit for Every Song", value = "Off"),
    DeviceListRow("Account", opensSubmenu = true),
)

@PreviewLightDark
@Composable
private fun DeviceListShortPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("rolabox", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(MainMenu, highlightedIndex = 0)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceListLongFirstPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Artists", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Artists, highlightedIndex = 0)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceListLongMiddlePreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Artists", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Artists, highlightedIndex = 7)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceListLongLastPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Artists", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Artists, highlightedIndex = Artists.lastIndex)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceListValuesPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Settings", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Settings, highlightedIndex = 2)
            }
        }
    }
}
