package com.eduardoflores.rolabox.device.playback.ui.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceCoverArtPlaceholder
import com.eduardoflores.rolabox.common.designsystem.component.DeviceMessage
import com.eduardoflores.rolabox.common.designsystem.component.DeviceProgressBar
import com.eduardoflores.rolabox.common.designsystem.component.ProgressBarMode
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxMetal
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxType
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandleMenu
import com.eduardoflores.rolabox.device.host.HandleWheelEvents

private val ScreenPadding = 12.dp
private val ScreenTopPadding = 10.dp
private val SectionGap = 12.dp
private val TextGap = 4.dp
private val CoverMinSize = 56.dp
private const val TITLE_MAX_LINES = 2

/**
 * Connects Now Playing to its ViewModel, which the entry creates (ADR-021). Turn and center go to the
 * ViewModel, and MENU too while scrubbing, so it leaves scrub and the host doesn't go back a screen
 * (ADR-018, rule 16). The ViewModel is also told when the app goes to the background, with the system's
 * back or with Home, which ends scrub: the screen takes no back of its own (ADR-018, rule 9).
 */
@Composable
internal fun NowPlayingRoute(viewModel: NowPlayingViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> viewModel.onTurn(event.steps)
            WheelEvent.Center -> viewModel.onCenter()
            else -> Unit
        }
    }
    HandleMenu(
        enabled = (state as? NowPlayingUiState.Playing)?.mode == NowPlayingMode.Scrub,
        onMenu = viewModel::onMenu,
    )
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackground() }
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
 * The song on the display, top to bottom: its place in the queue, its cover art, its title, artist and
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
            when (state.mode) {
                NowPlayingMode.Time -> Unit

                NowPlayingMode.Scrub -> ModeTag(
                    stringResource(R.string.now_playing_tag_scrub),
                    RolaboxMetal.colors.accent,
                )

                NowPlayingMode.Volume -> ModeTag(stringResource(R.string.now_playing_tag_volume))
            }
        }
        // Takes what the meta row, the names and the bar leave. The cover art is the largest square that fits in it,
        // so it grows with the display and the names keep their space.
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            DeviceCoverArtPlaceholder(Modifier.size(minOf(maxWidth, maxHeight).coerceAtLeast(CoverMinSize)))
        }
        SongNames(state, Modifier.fillMaxWidth())
        NowPlayingBar(state)
    }
}

/** The bar: the time, and the volume while it is changing. Scrub is the marker's time with a thicker, orange bar. */
@Composable
private fun NowPlayingBar(state: NowPlayingUiState.Playing) {
    if (state.mode == NowPlayingMode.Volume) {
        DeviceProgressBar(
            progress = state.volumeFraction,
            startLabel = stringResource(R.string.now_playing_volume_label),
            endLabel = stringResource(R.string.now_playing_volume_level, state.volume),
            description = stringResource(R.string.now_playing_volume_description),
            mode = ProgressBarMode.Volume,
        )
    } else {
        DeviceProgressBar(
            progress = state.progress,
            startLabel = state.position.toClock(),
            endLabel = timeLeftClock(state.position, state.duration),
            description = stringResource(R.string.now_playing_progress_description),
            mode = if (state.mode == NowPlayingMode.Scrub) ProgressBarMode.Scrub else ProgressBarMode.Normal,
        )
    }
}

@Composable
private fun ModeTag(text: String, color: Color = Color.Unspecified) {
    Text(text, style = RolaboxType.styles.displayMeta, color = color)
}

/** The song's title, artist and album. Each one ends in an ellipsis when it is longer than its lines. */
@Composable
private fun SongNames(state: NowPlayingUiState.Playing, modifier: Modifier = Modifier) {
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(TextGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            state.title,
            style = RolaboxType.styles.displayHeadline,
            textAlign = TextAlign.Center,
            maxLines = TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            state.artist,
            style = RolaboxType.styles.displayBody,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            state.album,
            style = RolaboxType.styles.displayCaption,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
