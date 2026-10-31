package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced

/** Between the title and the artist in an album's key. It can't be typed in a tag by accident. */
private const val KEY_SEPARATOR = '\u001F'

/** Pass 1: every field takes MediaStore's value, or none. The folder and the file name come with library/05. */
internal fun AudioFile.toMetadata() = SongMetadata(
    title = title.fromMediaStore(),
    artist = artist.fromMediaStore(),
    albumArtist = albumArtist.fromMediaStore(),
    album = album.fromMediaStore(),
    trackNumber = trackNumber.fromMediaStore(),
    discNumber = discNumber.fromMediaStore(),
    year = year.fromMediaStore(),
    compilation = compilation.fromMediaStore(),
    durationMillis = durationMillis.fromMediaStore(),
)

private fun <T : Any> T?.fromMediaStore(): Sourced<T> =
    Sourced(this, if (this == null) FieldSource.Unknown else FieldSource.MediaStore)

/**
 * The write of a song. Until library/06's grouping rules, an album is its title and its album artist, or its
 * artist when there is none. The song stays with its own artist, and a missing name is a group of its own.
 */
internal fun AudioFile.toWrite(id: SongId?): SongWrite {
    val metadata = toMetadata()
    val albumArtist = artistGroup(metadata.albumArtist.value ?: metadata.artist.value)
    val albumKey = "${metadata.album.value.orEmpty()}$KEY_SEPARATOR${albumArtist.key}"
    return SongWrite(
        id = id,
        file = file,
        metadata = metadata,
        artist = artistGroup(metadata.artist.value),
        album = AlbumGroup(albumKey, metadata.album.value, metadata.year.value, albumArtist),
    )
}

private fun artistGroup(name: String?) = ArtistGroup(name.orEmpty(), name)
