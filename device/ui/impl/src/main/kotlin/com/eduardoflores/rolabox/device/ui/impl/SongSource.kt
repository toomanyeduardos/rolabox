package com.eduardoflores.rolabox.device.ui.impl

import androidx.navigation3.runtime.NavKey
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.music.ui.api.AlbumSongsKey
import com.eduardoflores.rolabox.device.music.ui.api.ArtistSongsKey
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource

/**
 * The list a song was selected from, which is the list of the screen it was clicked on (ADR-018, rule 17).
 * `null` for a screen that isn't a list of songs.
 */
internal fun playbackSourceOf(screen: NavKey): PlaybackSource? = when (screen) {
    is AlbumSongsKey -> PlaybackSource.ByAlbum(AlbumId(screen.albumId))
    is ArtistSongsKey -> PlaybackSource.ByArtist(ArtistId(screen.artistId))
    else -> null
}
