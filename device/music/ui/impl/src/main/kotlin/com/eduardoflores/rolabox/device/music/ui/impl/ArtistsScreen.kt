package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects the Artists list to its ViewModel, which the entry creates (ADR-021). Center on an artist
 * reports [onArtistClick] with its id.
 */
@Composable
internal fun ArtistsRoute(viewModel: ArtistsViewModel, onArtistClick: (artistId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unknownArtist = stringResource(R.string.music_unknown_artist)
    val rows = remember(state, unknownArtist) {
        state.map { DeviceListRow(it.name ?: unknownArtist, opensSubmenu = true) }
    }
    ScreenTitle(stringResource(R.string.music_artists_title))
    WheelList(
        state = rows,
        emptyText = stringResource(R.string.music_no_artists),
        onCenter = { index -> (state as? ListUiState.Loaded)?.items?.get(index)?.let { onArtistClick(it.id.value) } },
        sortedByName = true,
    )
}
