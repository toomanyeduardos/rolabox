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

// Every genre by name, and the genre of songs with none last.
private val Genres = listOf(
    "Alternative", "Ambient", "Blues", "Classical", "Dream Pop", "Electronic", "Folk", "Hip-Hop", "Jazz", "Pop",
    "R&B", "Rock", "Soundtrack",
).map { DeviceListRow(it, opensSubmenu = true) }

@PreviewLightDark
@Composable
private fun GenresPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Genres", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                val unknownGenre = DeviceListRow(stringResource(R.string.music_unknown_genre), opensSubmenu = true)
                val rows = Genres + unknownGenre
                MusicList(
                    ListUiState.Loaded(rows),
                    highlightedIndex = rows.lastIndex,
                    emptyText = stringResource(R.string.music_no_genres),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun GenresFastTurningPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Genres", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                MusicList(
                    ListUiState.Loaded(Genres),
                    highlightedIndex = 8,
                    emptyText = stringResource(R.string.music_no_genres),
                    letter = "J",
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NoGenresPreview() {
    RolaboxTheme {
        DeviceScreen(title = "Genres", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) {
                MusicList(
                    ListUiState.Loaded(emptyList()),
                    highlightedIndex = 0,
                    emptyText = stringResource(R.string.music_no_genres),
                )
            }
        }
    }
}
