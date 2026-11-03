package com.eduardoflores.rolabox.device.library.local.impl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.local.impl.index.AlbumArtworkEntity
import com.eduardoflores.rolabox.device.library.local.impl.index.ArtworkState
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_1_2
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_2_3
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        assertEquals(emptyList<Any>(), database.libraryIndexDao().getIndexedSongs())
        database.close()
    }

    @Test
    fun version2_keepsItsCache_andGainsTheTableOfDownloadedCovers() = runTest {
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

        // Opening fails if the migrated tables aren't the ones the entities expect, which are exported as 3.json.
        val database = Room.databaseBuilder(context, LibraryDatabase::class.java, file.path)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        // The cache didn't change, so it isn't dropped.
        assertEquals("live 2012|coldplay", database.albumArtworkDao().albums().single().albumKey)
        val record = AlbumArtworkEntity("live 2012|coldplay", ArtworkState.NotFound, attempts = 1, lastAttemptAt = 5)
        database.albumArtworkDao().upsert(record)
        assertEquals(record, database.albumArtworkDao().observe("live 2012|coldplay").first())
        database.close()
    }
}
