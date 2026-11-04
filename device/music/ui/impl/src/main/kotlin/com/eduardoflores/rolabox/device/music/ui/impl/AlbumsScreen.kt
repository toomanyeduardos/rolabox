package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects the Albums list to its ViewModel, which the entry creates (ADR-021). Center on an album reports
 * [onAlbumClick] with its id, which opens the same songs as from an artist's albums. The album of songs with no
 * album is "Unknown album", last.
 */
@Composable
internal fun AlbumsRoute(viewModel: AlbumsViewModel, onAlbumClick: (albumId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unknownAlbum = stringResource(R.string.music_unknown_album)
    val rows = remember(state, unknownAlbum) {
        state.map { album -> DeviceListRow(album.title ?: unknownAlbum, opensSubmenu = true) }
    }
    ScreenTitle(stringResource(R.string.music_albums_title))
    WheelList(
        state = rows,
        emptyText = stringResource(R.string.music_no_albums),
        onCenter = { index -> (state as? ListUiState.Loaded)?.items?.get(index)?.let { onAlbumClick(it.id.value) } },
        sortedByName = true,
    )
}
