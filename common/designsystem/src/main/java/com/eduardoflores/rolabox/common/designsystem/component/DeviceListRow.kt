package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val RowMinHeight = 32.dp
private val RowStartPadding = 12.dp
private val RowVerticalPadding = 4.dp
private val RowGap = 8.dp
private val ScrollbarEndPadding = 16.dp
private const val CHEVRON_ALPHA = 0.75f
private const val SELECTION_UPPER_STOP = 0.48f
private const val SELECTION_LOWER_STOP = 0.52f

// The marks before and after a row's text are sized with the text: these are their sizes at font scale 1.
private val DotSize = 8.dp
private val DotStroke = 1.5.dp
private const val ARROW_WIDTH = 12f
private const val ARROW_HEIGHT = 13f
private const val ARROW_STROKE = 1.6f

/** The most of a row's width that a secondary value takes, so the row's label comes first. */
private const val SECONDARY_VALUE_MAX_FRACTION = 0.42f

private val SectionLabelMinHeight = 22.dp

/**
 * An entry of a [DeviceList]: a [DeviceListRow], which the highlight can be on, or a [DeviceListSection],
 * a label over the rows that follow it, which it never is.
 */
sealed interface DeviceListEntry

/**
 * A row of a [DeviceList]: its [label], and what is drawn at its ends.
 *
 * Before the label, a [dot] for an item that hasn't been played to its end. After it, one thing, the
 * first of: the [download] of the item, as an arrow or a percentage; a [value], such as a setting's
 * "Off"; a [secondaryValue] in a quieter voice, such as a show's creator, which gives way to the label
 * when the row is narrow; and the `›` of a row that [opensSubmenu].
 */
@Immutable
data class DeviceListRow(
    val label: String,
    val opensSubmenu: Boolean = false,
    val value: String? = null,
    val secondaryValue: String? = null,
    val dot: DeviceListDot? = null,
    val download: DeviceListDownload? = null,
) : DeviceListEntry

/** A label over a section of a [DeviceList], such as Shows. The wheel skips it: see [isSelectable]. */
@Immutable
data class DeviceListSection(val label: String) : DeviceListEntry

/** The dot before a row's label: [Unplayed] is full, [PartlyPlayed] is half. A played item has none. */
enum class DeviceListDot { Unplayed, PartlyPlayed }

/** The download of a row's item, drawn after its label. A downloaded item has none. */
@Immutable
sealed interface DeviceListDownload {
    /** Not downloaded yet: an arrow. */
    data object NotDownloaded : DeviceListDownload

    /** Downloading: how much is done, from 0 to 100. */
    data class Downloading(val percent: Int) : DeviceListDownload
}

/** What a [DeviceList] says under its rows, such as that there is nothing else yet: a [title] and a [body]. */
@Immutable
data class DeviceListMessage(val title: String, val body: String)

/**
 * Whether the highlight can be on the entry at [index]: a row can, a section label or an index outside
 * the list can't. A screen moves its highlight with it, so the wheel skips the labels.
 */
fun List<DeviceListEntry>.isSelectable(index: Int): Boolean = getOrNull(index) is DeviceListRow

@Composable
internal fun DeviceListItem(row: DeviceListRow, selected: Boolean, animationsOff: Boolean) {
    val colors = RolaboxMetal.colors
    val styles = RolaboxType.styles
    val background = if (selected) Modifier.background(selectionBrush(colors.selection)) else Modifier
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
        if (row.dot != null) {
            PlayedDot(row.dot, color = if (selected) colors.onSelection else colors.accent)
        }
        RowLabel(
            row.label,
            style = styles.displayRow.copy(color = ink),
            highlighted = selected,
            animationsOff = animationsOff,
            modifier = Modifier.weight(1f),
        )
        RowEnd(row, ink = ink, quietInk = if (selected) colors.onSelection else colors.displayMuted)
    }
}

/** The selection's gradient, behind a highlighted row and a highlighted [DeviceTextField]. */
internal fun selectionBrush(selection: List<Color>): Brush = Brush.verticalGradient(
    0f to selection[0],
    SELECTION_UPPER_STOP to selection[1],
    SELECTION_LOWER_STOP to selection[2],
    1f to selection.last(),
)

/** The one thing at the end of a row: its download, its value, its secondary value or its `›`. */
@Composable
private fun RowEnd(row: DeviceListRow, ink: Color, quietInk: Color) {
    val styles = RolaboxType.styles
    when {
        row.download is DeviceListDownload.Downloading -> {
            val described = stringResource(R.string.ds_downloading, row.download.percent)
            Text(
                stringResource(R.string.ds_download_percent, row.download.percent),
                style = styles.displayRowValue.copy(color = ink),
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics { contentDescription = described },
            )
        }

        row.download == DeviceListDownload.NotDownloaded -> {
            val scale = LocalDensity.current.fontScale
            Icon(
                DownloadArrow,
                contentDescription = stringResource(R.string.ds_not_downloaded),
                tint = quietInk,
                modifier = Modifier.size(ARROW_WIDTH.dp * scale, ARROW_HEIGHT.dp * scale),
            )
        }

        row.value != null -> Text(row.value, style = styles.displayRowValue.copy(color = ink), maxLines = 1)

        row.secondaryValue != null -> {
            // The label comes first: the value takes what the label's whole width leaves, and never more than its
            // share, so at a large font scale the value is shortened with "…" before the label is.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val before = with(density) {
                RowGap.roundToPx() +
                    if (row.dot != null) (DotSize * density.fontScale).roundToPx() + RowGap.roundToPx() else 0
            }
            Text(
                row.secondaryValue,
                style = styles.displayRowSecondary.copy(color = quietInk),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.leavingRoomForLabel(SECONDARY_VALUE_MAX_FRACTION) {
                    measurer.measure(row.label, styles.displayRow, maxLines = 1, softWrap = false).size.width + before
                },
            )
        }

        row.opensSubmenu ->
            // Decorative: the row opens something whether or not the arrow is read out.
            Text(
                "›",
                style = styles.displayChevron.copy(color = ink.copy(alpha = CHEVRON_ALPHA)),
                modifier = Modifier.clearAndSetSemantics {},
            )
    }
}

/**
 * Caps the width at what the label leaves of the row, [labelWidth] with the marks and gaps before the value, and at
 * most [fraction] of the row's. In a [Row], an element without a weight is measured before the weighted label, with
 * the whole width of the row's content.
 */
private fun Modifier.leavingRoomForLabel(fraction: Float, labelWidth: () -> Int): Modifier =
    layout { measurable, constraints ->
        val cap = if (constraints.hasBoundedWidth) {
            minOf((constraints.maxWidth * fraction).toInt(), (constraints.maxWidth - labelWidth()).coerceAtLeast(0))
        } else {
            constraints.maxWidth
        }
        val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = cap))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }

/**
 * The dot of an unplayed item, full, or of a partly played one, with its left half full. It is sized
 * with the text, as the row's other marks are, and says what it means to TalkBack.
 */
@Composable
private fun PlayedDot(dot: DeviceListDot, color: Color) {
    val scale = LocalDensity.current.fontScale
    val description = stringResource(
        when (dot) {
            DeviceListDot.Unplayed -> R.string.ds_unplayed
            DeviceListDot.PartlyPlayed -> R.string.ds_partly_played
        },
    )
    Canvas(
        Modifier
            .size(DotSize * scale)
            .semantics { contentDescription = description },
    ) {
        val stroke = DotStroke.toPx() * scale
        when (dot) {
            DeviceListDot.Unplayed -> drawCircle(color)

            DeviceListDot.PartlyPlayed -> {
                // From the bottom, clockwise to the top: the left half.
                drawArc(color, startAngle = 90f, sweepAngle = 180f, useCenter = true)
                drawCircle(color, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
            }
        }
    }
}

/** A label over a section of the list, in capitals, on the header's gray. The highlight is never on it. */
@Composable
internal fun DeviceListSectionLabel(label: String) {
    val colors = RolaboxMetal.colors
    val scale = LocalDensity.current.fontScale
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.displayHeader)
            .heightIn(min = SectionLabelMinHeight * scale)
            .padding(start = RowStartPadding, end = ScrollbarEndPadding)
            .semantics { heading() },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label.uppercase(), style = RolaboxType.styles.displaySectionLabel)
    }
}

// An arrow down onto a line, from the design's SVG (player-designs/device, frame 25).
private val DownloadArrow = ImageVector.Builder(
    name = "Download",
    defaultWidth = ARROW_WIDTH.dp,
    defaultHeight = ARROW_HEIGHT.dp,
    viewportWidth = ARROW_WIDTH,
    viewportHeight = ARROW_HEIGHT,
).apply {
    listOf("M6 1.5v7", "M3 5.8L6 8.8l3-3", "M1.5 11.5h9").forEach { path ->
        addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = ARROW_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

// A followed show in the library (frame 25): each dot, the arrow and a percentage.
private val Episodes = listOf(
    DeviceListRow("Follow", value = "On"),
    DeviceListRow(
        "A Heat Wave Over the North Atlantic",
        dot = DeviceListDot.Unplayed,
        download = DeviceListDownload.NotDownloaded,
    ),
    DeviceListRow(
        "Inside the Race for a Cheaper Battery",
        dot = DeviceListDot.Unplayed,
        download = DeviceListDownload.Downloading(percent = 38),
    ),
    DeviceListRow("The Town That Saved Its Pharmacy", dot = DeviceListDot.Unplayed),
    DeviceListRow("What Happened at the Port Strike, and What Comes Next", dot = DeviceListDot.PartlyPlayed),
    DeviceListRow("A Quiet Election in a Loud Year"),
    DeviceListRow("The Return of the Night Train"),
    DeviceListRow("Who Owns the Moon?"),
)

// Search's results (frame 31): section labels, and the shows' creators as secondary values.
private val SearchResults = listOf(
    DeviceListSection("Shows"),
    DeviceListRow("The Daily", secondaryValue = "NY Times"),
    DeviceListRow("The Daily Brief", secondaryValue = "Harbor Audio"),
    DeviceListRow("Daily Dose of Design", secondaryValue = "Field Notes Media"),
    DeviceListSection("Episodes"),
    DeviceListRow("Why the Daily Commute Changed", opensSubmenu = true),
    DeviceListRow("A Daily Habit, Ten Years Later", opensSubmenu = true),
)

// A show opened from search (frame 32): every episode with the arrow, and no dots.
private val SearchShow = listOf(
    DeviceListRow("Follow", value = "Off"),
    DeviceListRow("A Heat Wave Over the North Atlantic", download = DeviceListDownload.NotDownloaded),
    DeviceListRow("Inside the Race for a Cheaper Battery", download = DeviceListDownload.NotDownloaded),
    DeviceListRow("The Town That Saved Its Pharmacy", download = DeviceListDownload.NotDownloaded),
)

private val DisplayPreviewHeight = 318.dp

/** The highlight on the half dot, whose long title starts at its first word. */
@PreviewLightDark
@Composable
private fun DeviceListEpisodesPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("The Daily", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Episodes, highlightedIndex = 4)
            }
        }
    }
}

/** The highlight on a full dot and a percentage. */
@PreviewLightDark
@Composable
private fun DeviceListEpisodeDownloadingPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("The Daily", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Episodes, highlightedIndex = 2)
            }
        }
    }
}

/** The highlight on a full dot and the arrow. */
@PreviewLightDark
@Composable
private fun DeviceListEpisodeNotDownloadedPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("The Daily", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(Episodes, highlightedIndex = 1)
            }
        }
    }
}

/** The highlight on an arrow, with no dot. */
@PreviewLightDark
@Composable
private fun DeviceListSearchShowPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("The Daily", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(SearchShow, highlightedIndex = 1)
            }
        }
    }
}

/**
 * Section labels, and the highlight on a row with a secondary value, which gives way at 1.5x. The
 * highlight is on the first row of its section, so the section's label is in view above it.
 */
@PreviewLightDark
@Composable
private fun DeviceListSectionsPreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Search", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(SearchResults, highlightedIndex = 1)
            }
        }
    }
}

/** A message under the rows (frame 23). */
@PreviewLightDark
@Composable
private fun DeviceListMessagePreview() {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Podcasts", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                DeviceList(
                    listOf(DeviceListRow("Search", opensSubmenu = true)),
                    highlightedIndex = 0,
                    message = DeviceListMessage("No podcasts yet", "Select Search to find a show."),
                )
            }
        }
    }
}
