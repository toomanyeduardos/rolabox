package com.eduardoflores.rolabox.device.library.api

import kotlin.time.Duration

@JvmInline
value class ArtistId(val value: Long)

@JvmInline
value class AlbumId(val value: Long)

@JvmInline
value class SongId(val value: Long)

@JvmInline
value class GenreId(val value: Long)

/**
 * What the library knows about a value can be nothing, whatever the file says: a name, a title, a year or a track
 * number is `null` then, and the words the user sees ("Unknown artist") are the screens' (ADR-024). [isVarious] is
 * the row of Various Artists, whose [name] is `null` too, for the screens to give the words of.
 */
data class Artist(val id: ArtistId, val name: String?, val isVarious: Boolean = false)

/**
 * A genre of the library. Names that differ only in case or whitespace are one genre, which shows the spelling on most
 * songs. [name] is `null` for the genre of songs with none, Unknown Genre, whose words are the screens' (ADR-024).
 */
data class Genre(val id: GenreId, val name: String?)

/** [artwork] is where the album's cover is: one of its songs, whose picture stands for the album (ADR-026). */
data class Album(val id: AlbumId, val artistId: ArtistId, val title: String?, val year: Int?, val artwork: ArtworkRef)

/**
 * A song of the library. [uri] is where its audio is, the `content://` URI MediaStore gives it, which playback hands to
 * the player as it is (ADR-023, rule 5, and ADR-024). It is a string, since this module has no `android.net.Uri`.
 * [artistId] is the song's own artist, who may not be the artist of its album. [duration] is zero when the library
 * doesn't know it. [discNumber] and [trackNumber] are where it is in its album, which is the order of the album's list:
 * disc, then track.
 */
data class Song(
    val id: SongId,
    val albumId: AlbumId,
    val artistId: ArtistId,
    val title: String?,
    val trackNumber: Int?,
    val duration: Duration,
    val uri: String,
    val discNumber: Int? = null,
)

/** How far a long piece of work has come: [done] of [total]. */
data class Progress(val done: Int, val total: Int)

/** What the library can show right now (ADR-024). */
sealed interface LibraryStatus {
    /** The audio permission isn't granted. Whatever the index holds stays readable. */
    data object PermissionMissing : LibraryStatus

    /** The index is empty, and the first pass of a scan is running. */
    data object Scanning : LibraryStatus

    /** A scan finished and found no music. */
    data object Empty : LibraryStatus

    /** The index has songs. [readingTags] is the progress of reading their tags, or null when that isn't running. */
    data class Ready(val readingTags: Progress?) : LibraryStatus
}
