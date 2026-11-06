package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.host.ScreenTitle

/**
 * Connects the Genres list to its ViewModel, which the entry creates (ADR-021). Center on a genre reports
 * [onGenreClick] with its id, which opens its artists. The genre of songs with none is "Unknown Genre", last.
 */
@Composable
internal fun GenresRoute(viewModel: GenresViewModel, onGenreClick: (genreId: Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unknownGenre = stringResource(R.string.music_unknown_genre)
    val rows = remember(state, unknownGenre) {
        state.map { genre -> DeviceListRow(genre.name ?: unknownGenre, opensSubmenu = true) }
    }
    ScreenTitle(stringResource(R.string.music_genres_title))
    WheelList(
        state = rows,
        emptyText = stringResource(R.string.music_no_genres),
        onCenter = { index -> (state as? ListUiState.Loaded)?.items?.get(index)?.let { onGenreClick(it.id.value) } },
        sortedByName = true,
    )
}
