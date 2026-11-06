package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects a list of artists to its ViewModel, which the entry creates (ADR-021): every artist, or a genre's. Center
 * on an artist reports [onArtistClick] with its id. The screen's title is Artists, or the genre's name.
 */
@Composable
internal fun ArtistsRoute(viewModel: ArtistsViewModel, onArtistClick: (artistId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unknownArtist = stringResource(R.string.music_unknown_artist)
    val variousArtists = stringResource(R.string.music_various_artists)
    val rows = remember(state, unknownArtist, variousArtists) {
        state.map { artist ->
            val label = artist.name ?: if (artist.isVarious) variousArtists else unknownArtist
            DeviceListRow(label, opensSubmenu = true)
        }
    }
    val title by viewModel.title.collectAsStateWithLifecycle()
    ScreenTitle(title.label())
    WheelList(
        state = rows,
        emptyText = stringResource(R.string.music_no_artists),
        onCenter = { index -> (state as? ListUiState.Loaded)?.items?.get(index)?.let { onArtistClick(it.id.value) } },
        sortedByName = true,
    )
}
