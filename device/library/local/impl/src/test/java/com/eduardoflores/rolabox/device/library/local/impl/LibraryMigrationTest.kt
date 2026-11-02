package com.eduardoflores.rolabox.device.library.local.impl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.local.impl.index.LibraryDatabase
import com.eduardoflores.rolabox.device.library.local.impl.index.MIGRATION_1_2
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
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

        assertEquals(emptyList<Any>(), database.libraryIndexDao().getIndexedSongs())
        database.close()
    }
}
