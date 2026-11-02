package com.eduardoflores.rolabox.device.library.impl

import java.util.Locale

/** The artist and album a file's folders say, when they look like `…/Artist/Album/file`. */
internal data class FolderMetadata(val artist: String, val album: String)

/** What a file's name says. A value it doesn't say is null. */
internal data class FileNameMetadata(val title: String?, val artist: String?, val track: Int?)

/** Folders that say where music is kept, and not whose it is. Compared in lower case. */
private val GENERIC_FOLDERS = setOf(
    "albums", "artists", "audio", "bluetooth", "download", "downloads", "itunes", "itunes media", "media",
    "mp3", "mp3s", "music", "my music", "new folder", "podcasts", "recordings", "ringtones", "songs", "telegram",
    "tracks", "unknown", "unknown album", "unknown artist", "whatsapp",
)

/** The fewest folders that are a path's artist and album, below its top one. */
private const val ARTIST_AND_ALBUM = 2

// A number, then a dash, dot, underscore or bracket: "3 - Title". Without one, only two or three digits make a track,
// so "7 Rings" stays a title. A four-digit number is a year or a date.
private val LEADING_TRACK = Regex("""^(\d{1,3})\s*[-._)]\s*(\S.*)$|^(\d{2,3})\s+(\S.*)$""")

private const val ARTIST_SEPARATOR = " - "

/**
 * The folder fallback, the weakest guess that names an artist. The top folder (Music, Download…) is the system's, and
 * a name that only says where music is kept isn't an artist or an album. So the last two folders under the top one
 * count, only when neither of them is generic: `Music/Dump/x.mp3` says nothing, rather than inventing an artist.
 */
internal fun folderOf(relativePath: String): FolderMetadata? {
    val folders = relativePath.split('/').map(String::trim).filter(String::isNotEmpty).drop(1)
    if (folders.size < ARTIST_AND_ALBUM) return null
    val (artist, album) = folders.takeLast(ARTIST_AND_ALBUM)
    val generic = listOf(artist, album).any { it.lowercase(Locale.ROOT) in GENERIC_FOLDERS }
    return if (generic) null else FolderMetadata(artist, album)
}

/** Reads `Artist - Title.ext`, `01 Title.ext` and `01 - Artist - Title.ext`. Only no name at all has no title. */
internal fun fileNameOf(fileName: String): FileNameMetadata {
    val stem = fileName.stem().trim()
    val numbered = LEADING_TRACK.find(stem)?.groupValues
    val digits = numbered?.let { it[1].ifEmpty { it[3] } }
    val rest = numbered?.let { it[2].ifEmpty { it[4] } } ?: stem
    val artist = rest.substringBefore(ARTIST_SEPARATOR, "").trim().ifEmpty { null }
    val title = if (artist != null) rest.substringAfter(ARTIST_SEPARATOR).trim() else rest
    return FileNameMetadata(
        title = title.ifEmpty { stem }.ifEmpty { null },
        artist = artist,
        track = digits?.toInt()?.takeIf { it > 0 },
    )
}

/** [this] file name without its extension. */
internal fun String.stem(): String = substringBeforeLast('.', this)
