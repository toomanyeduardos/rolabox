package com.eduardoflores.rolabox.device.library.local.impl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.local.impl.index.AlbumArtworkEntity
import com.eduardoflores.rolabox.device.library.local.impl.index.ArtworkState
import com.eduardoflores.rolabox.device.library.local.impl.index.CACHE_TABLES_OF_VERSION_4
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_1_2
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_2_3
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_3_4
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_4_5
import com.eduardoflores.rolabox.device.library.local.impl.index.TABLES_OF_VERSION_2
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryMigrationTest {
    @Test
    fun version1_isDroppedAndRecreated_soTheNextStartRescans() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-test.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, '98a9529dd0f58e16b55d7520cc996e93')")
            db.execSQL(
                "CREATE TABLE artists (id INTEGER PRIMARY KEY AUTOINCREMENT, groupKey TEXT NOT NULL, name TEXT)",
            )
            db.execSQL("INSERT INTO artists (groupKey, name) VALUES ('a', 'A')")
            db.version = 1
        }

        // Opening fails if the migrated tables aren't the ones the entities expect.
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        assertEquals(emptyList<Any>(), database.libraryIndexDao().getIndexedSongs())
        database.close()
    }

    @Test
    fun version2_gainsTheTableOfDownloadedCovers() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-2-test.db").apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Version 2 as Room exported it, in schemas/…/2.json.
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, '253f1ac5a50fcd742142d3bebc847a41')")
            TABLES_OF_VERSION_2.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO artists (groupKey, name, sortName, isVarious) " +
                    "VALUES ('coldplay', 'Coldplay', 'coldplay', 0)",
            )
            db.execSQL(
                "INSERT INTO albums (groupKey, artistId, title, year) " +
                    "VALUES ('live 2012|coldplay', 1, 'Live 2012', 2012)",
            )
            db.version = 2
        }

        // Opening fails if the migrated tables aren't the ones the entities expect, which are exported as 5.json.
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // 2 to 3 kept the cache, and 3 to 4 dropped it for the genres: the next start rescans.
        assertEquals(emptyList<Any>(), database.albumArtworkDao().albums())
        val record = AlbumArtworkEntity("live 2012|coldplay", ArtworkState.NotFound, attempts = 1, lastAttemptAt = 5)
        database.albumArtworkDao().upsert(record)
        assertEquals(record, database.albumArtworkDao().observe("live 2012|coldplay").first())
        database.close()
    }

    @Test
    fun version3_dropsItsCacheForTheGenres_andKeepsTheRecordsOfDownloadedCovers() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-3-test.db").apply { parentFile?.mkdirs() }
        val record = AlbumArtworkEntity("live 2012|coldplay", ArtworkState.Found, fileName = "c.jpg", sourceId = "s")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Version 3 as Room exported it, in schemas/…/3.json: version 2's tables and the covers' records.
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, '2270d5e037fd655bbc23dc288d64f65e')")
            TABLES_OF_VERSION_2.forEach(db::execSQL)
            @Suppress("MaxLineLength") // Room's own statement, as 3.json has it.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_artwork` (`albumKey` TEXT NOT NULL, `state` TEXT NOT NULL, `fileName` TEXT, `sourceId` TEXT, `foundAt` INTEGER, `attempts` INTEGER NOT NULL, `lastAttemptAt` INTEGER, PRIMARY KEY(`albumKey`))",
            )
            db.execSQL(
                "INSERT INTO artists (groupKey, name, sortName, isVarious) " +
                    "VALUES ('coldplay', 'Coldplay', 'coldplay', 0)",
            )
            db.execSQL(
                "INSERT INTO album_artwork (albumKey, state, fileName, sourceId, attempts) " +
                    "VALUES ('live 2012|coldplay', 'Found', 'c.jpg', 's', 0)",
            )
            db.version = 3
        }

        // Opening fails if the migrated tables aren't the ones the entities expect, which are exported as 5.json.
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        assertEquals(emptyList<Any>(), database.libraryIndexDao().getIndexedSongs())
        assertEquals(emptyList<Any>(), database.libraryIndexDao().observeGenres().first())
        assertEquals(record, database.albumArtworkDao().observe("live 2012|coldplay").first())
        database.close()
    }

    @Test
    fun version4_dropsItsCacheForProtectedSongs_andKeepsTheRecordsOfDownloadedCovers() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("migration-4-test.db").apply { parentFile?.mkdirs() }
        val record = AlbumArtworkEntity("live 2012|coldplay", ArtworkState.Found, fileName = "c.jpg", sourceId = "s")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            // Version 4 as Room exported it, in schemas/…/4.json: its cache tables and the covers' records.
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES (42, 'f8b4080eab344bc2fb7527ea0d8491ef')")
            CACHE_TABLES_OF_VERSION_4.forEach(db::execSQL)
            @Suppress("MaxLineLength") // Room's own statement, as 4.json has it.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `album_artwork` (`albumKey` TEXT NOT NULL, `state` TEXT NOT NULL, `fileName` TEXT, `sourceId` TEXT, `foundAt` INTEGER, `attempts` INTEGER NOT NULL, `lastAttemptAt` INTEGER, PRIMARY KEY(`albumKey`))",
            )
            db.execSQL(
                "INSERT INTO artists (groupKey, name, sortName, isVarious) " +
                    "VALUES ('coldplay', 'Coldplay', 'coldplay', 0)",
            )
            db.execSQL("INSERT INTO genres (groupKey, name) VALUES ('rock', 'Rock')")
            db.execSQL(
                "INSERT INTO album_artwork (albumKey, state, fileName, sourceId, attempts) " +
                    "VALUES ('live 2012|coldplay', 'Found', 'c.jpg', 's', 0)",
            )
            db.version = 4
        }

        // Opening fails if the migrated tables aren't the ones the entities expect, which are exported as 5.json.
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        assertEquals(emptyList<Any>(), database.libraryIndexDao().getIndexedSongs())
        assertEquals(emptyList<Any>(), database.libraryIndexDao().observeGenres().first())
        assertEquals(record, database.albumArtworkDao().observe("live 2012|coldplay").first())
        database.close()
    }
}
