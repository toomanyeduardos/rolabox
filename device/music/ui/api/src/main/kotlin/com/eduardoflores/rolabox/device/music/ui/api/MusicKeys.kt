package com.eduardoflores.rolabox.device.music.ui.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The keys of the music screens on the screen stack inside the display (ADR-018). They carry the ids
// of what they show, not models (ADR-012, rule 2).

/** The Music menu: Artists, Albums, Songs, Genres and Playlists. */
@Serializable
data object MusicMenuKey : NavKey

/** Every artist of the library. */
@Serializable
data object ArtistsKey : NavKey

/** Every album of the library, by title. */
@Serializable
data object AlbumsKey : NavKey

/** Every song of the library, by title. */
@Serializable
data object SongsKey : NavKey

/** The albums of an artist, after "All Songs". */
@Serializable
data class ArtistAlbumsKey(val artistId: Long) : NavKey

/** The songs of one album, in track order, from an artist's albums or from Albums. */
@Serializable
data class AlbumSongsKey(val albumId: Long) : NavKey

/** All the songs of an artist, across their albums. */
@Serializable
data class ArtistSongsKey(val artistId: Long) : NavKey

/** Every genre of the library, by name, with Unknown Genre last. */
@Serializable
data object GenresKey : NavKey

/** The artists with a song in a genre. */
@Serializable
data class GenreArtistsKey(val genreId: Long) : NavKey

/** An artist's albums with a song in a genre, after "All Songs". */
@Serializable
data class GenreArtistAlbumsKey(val genreId: Long, val artistId: Long) : NavKey

/** An artist's songs in a genre, across their albums. */
@Serializable
data class GenreArtistSongsKey(val genreId: Long, val artistId: Long) : NavKey

/** An album's songs in a genre, in track order. */
@Serializable
data class GenreAlbumSongsKey(val genreId: Long, val albumId: Long) : NavKey
