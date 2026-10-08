package com.eduardoflores.rolabox.device.playback.api

import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlin.time.Duration

/** A song in the queue, with what Now Playing shows: the names of its artist and album, not their ids. */
data class PlaybackSong(
    val id: SongId,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Duration,
)

/** The list a song was selected from. Playing a song queues the whole list, in the order it is shown. */
sealed interface PlaybackSource {
    /** Every song of the library. */
    data object AllSongs : PlaybackSource

    data class ByArtist(val artistId: ArtistId) : PlaybackSource

    data class ByAlbum(val albumId: AlbumId) : PlaybackSource
}
