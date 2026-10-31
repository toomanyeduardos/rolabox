package com.eduardoflores.rolabox.device.library.local.impl.index

import com.eduardoflores.rolabox.device.library.api.Album
import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.library.api.Artist
import com.eduardoflores.rolabox.device.library.api.ArtistId
import com.eduardoflores.rolabox.device.library.api.Song
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal fun ArtistEntity.toArtist() = Artist(ArtistId(id), name)

internal fun AlbumEntity.toAlbum() = Album(AlbumId(id), ArtistId(artistId), title, year)

internal fun SongRow.toSong() = Song(
    id = SongId(id),
    albumId = AlbumId(albumId),
    artistId = ArtistId(artistId),
    title = title,
    trackNumber = trackNumber,
    duration = durationMillis?.milliseconds ?: Duration.ZERO,
    uri = uri,
)

internal fun IndexedSongRow.toIndexedSong() = IndexedSong(
    id = SongId(song.id),
    file = SongFile(
        mediaStoreId = song.mediaStoreId,
        uri = song.uri,
        relativePath = song.relativePath,
        fileName = song.fileName,
        size = song.size,
        dateModified = song.dateModified,
    ),
    metadata = SongMetadata(
        title = sourced(song.title, song.titleSource),
        artist = sourced(song.artist, song.artistSource),
        albumArtist = sourced(song.albumArtist, song.albumArtistSource),
        album = sourced(song.album, song.albumSource),
        trackNumber = sourced(song.trackNumber, song.trackNumberSource),
        discNumber = sourced(song.discNumber, song.discNumberSource),
        year = sourced(song.year, song.yearSource),
        compilation = sourced(song.compilation, song.compilationSource),
        durationMillis = sourced(song.durationMillis, song.durationSource),
    ),
    artistKey = artistKey,
    albumKey = albumKey,
)

private fun <T : Any> sourced(value: T?, source: FieldSource) = Sourced(value, source)

internal fun SongWrite.toEntity(artistId: Long, albumId: Long) = SongEntity(
    id = id?.value ?: 0,
    artistId = artistId,
    albumId = albumId,
    mediaStoreId = file.mediaStoreId,
    uri = file.uri,
    relativePath = file.relativePath,
    fileName = file.fileName,
    size = file.size,
    dateModified = file.dateModified,
    title = metadata.title.value,
    titleSource = metadata.title.source,
    artist = metadata.artist.value,
    artistSource = metadata.artist.source,
    albumArtist = metadata.albumArtist.value,
    albumArtistSource = metadata.albumArtist.source,
    album = metadata.album.value,
    albumSource = metadata.album.source,
    trackNumber = metadata.trackNumber.value,
    trackNumberSource = metadata.trackNumber.source,
    discNumber = metadata.discNumber.value,
    discNumberSource = metadata.discNumber.source,
    year = metadata.year.value,
    yearSource = metadata.year.source,
    compilation = metadata.compilation.value,
    compilationSource = metadata.compilation.source,
    durationMillis = metadata.durationMillis.value,
    durationSource = metadata.durationMillis.source,
)
