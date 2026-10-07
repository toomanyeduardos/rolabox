package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.common.designsystem.component.DeviceList
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.wheel.WheelEvent
import com.eduardoflores.rolabox.device.host.HandleWheelEvents
import com.eduardoflores.rolabox.device.host.rememberListHighlight

/** The rows of the Music menu, in the order they are shown. */
private enum class MusicMenuRow { Artists, Albums, Songs, Genres, Playlists }

/**
 * The Music menu. Turning moves the highlight, which stops at the first and last rows. Center on
 * Artists reports [onArtistsClick]; the other rows do nothing until their screens exist.
 */
@Composable
internal fun MusicMenuRoute(onArtistsClick: () -> Unit) {
    val highlight = rememberListHighlight()
    HandleWheelEvents { event ->
        when (event) {
            is WheelEvent.Turn -> highlight.move(event.steps, MusicMenuRow.entries.size)

            WheelEvent.Center -> when (MusicMenuRow.entries[highlight.index]) {
                MusicMenuRow.Artists -> onArtistsClick()
                MusicMenuRow.Albums, MusicMenuRow.Songs, MusicMenuRow.Genres, MusicMenuRow.Playlists -> Unit
            }

            else -> Unit
        }
    }
    MusicMenu(highlightedIndex = highlight.index)
}

/** The stateless menu that previews and screenshot tests call (ADR-021, rule 4). */
@Composable
internal fun MusicMenu(highlightedIndex: Int) {
    val rows = MusicMenuRow.entries.map { row ->
        val label = when (row) {
            MusicMenuRow.Artists -> stringResource(R.string.music_menu_artists)
            MusicMenuRow.Albums -> stringResource(R.string.music_menu_albums)
            MusicMenuRow.Songs -> stringResource(R.string.music_menu_songs)
            MusicMenuRow.Genres -> stringResource(R.string.music_menu_genres)
            MusicMenuRow.Playlists -> stringResource(R.string.music_menu_playlists)
        }
        DeviceListRow(label, opensSubmenu = true)
    }
    DeviceList(rows = rows, highlightedIndex = highlightedIndex)
}
