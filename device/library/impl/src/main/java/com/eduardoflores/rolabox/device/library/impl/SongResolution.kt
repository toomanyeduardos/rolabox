package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.device.library.local.api.AudioFile
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.Sourced

/**
 * Pass 1: each field takes the best value that doesn't need the file opened (ADR-024, rule 7). When a file has no
 * tag, MediaStore makes its title the file's name and its album the folder's. Those are the weaker sources wearing
 * its name, so they count as nothing here, and the folder and the name are read by their own rules.
 */
internal fun AudioFile.toMetadata(): SongMetadata {
    val folder = folderOf(file.relativePath)
    val name = fileNameOf(file.fileName)
    return SongMetadata(
        title = firstOf(
            title?.takeUnless { it == file.fileName.stem() } to FieldSource.MediaStore,
            name.title to FieldSource.FileName,
        ),
        artist = firstOf(
            artist to FieldSource.MediaStore,
            folder?.artist to FieldSource.Folder,
            name.artist to FieldSource.FileName,
        ),
        albumArtist = firstOf(albumArtist to FieldSource.MediaStore),
        album = firstOf(
            album?.takeUnless { it == file.parentFolder() } to FieldSource.MediaStore,
            folder?.album to FieldSource.Folder,
        ),
        trackNumber = firstOf(trackNumber to FieldSource.MediaStore, name.track to FieldSource.FileName),
        discNumber = firstOf(discNumber to FieldSource.MediaStore),
        year = firstOf(year to FieldSource.MediaStore),
        compilation = firstOf(compilation to FieldSource.MediaStore),
        durationMillis = firstOf(durationMillis to FieldSource.MediaStore),
        // Neither the folder nor the name says a genre.
        genres = firstOf(genres.ifEmpty { null } to FieldSource.MediaStore),
    )
}

private fun SongFile.parentFolder(): String? = relativePath.trimEnd('/').substringAfterLast('/').ifEmpty { null }

private fun <T : Any> firstOf(vararg candidates: Pair<T?, FieldSource>): Sourced<T> {
    val (value, source) = candidates.firstOrNull { it.first != null } ?: return Sourced(null, FieldSource.Unknown)
    return Sourced(value, source)
}

/**
 * What a read of the tags makes of [this] song's values: a tag replaces any other source, and a missing tag leaves
 * what's there. Duration is a property of the audio, so MediaStore's stays over the parser's (rule 7).
 */
internal fun SongMetadata.withTags(tags: FileTags) = SongMetadata(
    title = title.orTag(tags.title),
    artist = artist.orTag(tags.artist),
    albumArtist = albumArtist.orTag(tags.albumArtist),
    album = album.orTag(tags.album),
    trackNumber = trackNumber.orTag(tags.trackNumber),
    discNumber = discNumber.orTag(tags.discNumber),
    year = year.orTag(tags.year),
    compilation = compilation.orTag(tags.compilation),
    durationMillis = if (durationMillis.source == FieldSource.MediaStore) {
        durationMillis
    } else {
        durationMillis.orTag(tags.durationMillis)
    },
    genres = genres.orTag(tags.genres.ifEmpty { null }),
)

private fun <T : Any> Sourced<T>.orTag(tag: T?): Sourced<T> = if (tag != null) Sourced(tag, FieldSource.Tags) else this

/**
 * [this] pass 1's reading of a file whose tags were read at its current date, with what it can't know kept from
 * [read]: a weaker source never replaces a stronger one, so a rescan doesn't undo pass 2. Duration is the one field
 * MediaStore beats the tags in.
 */
private fun SongMetadata.keepingStronger(read: SongMetadata) = SongMetadata(
    title = title.orStronger(read.title),
    artist = artist.orStronger(read.artist),
    albumArtist = albumArtist.orStronger(read.albumArtist),
    album = album.orStronger(read.album),
    trackNumber = trackNumber.orStronger(read.trackNumber),
    discNumber = discNumber.orStronger(read.discNumber),
    year = year.orStronger(read.year),
    compilation = compilation.orStronger(read.compilation),
    durationMillis = if (durationMillis.value != null) durationMillis else read.durationMillis,
    genres = genres.orStronger(read.genres),
)

// The sources are declared strongest first.
private fun <T : Any> Sourced<T>.orStronger(read: Sourced<T>): Sourced<T> =
    if (read.source.ordinal < source.ordinal) read else this

/**
 * [this] file as pass 1 sees it. While [song] was read at the file's current date, what the tags said stays. A file
 * that changed since then is read again by pass 2, and what its old tags said is gone.
 */
internal fun AudioFile.toDraft(song: IndexedSong?): SongDraft {
    val read = song?.takeIf { it.tagsReadAt == file.dateModified }
    val metadata = if (read != null) toMetadata().keepingStronger(read.metadata) else toMetadata()
    return SongDraft(song?.id, file, metadata, read?.hasEmbeddedPicture, read?.tagsReadAt, read?.isProtected == true)
}

/** [this] song once its tags have been read, or [tags] null when they couldn't be. */
internal fun IndexedSong.toDraftAfterReading(tags: FileTags?) = SongDraft(
    id = id,
    file = file,
    metadata = if (tags != null) metadata.withTags(tags) else metadata,
    hasEmbeddedPicture = tags?.hasEmbeddedPicture,
    tagsReadAt = file.dateModified,
    isProtected = tags?.isProtected == true,
)
