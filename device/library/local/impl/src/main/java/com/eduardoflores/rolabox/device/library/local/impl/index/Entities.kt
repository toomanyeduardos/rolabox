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
    /** Null until the tags are read (library/05). */
    val hasEmbeddedPicture: Boolean? = null,
    /** The date modified at which the tags were last read, or null if they haven't been (library/05). */
    val tagsReadAt: Long? = null,
)

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
)

@Entity(tableName = "albums", indices = [Index("groupKey", unique = true), Index("artistId")])
internal data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupKey: String,
    val artistId: Long,
    val title: String?,
    val year: Int?,
)

@Entity(tableName = "artists", indices = [Index("groupKey", unique = true)])
internal data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupKey: String,
    val name: String?,
)

/** MediaStore's version and generation at the last scan, per volume. library/07 compares them. */
@Entity(tableName = "scan_state")
internal data class ScanStateEntity(@PrimaryKey val volume: String, val version: String, val generation: Long?)
