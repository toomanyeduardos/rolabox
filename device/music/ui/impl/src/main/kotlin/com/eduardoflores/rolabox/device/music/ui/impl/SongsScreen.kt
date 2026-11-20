package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects a list of songs to its ViewModel, which the entry creates (ADR-021). A song opens
 * nothing on the display: center on it reports [onSongClick] with its id, and so does ⏯, since a song
 * is a row that can be played (ADR-018, rule 5). The screen's title is the name of what the songs are of.
 * [sortedByName] is for songs sorted by title, which show the fast-scroll letter: all of an artist's, or every song.
 */
@Composable
internal fun SongsRoute(viewModel: SongsViewModel, sortedByName: Boolean, onSongClick: (songId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val unknownSong = stringResource(R.string.music_unknown_song)
    val rows = remember(state, unknownSong) { state.map { DeviceListRow(it.title ?: unknownSong) } }
    ScreenTitle(title.label())
    val onSongRow: (Int) -> Unit = { index ->
        (state as? ListUiState.Loaded)?.items?.get(index)?.let { onSongClick(it.id.value) }
    }
    WheelList(
        state = rows,
        emptyText = stringResource(R.string.music_no_songs),
        onCenter = onSongRow,
        onPlayPause = onSongRow,
        sortedByName = sortedByName,
    )
}

private val Songs = listOf(
    "Dial Tone", "Every Other Window", "Glasshouse", "Tin Can Summer", "Weather Report", "Satellite Hymn",
    "Coastline", "Halfway Houses", "Night Bus", "Paper Satellites",
).map { DeviceListRow(it) }

@PreviewLightDark
@Composable
private fun SongsPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Paper Satellites", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                MusicList(
                    ListUiState.Loaded(Songs),
                    highlightedIndex = 2,
                    emptyText = stringResource(R.string.music_no_songs),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AllSongsFastTurningPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Songs", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                MusicList(
                    ListUiState.Loaded(Songs.sortedBy { it.label }),
                    highlightedIndex = 6,
                    emptyText = stringResource(R.string.music_no_songs),
                    letter = "P",
                )
            }
        }
    }
}
