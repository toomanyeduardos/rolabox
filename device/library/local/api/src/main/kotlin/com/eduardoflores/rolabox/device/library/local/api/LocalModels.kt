package com.eduardoflores.rolabox.device.library.local.api

import com.eduardoflores.rolabox.device.library.api.SongId

/** Where a field's value came from, strongest first (ADR-024, Metadata). */
enum class FieldSource { Tags, MediaStore, Folder, FileName, Unknown }

/** A field's value and its source. [value] is null exactly when [source] is [FieldSource.Unknown]. */
data class Sourced<T : Any>(val value: T?, val source: FieldSource)

/** A file as the system knows it. [dateModified] is in seconds since the epoch, as MediaStore gives it. */
data class SongFile(
    val mediaStoreId: Long,
    val uri: String,
    val relativePath: String,
    val fileName: String,
    val size: Long,
    val dateModified: Long,
)

/** A file as MediaStore lists it, with the system's reading of its tags. A value it doesn't have is null. */
data class AudioFile(
    val file: SongFile,
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val compilation: Boolean?,
    val durationMillis: Long?,
)

/** MediaStore's version and generation of one volume. Generations don't exist before API 30. */
data class VolumeScanState(val volume: String, val version: String, val generation: Long?)

/** Everything one query of MediaStore found, and the state of each volume when it did. */
data class AudioFileListing(val files: List<AudioFile>, val volumes: List<VolumeScanState>)

/** A song's fields, each with its source. */
data class SongMetadata(
    val title: Sourced<String>,
    val artist: Sourced<String>,
    val albumArtist: Sourced<String>,
    val album: Sourced<String>,
    val trackNumber: Sourced<Int>,
    val discNumber: Sourced<Int>,
    val year: Sourced<Int>,
    val compilation: Sourced<Boolean>,
    val durationMillis: Sourced<Long>,
)

/** An artist as the index finds it: by [key], which keeps its id across rescans. */
data class ArtistGroup(val key: String, val name: String?)

/** An album as the index finds it: by [key], which keeps its id across rescans. */
data class AlbumGroup(val key: String, val title: String?, val year: Int?, val artist: ArtistGroup)

/** A song as indexed, with the keys of the artist and the album it is on. */
data class IndexedSong(
    val id: SongId,
    val file: SongFile,
    val metadata: SongMetadata,
    val artistKey: String,
    val albumKey: String,
)

/** A song to add, when [id] is null, or to update. */
data class SongWrite(
    val id: SongId?,
    val file: SongFile,
    val metadata: SongMetadata,
    val artist: ArtistGroup,
    val album: AlbumGroup,
)

/** What one transaction of the index writes. Albums and artists left with no song go with it. */
data class IndexBatch(val writes: List<SongWrite> = emptyList(), val removals: List<SongId> = emptyList())
