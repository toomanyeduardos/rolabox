@file:Suppress("TooManyFunctions") // One for each state of the screens (ADR-016).

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
import com.eduardoflores.rolabox.device.library.api.Progress

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

// Every album, by title without a leading "The", and the album of songs with no album last.
private val Albums = listOf(
    "Coastal Lines", "Glass Garden", "Halfway Houses", "Harborlight", "Low Tide Radio", "North of Here",
    "Paper Satellites", "Quiet Hours", "Static Bloom", "The Long Weekend", "Under Lanterns", "Weather Report",
).map { DeviceListRow(it, opensSubmenu = true) }

@Composable
private fun DevicePreview(title: String, content: @Composable () -> Unit) {
    RolaboxTheme {
        DeviceScreen(title = title, batteryLevel = 0.7f, onWheelEvent = {}) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@PreviewLightDark
@Composable
private fun MusicMenuPreview() = DevicePreview(title = "Music") {
    MusicMenuScreen(MusicMenuUiState.Menu(readingTags = null), highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun PermissionDeniedPreview() = DevicePreview(title = "Music") {
    MusicMenuScreen(MusicMenuUiState.PermissionDenied, highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun PermissionPermanentlyDeniedPreview() = DevicePreview(title = "Music") {
    MusicMenuScreen(MusicMenuUiState.PermissionPermanentlyDenied, highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun FirstScanPreview() = DevicePreview(title = "Music") {
    MusicMenuScreen(MusicMenuUiState.Scanning, highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun NoMusicPreview() = DevicePreview(title = "Music") {
    MusicMenuScreen(MusicMenuUiState.Empty, highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun ReadingTagsPreview() = DevicePreview(title = "Music") {
    MusicMenuScreen(MusicMenuUiState.Menu(readingTags = Progress(done = 40, total = 100)), highlightedIndex = 0)
}

@PreviewLightDark
@Composable
private fun ArtistsPreview() = DevicePreview(title = "Artists") {
    MusicList(ListUiState.Loaded(Artists), highlightedIndex = 9, emptyText = stringResource(R.string.music_no_artists))
}

@PreviewLightDark
@Composable
private fun ArtistsFastTurningPreview() = DevicePreview(title = "Artists") {
    MusicList(
        ListUiState.Loaded(Artists),
        highlightedIndex = 9,
        emptyText = stringResource(R.string.music_no_artists),
        letter = "M",
    )
}

@PreviewLightDark
@Composable
private fun ArtistAlbumsPreview() = DevicePreview(title = "Maren Holt") {
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
private fun SongsPreview() = DevicePreview(title = "Paper Satellites") {
    MusicList(ListUiState.Loaded(Songs), highlightedIndex = 2, emptyText = stringResource(R.string.music_no_songs))
}

@PreviewLightDark
@Composable
private fun AlbumsPreview() = DevicePreview(title = "Albums") {
    val unknownAlbum = DeviceListRow(stringResource(R.string.music_unknown_album), opensSubmenu = true)
    val rows = Albums + unknownAlbum
    MusicList(
        ListUiState.Loaded(rows),
        highlightedIndex = rows.lastIndex,
        emptyText = stringResource(R.string.music_no_albums),
    )
}

@PreviewLightDark
@Composable
private fun AlbumsFastTurningPreview() = DevicePreview(title = "Albums") {
    MusicList(
        ListUiState.Loaded(Albums),
        highlightedIndex = 9,
        emptyText = stringResource(R.string.music_no_albums),
        letter = "L",
    )
}

@PreviewLightDark
@Composable
private fun AllSongsFastTurningPreview() = DevicePreview(title = "Songs") {
    MusicList(
        ListUiState.Loaded(Songs.sortedBy { it.label }),
        highlightedIndex = 6,
        emptyText = stringResource(R.string.music_no_songs),
        letter = "P",
    )
}

@PreviewLightDark
@Composable
private fun NoAlbumsPreview() = DevicePreview(title = "Albums") {
    MusicList(
        ListUiState.Loaded(emptyList()),
        highlightedIndex = 0,
        emptyText = stringResource(R.string.music_no_albums),
    )
}

@PreviewLightDark
@Composable
private fun EmptyPreview() = DevicePreview(title = "Artists") {
    MusicList(
        ListUiState.Loaded(emptyList()),
        highlightedIndex = 0,
        emptyText = stringResource(R.string.music_no_artists),
    )
}

@PreviewLightDark
@Composable
private fun FailedPreview() = DevicePreview(title = "Artists") {
    MusicList(ListUiState.Failed, highlightedIndex = 0, emptyText = stringResource(R.string.music_no_artists))
}
