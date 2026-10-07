package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow

/**
 * Connects a list of songs to its ViewModel, which the entry creates (ADR-021). A song opens
 * nothing on the display: center on it reports [onSongClick] with its id.
 */
@Composable
internal fun SongsRoute(viewModel: SongsViewModel, onSongClick: (songId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val rows = remember(state) { state.map { DeviceListRow(it.title) } }
    WheelList(
        state = rows,
        emptyText = stringResource(R.string.music_no_songs),
        onCenter = { index -> (state as? ListUiState.Loaded)?.items?.get(index)?.let { onSongClick(it.id.value) } },
    )
}
