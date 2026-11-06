package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2 as Room exported it. Room checks the tables against the entities when the migration ends. */
@Suppress("MaxLineLength") // Room's own statements, to compare with the exported schema as they are.
internal val TABLES_OF_VERSION_2 = listOf(
    "CREATE TABLE IF NOT EXISTS `songs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `artistId` INTEGER NOT NULL, `albumId` INTEGER NOT NULL, `mediaStoreId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `fileName` TEXT NOT NULL, `size` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL, `title` TEXT, `titleSource` TEXT NOT NULL, `artist` TEXT, `artistSource` TEXT NOT NULL, `albumArtist` TEXT, `albumArtistSource` TEXT NOT NULL, `album` TEXT, `albumSource` TEXT NOT NULL, `trackNumber` INTEGER, `trackNumberSource` TEXT NOT NULL, `discNumber` INTEGER, `discNumberSource` TEXT NOT NULL, `year` INTEGER, `yearSource` TEXT NOT NULL, `compilation` INTEGER, `compilationSource` TEXT NOT NULL, `durationMillis` INTEGER, `durationSource` TEXT NOT NULL, `hasEmbeddedPicture` INTEGER, `tagsReadAt` INTEGER, `isDuplicate` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_songs_mediaStoreId` ON `songs` (`mediaStoreId`)",
    "CREATE INDEX IF NOT EXISTS `index_songs_artistId` ON `songs` (`artistId`)",
    "CREATE INDEX IF NOT EXISTS `index_songs_albumId` ON `songs` (`albumId`)",
    "CREATE TABLE IF NOT EXISTS `albums` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupKey` TEXT NOT NULL, `artistId` INTEGER NOT NULL, `title` TEXT, `year` INTEGER)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_groupKey` ON `albums` (`groupKey`)",
    "CREATE INDEX IF NOT EXISTS `index_albums_artistId` ON `albums` (`artistId`)",
    "CREATE TABLE IF NOT EXISTS `artists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupKey` TEXT NOT NULL, `name` TEXT, `sortName` TEXT, `isVarious` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_groupKey` ON `artists` (`groupKey`)",
    "CREATE TABLE IF NOT EXISTS `scan_state` (`volume` TEXT NOT NULL, `version` TEXT NOT NULL, `generation` INTEGER, PRIMARY KEY(`volume`))",
)

/** library/06's grouping rules. Every table is cache, so they are dropped and the next start rescans (rule 10). */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("songs", "albums", "artists", "scan_state").forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
        TABLES_OF_VERSION_2.forEach(db::execSQL)
    }
}

/**
 * library/08's record of the covers it downloads, the first table of the user's data (ADR-027). The cache tables
 * didn't change, so they stay, and nothing is rescanned.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        @Suppress("MaxLineLength") // Room's own statement, to compare with the exported schema as it is.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `album_artwork` (`albumKey` TEXT NOT NULL, `state` TEXT NOT NULL, `fileName` TEXT, `sourceId` TEXT, `foundAt` INTEGER, `attempts` INTEGER NOT NULL, `lastAttemptAt` INTEGER, PRIMARY KEY(`albumKey`))",
        )
    }
}

/** The cache tables of version 4 as Room exported them: version 2's, with the genres (music/02). */
@Suppress("MaxLineLength") // Room's own statements, to compare with the exported schema as they are.
internal val CACHE_TABLES_OF_VERSION_4 = listOf(
    "CREATE TABLE IF NOT EXISTS `songs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `artistId` INTEGER NOT NULL, `albumId` INTEGER NOT NULL, `mediaStoreId` INTEGER NOT NULL, `uri` TEXT NOT NULL, `relativePath` TEXT NOT NULL, `fileName` TEXT NOT NULL, `size` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL, `title` TEXT, `titleSource` TEXT NOT NULL, `artist` TEXT, `artistSource` TEXT NOT NULL, `albumArtist` TEXT, `albumArtistSource` TEXT NOT NULL, `album` TEXT, `albumSource` TEXT NOT NULL, `trackNumber` INTEGER, `trackNumberSource` TEXT NOT NULL, `discNumber` INTEGER, `discNumberSource` TEXT NOT NULL, `year` INTEGER, `yearSource` TEXT NOT NULL, `compilation` INTEGER, `compilationSource` TEXT NOT NULL, `durationMillis` INTEGER, `durationSource` TEXT NOT NULL, `hasEmbeddedPicture` INTEGER, `tagsReadAt` INTEGER, `isDuplicate` INTEGER NOT NULL, `genres` TEXT, `genreSource` TEXT NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_songs_mediaStoreId` ON `songs` (`mediaStoreId`)",
    "CREATE INDEX IF NOT EXISTS `index_songs_artistId` ON `songs` (`artistId`)",
    "CREATE INDEX IF NOT EXISTS `index_songs_albumId` ON `songs` (`albumId`)",
    "CREATE TABLE IF NOT EXISTS `albums` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupKey` TEXT NOT NULL, `artistId` INTEGER NOT NULL, `title` TEXT, `year` INTEGER)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_albums_groupKey` ON `albums` (`groupKey`)",
    "CREATE INDEX IF NOT EXISTS `index_albums_artistId` ON `albums` (`artistId`)",
    "CREATE TABLE IF NOT EXISTS `artists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupKey` TEXT NOT NULL, `name` TEXT, `sortName` TEXT, `isVarious` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_artists_groupKey` ON `artists` (`groupKey`)",
    "CREATE TABLE IF NOT EXISTS `scan_state` (`volume` TEXT NOT NULL, `version` TEXT NOT NULL, `generation` INTEGER, PRIMARY KEY(`volume`))",
    "CREATE TABLE IF NOT EXISTS `genres` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupKey` TEXT NOT NULL, `name` TEXT)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_genres_groupKey` ON `genres` (`groupKey`)",
    "CREATE TABLE IF NOT EXISTS `song_genres` (`songId` INTEGER NOT NULL, `genreId` INTEGER NOT NULL, PRIMARY KEY(`songId`, `genreId`))",
    "CREATE INDEX IF NOT EXISTS `index_song_genres_genreId` ON `song_genres` (`genreId`)",
)

/**
 * music/02's genres, a change to cache tables: they are dropped and recreated, and the next start rescans (rule 10).
 * The record of downloaded covers is the user's data, and stays as it is.
 */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("songs", "albums", "artists", "scan_state").forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
        CACHE_TABLES_OF_VERSION_4.forEach(db::execSQL)
    }
}
