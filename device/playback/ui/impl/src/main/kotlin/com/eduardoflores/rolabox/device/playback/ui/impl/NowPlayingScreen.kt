package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceCoverArtPlaceholder
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceProgressBar
import com.eduardoflores.rolabox.common.designsystem.component.ProgressBarMode
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType

private val ScreenPadding = 12.dp
private val ScreenTopPadding = 10.dp
private val SectionGap = 12.dp
private val CoverGap = 12.dp
private val TextGap = 4.dp
private val CoverSize = 108.dp
private val CoverMinSize = 56.dp
private const val TITLE_MAX_LINES = 2

/**
 * Connects Now Playing to its ViewModel, which the entry creates (ADR-021). The screen takes no input yet:
 * it only shows the state. Scrub and volume are modes that come with the wheel's events.
 */
@Composable
internal fun NowPlayingRoute(viewModel: NowPlayingViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NowPlayingScreen(state)
}

/** The stateless screen that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun NowPlayingScreen(state: NowPlayingUiState) {
    when (state) {
        // The playback state is in memory, so this is a frame. Nothing to say until it is slow.
        NowPlayingUiState.Loading -> Unit

        NowPlayingUiState.NothingLoaded -> DeviceMessage(stringResource(R.string.now_playing_nothing_loaded))

        is NowPlayingUiState.Playing -> NowPlayingContent(state)
    }
}

/**
 * The song on the display, top to bottom: its place in the queue, its cover art with its title, artist and
 * album, and the bar with the time. It can't scroll, since the wheel is its volume (ADR-018), so at large
 * font scales the text keeps its space and the cover art shrinks to what is left, down to a minimum.
 * The long lines of the song end in an ellipsis, so they never push the bar off the display.
 */
@Composable
private fun NowPlayingContent(state: NowPlayingUiState.Playing) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = ScreenPadding, end = ScreenPadding, top = ScreenTopPadding, bottom = ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(SectionGap),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.now_playing_position, state.number, state.count),
                style = RolaboxType.styles.displayMeta,
            )
            state.tag?.let { Text(it, style = RolaboxType.styles.displayMeta) }
        }
        // Takes what the meta row and the bar leave. The cover art can only use this, so the bar stays at the bottom.
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopStart) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoverGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeviceCoverArtPlaceholder(
                    Modifier
                        .sizeIn(
                            minWidth = CoverMinSize,
                            minHeight = CoverMinSize,
                            maxWidth = CoverSize,
                            maxHeight = CoverSize,
                        )
                        .aspectRatio(1f),
                )
                SongNames(state, Modifier.weight(1f))
            }
        }
        DeviceProgressBar(
            progress = state.progress,
            startLabel = state.position.toClock(),
            endLabel = timeLeftClock(state.position, state.duration),
            description = stringResource(R.string.now_playing_progress_description),
            mode = ProgressBarMode.Normal,
        )
    }
}

/** The song's title, artist and album. Each one ends in an ellipsis when it is longer than its lines. */
@Composable
private fun SongNames(state: NowPlayingUiState.Playing, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TextGap)) {
        Text(
            state.title,
            style = RolaboxType.styles.displayHeadline,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(state.artist, style = RolaboxType.styles.displayBody, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(state.album, style = RolaboxType.styles.displayCaption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
