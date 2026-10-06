package com.eduardoflores.rolabox.device.music.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.library.api.Album

/**
 * Connects the albums of the artist [artistId] to its ViewModel, which the entry creates (ADR-021).
 * "All Songs" comes first, and then the albums. Center on it reports [onAllSongsClick] with the
 * artist, and on an album [onAlbumClick] with its id.
 */
@Composable
internal fun ArtistAlbumsRoute(
    viewModel: ArtistAlbumsViewModel,
    artistId: Long,
    onAllSongsClick: (artistId: Long) -> Unit,
    onAlbumClick: (albumId: Long) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val allSongs = stringResource(R.string.music_all_songs)
    WheelList(
        state = state.withAllSongs(allSongs),
        emptyText = stringResource(R.string.music_no_albums),
        onCenter = { index ->
            if (index == ALL_SONGS_INDEX) {
                onAllSongsClick(artistId)
            } else {
                (state as? ListUiState.Loaded)?.items?.get(index - 1)?.let { onAlbumClick(it.id.value) }
            }
        },
    )
}

private const val ALL_SONGS_INDEX = 0

/** The rows of the albums screen: "All Songs", which an artist always has, and then their albums. */
internal fun ListUiState<Album>.withAllSongs(allSongsLabel: String): ListUiState<DeviceListRow> = when (this) {
    ListUiState.Loading -> ListUiState.Loading

    is ListUiState.Loaded -> ListUiState.Loaded(
        listOf(DeviceListRow(allSongsLabel, opensSubmenu = true)) +
            items.map { DeviceListRow(it.title, opensSubmenu = true) },
    )

    ListUiState.Failed -> ListUiState.Failed
}
