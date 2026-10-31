package com.eduardoflores.rolabox.device.library.local.impl.mediastore

import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.SongFile

/** What older versions of Android store in place of a missing artist or album. It is no value. */
private const val UNKNOWN_MARKER = "<unknown>"

/** MediaStore numbers a track on a disc as `disc * 1000 + track`. */
private const val DISC_FACTOR = 1000

/** One row of MediaStore's audio collection, as its columns hold it. A column this API doesn't have is null. */
internal data class MediaStoreRow(
    val id: Long,
    val relativePath: String?,
    val displayName: String?,
    val size: Long?,
    val dateModified: Long?,
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val track: Int?,
    val discNumber: String?,
    val year: Int?,
    val compilation: String?,
    val durationMillis: Long?,
)

/** The index's reading of [this] row, with its content [uri]. Every field is MediaStore's value, or none. */
internal fun MediaStoreRow.toAudioFile(uri: String) = AudioFile(
    file = SongFile(
        mediaStoreId = id,
        uri = uri,
        relativePath = relativePath.orEmpty(),
        fileName = displayName.orEmpty(),
        size = size ?: 0,
        dateModified = dateModified ?: 0,
    ),
    title = title.text(),
    artist = artist.text(),
    albumArtist = albumArtist.text(),
    album = album.text(),
    trackNumber = track?.rem(DISC_FACTOR).positive(),
    discNumber = discNumber?.substringBefore('/')?.trim()?.toIntOrNull().positive()
        ?: track?.div(DISC_FACTOR).positive(),
    year = year.positive(),
    compilation = compilation?.trim()?.toIntOrNull()?.let { it != 0 },
    durationMillis = durationMillis?.takeIf { it > 0 },
)

private fun String?.text(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != UNKNOWN_MARKER }

private fun Int?.positive(): Int? = this?.takeIf { it > 0 }
