package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

/** How the queue plays, which Now Playing shows after its place in the queue (ADR-028). Off shows nothing. */
enum class DeviceQueueIcon { Shuffle, Repeat, RepeatOne }

/** The height of the icons, in dp so they stay the same at every font scale: they are symbols, not text. */
private val IconHeight = 12.dp
private val IconGap = 6.dp
private const val STROKE_WIDTH = 1.6f
private const val ICON_HEIGHT = 12f
private const val ICON_WIDTH = 16f
private const val WIDE_ICON_WIDTH = 21f

/**
 * [icons] in a row, in the display's ink, as the designs draw them (player-designs/device, frames 22 and 23). Each
 * says what it is to TalkBack.
 */
@Composable
fun DeviceQueueIcons(icons: List<DeviceQueueIcon>, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(IconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEach { icon ->
            Icon(
                icon.vector,
                contentDescription = stringResource(icon.description),
                modifier = Modifier.height(IconHeight),
                tint = RolaboxMetal.colors.displayInk,
            )
        }
    }
}

private val DeviceQueueIcon.vector: ImageVector
    get() = when (this) {
        DeviceQueueIcon.Shuffle -> ShuffleIcon
        DeviceQueueIcon.Repeat -> RepeatIcon
        DeviceQueueIcon.RepeatOne -> RepeatOneIcon
    }

private val DeviceQueueIcon.description: Int
    get() = when (this) {
        DeviceQueueIcon.Shuffle -> R.string.ds_shuffle_on
        DeviceQueueIcon.Repeat -> R.string.ds_repeat_all
        DeviceQueueIcon.RepeatOne -> R.string.ds_repeat_one
    }

// Two crossing lines with an arrow at each end.
private val ShuffleIcon = strokedIcon(
    "Shuffle",
    ICON_WIDTH,
    listOf(
        "M1 2.5h3c1 0 1.6.4 2.2 1.2l3.2 4.6c.6.8 1.2 1.2 2.2 1.2H14",
        "M1 9.5h3c1 0 1.6-.4 2.2-1.2l.5-.7",
        "M9.3 4.4l.5-.7c.6-.8 1.2-1.2 2.2-1.2H14",
        "M12.4 1l1.8 1.5-1.8 1.5",
        "M12.4 8l1.8 1.5-1.8 1.5",
    ),
)

// A loop of two arrows.
private val RepeatPaths = listOf(
    "M2 6.5V4.5C2 3.7 2.7 3 3.5 3H13",
    "M11.4 1.2L13.3 3l-1.9 1.8",
    "M14 5.5v2c0 .8-.7 1.5-1.5 1.5H3",
    "M4.6 10.8L2.7 9l1.9-1.8",
)

private val RepeatIcon = strokedIcon("Repeat", ICON_WIDTH, RepeatPaths)

// The loop, with a 1 after it.
private val RepeatOneIcon = strokedIcon("RepeatOne", WIDE_ICON_WIDTH, RepeatPaths + "M17.2 3.6L19 2.2v8.6")

/** An icon of round-ended strokes, from the paths of the design's SVG, [width] by 12 of its units. */
private fun strokedIcon(name: String, width: Float, paths: List<String>): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = width.dp,
    defaultHeight = ICON_HEIGHT.dp,
    viewportWidth = width,
    viewportHeight = ICON_HEIGHT,
).apply {
    paths.forEach { path ->
        addPath(
            pathData = addPathNodes(path),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
}.build()

@PreviewLightDark
@Composable
private fun DeviceQueueIconsPreview() {
    RolaboxTheme {
        Box(Modifier.background(RolaboxMetal.colors.displayScreen).padding(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("4 OF 10", style = RolaboxType.styles.displayMeta)
                    DeviceQueueIcons(listOf(DeviceQueueIcon.Shuffle, DeviceQueueIcon.Repeat))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("3 OF 10", style = RolaboxType.styles.displayMeta)
                    DeviceQueueIcons(listOf(DeviceQueueIcon.RepeatOne))
                }
            }
        }
    }
}
