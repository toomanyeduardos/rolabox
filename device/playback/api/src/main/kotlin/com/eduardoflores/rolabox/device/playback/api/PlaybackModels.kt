package com.eduardoflores.rolabox.device.playback.api

import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.SongId
import kotlin.time.Duration

/** Which item of which kind: one case per kind of item the queue can hold (ADR-023, rule 4). */
sealed interface PlaybackItemId {
    data class Song(val id: SongId) : PlaybackItemId
}

/**
 * An item in the queue, with what Now Playing shows: the names of its artist and album, not their ids. Where
 * its audio is doesn't belong here, since the screens see it (ADR-023, rule 4). What the library doesn't know is
 * `null`, and the screens have the words for it (ADR-024).
 */
data class PlaybackItem(
    val id: PlaybackItemId,
    val title: String?,
    val artist: String?,
    val album: String?,
    val duration: Duration,
)

/** Which way [PlaybackController.startSeeking] moves the position. */
enum class SeekDirection { Backward, Forward }

/** The list an item was selected from. Playing an item queues the whole list, in the order it is shown. */
sealed interface PlaybackSource {
    /** Every song of the library. */
    data object AllSongs : PlaybackSource

    data class ByArtist(val artistId: ArtistId) : PlaybackSource

    data class ByAlbum(val albumId: AlbumId) : PlaybackSource
}
