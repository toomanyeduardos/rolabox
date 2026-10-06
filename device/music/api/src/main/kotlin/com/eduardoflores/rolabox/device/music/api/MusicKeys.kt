package com.eduardoflores.rolabox.device.music.api

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

/** The albums of an artist, after "All Songs". */
@Serializable
data class ArtistAlbumsKey(val artistId: Long) : NavKey

/** The songs of one album, in track order. */
@Serializable
data class AlbumSongsKey(val albumId: Long) : NavKey

/** All the songs of an artist, across their albums. */
@Serializable
data class ArtistSongsKey(val artistId: Long) : NavKey
