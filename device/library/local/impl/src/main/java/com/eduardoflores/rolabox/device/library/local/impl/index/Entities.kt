package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.eduardoflores.rolabox.device.library.local.api.FieldSource

/** One row per file. Every table is cache (ADR-024, rule 10): a rescan rebuilds it. */
@Entity(tableName = "songs", indices = [Index("mediaStoreId"), Index("artistId"), Index("albumId")])
internal data class SongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artistId: Long,
    val albumId: Long,
    val mediaStoreId: Long,
    val uri: String,
    val relativePath: String,
    val fileName: String,
    val size: Long,
    val dateModified: Long,
    val title: String?,
    val titleSource: FieldSource,
    val artist: String?,
    val artistSource: FieldSource,
    val albumArtist: String?,
    val albumArtistSource: FieldSource,
    val album: String?,
    val albumSource: FieldSource,
    val trackNumber: Int?,
    val trackNumberSource: FieldSource,
    val discNumber: Int?,
    val discNumberSource: FieldSource,
    val year: Int?,
    val yearSource: FieldSource,
    val compilation: Boolean?,
    val compilationSource: FieldSource,
    val durationMillis: Long?,
    val durationSource: FieldSource,
    /** Null until the tags are read, and for a format the parser doesn't read. */
    val hasEmbeddedPicture: Boolean? = null,
    /** The date modified at which the tags were last read, or null if they haven't been. */
    val tagsReadAt: Long? = null,
    /** The same song as another row's, which is the one the lists show (ADR-024, Grouping). */
    val isDuplicate: Boolean = false,
    /** The song's genres as its source gave them, separated by [GENRE_SEPARATOR], or null when it has none. */
    val genres: String? = null,
    val genreSource: FieldSource = FieldSource.Unknown,
    /** An MP4 whose audio is FairPlay's, which Android can't play (ADR-024, Reading the tags). */
    val isProtected: Boolean = false,
)

/** Between the genres of [SongEntity.genres]. No tag holds it, since it is a control character. */
internal const val GENRE_SEPARATOR = '\u001F'

/** A song with the keys of its artist and album, for a scan to compare. */
internal data class IndexedSongRow(@Embedded val song: SongEntity, val artistKey: String, val albumKey: String)

/** The columns the `:api`'s `Song` shows. */
internal data class SongRow(
    val id: Long,
    val albumId: Long,
    val artistId: Long,
    val title: String?,
    val trackNumber: Int?,
    val durationMillis: Long?,
    val uri: String,
    val discNumber: Int?,
    val isProtected: Boolean,
)

/** Where a song's audio is. */
internal data class SongLocationRow(val id: Long, val uri: String)

@Entity(tableName = "albums", indices = [Index("groupKey", unique = true), Index("artistId")])
internal data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupKey: String,
    val artistId: Long,
    val title: String?,
    val year: Int?,
)

/** An album, with the song whose cover stands for it (ADR-026), which its query picks. */
internal data class AlbumRow(@Embedded val album: AlbumEntity, val artworkSongId: Long)

@Entity(tableName = "artists", indices = [Index("groupKey", unique = true)])
internal data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupKey: String,
    val name: String?,
    val sortName: String?,
    val isVarious: Boolean,
)

/** One row per genre, found by its grouping key. The genre of songs with none has a null [name]. */
@Entity(tableName = "genres", indices = [Index("groupKey", unique = true)])
internal data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupKey: String,
    val name: String?,
)

/** A song in a genre. Every song has a row here: in the unknown genre when it has no other. */
@Entity(tableName = "song_genres", primaryKeys = ["songId", "genreId"], indices = [Index("genreId")])
internal data class SongGenreEntity(val songId: Long, val genreId: Long)

/** MediaStore's version and generation at the last scan, per volume. library/07 compares them. */
@Entity(tableName = "scan_state")
internal data class ScanStateEntity(@PrimaryKey val volume: String, val version: String, val generation: Long?)

/** What is known of an album's cover beyond the phone (ADR-027). */
internal enum class ArtworkState { Pending, Found, NotFound, OnPhone }

/**
 * The record of an album's cover, kept by the album's grouping key. It is the user's data (ADR-024, rule 10): a rescan
 * doesn't rebuild it, so a change to it has a migration, and the migrations of the cache tables leave it as it is.
 */
@Entity(tableName = "album_artwork")
internal data class AlbumArtworkEntity(
    @PrimaryKey val albumKey: String,
    val state: ArtworkState,
    /** The image in the app's files, when [state] is [ArtworkState.Found]. */
    val fileName: String? = null,
    /** The source's id of the album, when [state] is [ArtworkState.Found]. */
    val sourceId: String? = null,
    /** When it was found, in epoch millis. */
    val foundAt: Long? = null,
    /** How many times the source answered with no cover. */
    val attempts: Int = 0,
    /** When the source last answered with no cover, in epoch millis. */
    val lastAttemptAt: Long? = null,
)

/** An album in the index, with what the download reads of it and its record, if it has one. */
internal data class ArtworkAlbumRow(
    val albumKey: String,
    val title: String?,
    val artistName: String?,
    val isVarious: Boolean,
    val artworkSongUri: String?,
    val state: ArtworkState?,
    val fileName: String?,
    val sourceId: String?,
    val foundAt: Long?,
    val attempts: Int?,
    val lastAttemptAt: Long?,
    // The distinct artists of its songs, joined by a unit separator, since a row has no list.
    val songArtists: String?,
    val highestTrackNumber: Int?,
    val year: Int?,
)
