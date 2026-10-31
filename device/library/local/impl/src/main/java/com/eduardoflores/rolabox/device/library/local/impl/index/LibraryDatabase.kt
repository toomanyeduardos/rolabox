package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The library's own database (ADR-024, rule 5). Every table is cache, so a change to them drops and recreates
 * them in a migration, and the next start rescans (rule 10).
 */
@Database(
    entities = [SongEntity::class, AlbumEntity::class, ArtistEntity::class, ScanStateEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryIndexDao(): LibraryIndexDao

    companion object {
        const val NAME = "library-index.db"
    }
}
