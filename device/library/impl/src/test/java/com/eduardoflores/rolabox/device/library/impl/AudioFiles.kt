package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.SongFile

/** A file of a test: everything is derived from [id], unless the test is about it. */
@Suppress("LongParameterList") // One parameter per thing a test can vary.
internal fun audioFile(
    id: Long,
    path: String = "Music/",
    name: String = "$id.mp3",
    size: Long = 1000 + id,
    dateModified: Long = 100,
    title: String? = "Song $id",
    artist: String? = "Artist",
    albumArtist: String? = null,
    album: String? = "Album",
    year: Int? = null,
) = AudioFile(
    file = SongFile(id, "content://media/external/audio/media/$id", path, name, size, dateModified),
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    trackNumber = null,
    discNumber = null,
    year = year,
    compilation = null,
    durationMillis = 1000,
)
