package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.PreviewLightDark
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.device.host.DeviceScreen

// The screens as the designs show them (player-designs/device), on the assembled device. They call
// the stateless composables with explicit state (ADR-016, rule 3).

private val Artists = listOf(
    "Adeline Park", "Blue Harbor", "Cass Monroe", "Dune Collective", "Ellis Grey", "Fern & Iron", "Hollow Pines",
    "Juno Vale", "Kite Theory", "Maren Holt", "Northbound", "Otis Ray", "Pale Lanterns", "Quiet Fleet",
).map { DeviceListRow(it, opensSubmenu = true) }

private val Songs = listOf(
    "Dial Tone", "Every Other Window", "Glasshouse", "Tin Can Summer", "Weather Report", "Satellite Hymn",
    "Coastline", "Halfway Houses", "Night Bus", "Paper Satellites",
).map { DeviceListRow(it) }

@Composable
private fun DevicePreview(content: @Composable () -> Unit) {
    RolaboxTheme {
        DeviceScreen(title = "Rolabox", batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@PreviewLightDark
@Composable
private fun MusicMenuPreview() = DevicePreview { MusicMenu(highlightedIndex = 0) }

@PreviewLightDark
@Composable
private fun ArtistsPreview() = DevicePreview {
    MusicList(ListUiState.Loaded(Artists), highlightedIndex = 9, emptyText = stringResource(R.string.music_no_artists))
}

@PreviewLightDark
@Composable
private fun ArtistAlbumsPreview() = DevicePreview {
    val rows = listOf("Low Tide Radio", "Paper Satellites", "North of Here").map { DeviceListRow(it, true) }
    val allSongs = DeviceListRow(stringResource(R.string.music_all_songs), opensSubmenu = true)
    MusicList(
        ListUiState.Loaded(listOf(allSongs) + rows),
        highlightedIndex = 2,
        emptyText = stringResource(R.string.music_no_albums),
    )
}

@PreviewLightDark
@Composable
private fun SongsPreview() = DevicePreview {
    MusicList(ListUiState.Loaded(Songs), highlightedIndex = 2, emptyText = stringResource(R.string.music_no_songs))
}

@PreviewLightDark
@Composable
private fun EmptyPreview() = DevicePreview {
    MusicList(
        ListUiState.Loaded(emptyList()),
        highlightedIndex = 0,
        emptyText = stringResource(R.string.music_no_artists),
    )
}

@PreviewLightDark
@Composable
private fun FailedPreview() = DevicePreview {
    MusicList(ListUiState.Failed, highlightedIndex = 0, emptyText = stringResource(R.string.music_no_artists))
}
