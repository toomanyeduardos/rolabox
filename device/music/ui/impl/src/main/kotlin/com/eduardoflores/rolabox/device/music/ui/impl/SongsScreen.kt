package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects a list of songs to its ViewModel, which the entry creates (ADR-021). A song opens
 * nothing on the display: center on it reports [onSongClick] with its id, and so does ⏯, since a song
 * is a row that can be played (ADR-018, rule 5). The screen's title is the name of what the songs are of.
 */
@Composable
internal fun SongsRoute(viewModel: SongsViewModel, onSongClick: (songId: Long) -> Unit) {
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
    )
}
