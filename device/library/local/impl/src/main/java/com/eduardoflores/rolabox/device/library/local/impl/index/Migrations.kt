package com.eduardoflores.rolabox.device.library.local.impl.index

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Version 2 as Room exported it. Room checks the tables against the entities when the migration ends. */
@Suppress("MaxLineLength") // Room's own statements, to compare with the exported schema as they are.
private val TABLES_OF_VERSION_2 = listOf(
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
