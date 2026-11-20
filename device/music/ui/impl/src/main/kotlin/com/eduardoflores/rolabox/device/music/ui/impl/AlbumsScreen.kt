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

// Every album, by title without a leading "The", and the album of songs with no album last.
private val Albums = listOf(
    "Coastal Lines", "Glass Garden", "Halfway Houses", "Harborlight", "Low Tide Radio", "North of Here",
    "Paper Satellites", "Quiet Hours", "Static Bloom", "The Long Weekend", "Under Lanterns", "Weather Report",
).map { DeviceListRow(it, opensSubmenu = true) }

@PreviewLightDark
@Composable
private fun AlbumsPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Albums", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val unknownAlbum = DeviceListRow(stringResource(R.string.music_unknown_album), opensSubmenu = true)
                val rows = Albums + unknownAlbum
                MusicList(
                    ListUiState.Loaded(rows),
                    highlightedIndex = rows.lastIndex,
                    emptyText = stringResource(R.string.music_no_albums),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AlbumsFastTurningPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Albums", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                MusicList(
                    ListUiState.Loaded(Albums),
                    highlightedIndex = 9,
                    emptyText = stringResource(R.string.music_no_albums),
                    letter = "L",
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NoAlbumsPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Albums", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                MusicList(
                    ListUiState.Loaded(emptyList()),
                    highlightedIndex = 0,
                    emptyText = stringResource(R.string.music_no_albums),
                )
            }
        }
    }
}
