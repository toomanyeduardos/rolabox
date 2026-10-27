package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val OverlayMinSide = 104.dp
private val OverlayPadding = 18.dp
private val OverlayShape = RoundedCornerShape(18.dp)
private const val FADE_IN_MILLIS = 100
private const val FADE_OUT_MILLIS = 250

/**
 * The large letter over a list sorted by name while the wheel spins fast through it, so the user
 * knows where they are without reading the rows, as a [QuickScroll] reports it. It is centered over
 * the list, fades in with a [letter] and fades out when it is null. Put it over a [DeviceList], in
 * the same box.
 *
 * The square grows with the letter, which follows the font scale (ADR-017). It is decorative: the
 * highlighted row is what a screen reader reads.
 */
@Composable
fun DeviceLetterOverlay(letter: String?, modifier: Modifier = Modifier) {
    // Kept while the square fades out, when the letter is already null.
    val shown = remember { mutableStateOf(letter.orEmpty()) }
    if (letter != null) shown.value = letter
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = letter != null,
            enter = fadeIn(tween(FADE_IN_MILLIS)),
            exit = fadeOut(tween(FADE_OUT_MILLIS)),
        ) {
            Box(
                Modifier
                    .squareOfAtLeast(OverlayMinSide)
                    .clip(OverlayShape)
                    .background(RolaboxMetal.colors.displayOverlay)
                    .padding(OverlayPadding)
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(shown.value, style = RolaboxType.styles.displayOverlayLetter, maxLines = 1)
            }
        }
    }
}

/**
 * Makes the content a square as large as its longer side, and at least [minSide], so what is drawn
 * after this, such as a background, fills the square.
 */
private fun Modifier.squareOfAtLeast(minSide: Dp) = layout { measurable, _ ->
    val width = measurable.maxIntrinsicWidth(Constraints.Infinity)
    val height = measurable.maxIntrinsicHeight(width)
    val side = maxOf(width, height, minSide.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(side, side))
    layout(side, side) { placeable.place(0, 0) }
}

private val DisplayPreviewHeight = 318.dp

private val LongArtists = listOf(
    "Lantern Bay", "Late Arrivals", "Lowland Choir", "Lucid Mile", "Lyra Banks", "Mabel Quinn", "Maple Static",
    "Maren Holt", "Marlow", "Mason Reed", "Meadowlark", "Midnight Arcade", "Mira Sol", "Moth & Flame",
    "Muir Street", "Nadia Frost", "Northbound",
).map { DeviceListRow(it, opensSubmenu = true) }

private val NumberedArtists = listOf(
    "!Static",
    "10,000 Lakes",
    "2 Hour Drive",
    "4th Avenue",
    "99 Lanterns",
    "Adeline Park",
    "Blue Harbor",
).map { DeviceListRow(it, opensSubmenu = true) }

@Composable
private fun DeviceLetterOverlayPreview(rows: List<DeviceListRow>, highlightedIndex: Int, letter: String) {
    RolaboxTheme {
        Box(Modifier.padding(16.dp)) {
            DeviceDisplay("Artists", batteryLevel = 0.7f, modifier = Modifier.heightIn(max = DisplayPreviewHeight)) {
                Box {
                    DeviceList(rows, highlightedIndex)
                    DeviceLetterOverlay(letter)
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceLetterOverlayLetterPreview() = DeviceLetterOverlayPreview(LongArtists, highlightedIndex = 9, "M")

@PreviewLightDark
@Composable
private fun DeviceLetterOverlayOtherPreview() = DeviceLetterOverlayPreview(NumberedArtists, highlightedIndex = 2, "#")
