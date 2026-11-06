package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The library's own database (ADR-024, rule 5). Every table is cache but `album_artwork`: a change to a cache table
 * drops and recreates the cache tables in a migration, and the next start rescans. `album_artwork` is the user's data
 * (ADR-027), which those migrations leave as it is, and a change to it has a migration of its own (rule 10).
 */
@Database(
    entities = [
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ScanStateEntity::class,
        AlbumArtworkEntity::class,
        GenreEntity::class,
        SongGenreEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryIndexDao(): LibraryIndexDao

    abstract fun albumArtworkDao(): AlbumArtworkDao

    companion object {
        const val NAME = "library-index.db"
    }
}
