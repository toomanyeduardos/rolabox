package com.eduardoflores.rolabox.device.library.api

import kotlin.time.Duration

@JvmInline
value class ArtistId(val value: Long)

@JvmInline
value class AlbumId(val value: Long)

@JvmInline
value class SongId(val value: Long)

data class Artist(val id: ArtistId, val name: String)

data class Album(val id: AlbumId, val artistId: ArtistId, val title: String, val year: Int)

data class Song(
    val id: SongId,
    val albumId: AlbumId,
    val artistId: ArtistId,
    val title: String,
    val trackNumber: Int,
    val duration: Duration,
)
