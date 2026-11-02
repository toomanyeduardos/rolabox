package com.eduardoflores.rolabox.device.library.local.impl.tags

import com.eduardoflores.rolabox.device.library.local.api.FileTags

private const val HEAD_LENGTH = 12

// The formats whose tags aren't read yet: FLAC, OGG and Opus, and WAV (ADR-024).
private val UNREAD_FORMATS = listOf("fLaC", "OggS", "RIFF")

/**
 * Rolabox's own reader of ID3 and MP4 (ADR-024), plain Kotlin over bytes. A file with no tags has empty [FileTags],
 * and null means that it says it has tags and they can't be read: an MP4 with no `moov`, or an ID3v2 header with
 * nothing usable after it.
 */
internal fun parseTags(source: ByteSource): FileTags? {
    val head = source.read(0, HEAD_LENGTH)
    if (head.isMp4()) return readMp4(source)
    val id3v2 = readId3v2(source)
    val id3v1 = readId3v1(source)
    return when {
        id3v2 != null && id3v1 != null -> id3v2.orElse(id3v1)
        id3v2 != null -> id3v2
        id3v1 != null -> id3v1
        head.startsWith("ID3") -> null
        UNREAD_FORMATS.any(head::startsWith) -> FileTags()
        else -> FileTags(hasEmbeddedPicture = false)
    }
}

private fun ByteArray.startsWith(text: String) = size >= text.length && ascii(0, text.length) == text

// ID3v2 is the fuller tag, so ID3v1, cut at 30 characters, only fills what it lacks.
private fun FileTags.orElse(other: FileTags) = FileTags(
    title = title ?: other.title,
    artist = artist ?: other.artist,
    albumArtist = albumArtist ?: other.albumArtist,
    album = album ?: other.album,
    trackNumber = trackNumber ?: other.trackNumber,
    discNumber = discNumber ?: other.discNumber,
    year = year ?: other.year,
    compilation = compilation ?: other.compilation,
    durationMillis = durationMillis ?: other.durationMillis,
    hasEmbeddedPicture = hasEmbeddedPicture,
)
