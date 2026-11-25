package com.eduardoflores.rolabox.device.podcasts.local.impl

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.common.sync.testing.FakeLastWriteWins
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Every table is the user's data, so every version must open as the current one with what it held (ADR-031, rule 5).
 * A new version adds its schema, its migration in [PodcastsDatabase.MIGRATIONS], and a test here that its rows stay.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PodcastsMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun everyExportedVersion_opensAsTheCurrentOne() {
        val versions = ExportedSchemas.versions
        assertTrue("No exported schema in schemas/", versions.isNotEmpty())
        versions.forEach { version ->
            val file = context.getDatabasePath("podcasts-$version.db")
            ExportedSchemas.create(file, version)
            // Opening fails if a migration is missing, or the migrated tables aren't the ones the entities expect.
            open(file.path).apply {
                openHelper.writableDatabase
                close()
            }
        }
    }

    @Test
    fun version1_keepsTheUsersData() = runTest {
        val file = context.getDatabasePath("podcasts-1-data.db")
        ExportedSchemas.create(file, 1)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO shows (directoryId, feedUrl, title, titleSortKey, ownerName, ownerKey, ownerSortKey, " +
                    "joinedAt) VALUES ('itunes-1', 'https://example.com/feed', 'The Daily', 'daily', 'NY Times', " +
                    "'ny times', 'ny times', 1)",
            )
            db.execSQL(
                "INSERT INTO episodes (showId, itemKey, guid, title, audioUrl, listedAt) " +
                    "VALUES (1, 'g1', 'g1', 'Who Owns the Moon?', 'https://example.com/1.mp3', 1)",
            )
            db.execSQL(
                "INSERT INTO progress (episodeId, positionMillis, played, lastPlayedAt) VALUES (1, 720000, 0, 2)",
            )
            db.execSQL(
                "INSERT INTO follows (directoryId, feedUrl, followed, updatedAt) " +
                    "VALUES ('itunes-1', 'https://example.com/feed', 1, 3)",
            )
        }

        val database = open(file.path)
        val local =
            RoomPodcastsLocalDataSource(
                database.podcastsDao(),
                UnconfinedTestDispatcher(),
                FakeNonFatalReport(),
                FakeLastWriteWins(),
            )

        assertEquals(true, local.getShow(ShowId(1)).getOrNull()?.isFollowed)
        assertEquals(12.minutes, local.getEpisode(EpisodeId(1)).getOrNull()?.progress?.position)
        // No episode had left its feed before version 2 recorded it.
        assertEquals(null, local.getEpisode(EpisodeId(1)).getOrNull()?.leftFeedAt)
        assertEquals("Who Owns the Moon?", local.getEpisode(EpisodeId(1)).getOrNull()?.title)
        database.close()
    }

    // ADR-043: version 3 adds the feed's kind, unknown until the feed is read again, and the user's choices, none yet.
    @Test
    fun version2_keepsTheUsersData_andListsEveryShowNewestFirst() = runTest {
        val file = context.getDatabasePath("podcasts-2-data.db")
        ExportedSchemas.create(file, 2)
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL(
                "INSERT INTO shows (directoryId, feedUrl, title, titleSortKey, ownerName, ownerKey, ownerSortKey, " +
                    "joinedAt) VALUES ('itunes-1', 'https://example.com/feed', 'The Daily', 'daily', 'NY Times', " +
                    "'ny times', 'ny times', 1)",
            )
            db.execSQL(
                "INSERT INTO episodes (showId, itemKey, guid, title, publishedAt, audioUrl, listedAt, leftFeedAt) " +
                    "VALUES (1, 'g1', 'g1', 'Old', 100, 'https://example.com/1.mp3', 1, NULL), " +
                    "(1, 'g2', 'g2', 'New', 200, 'https://example.com/2.mp3', 1, NULL)",
            )
            db.execSQL(
                "INSERT INTO follows (directoryId, feedUrl, followed, updatedAt) " +
                    "VALUES ('itunes-1', 'https://example.com/feed', 1, 3)",
            )
        }

        val database = open(file.path)
        val local =
            RoomPodcastsLocalDataSource(
                database.podcastsDao(),
                UnconfinedTestDispatcher(),
                FakeNonFatalReport(),
                FakeLastWriteWins(),
            )

        val show = local.getShow(ShowId(1)).getOrNull()!!
        assertEquals(true, show.isFollowed)
        assertEquals(EpisodeOrder.Newest, show.episodeOrder)
        assertEquals(listOf("New", "Old"), local.observeEpisodesByShow(ShowId(1)).first().getOrNull()?.map { it.title })
        assertEquals(emptyMap<Any, Any>(), local.getSyncedEpisodeOrders().getOrNull())
        database.close()
    }

    private fun open(path: String): PodcastsDatabase = Room.databaseBuilder(context, PodcastsDatabase::class.java, path)
        .apply { PodcastsDatabase.MIGRATIONS.forEach { addMigrations(it) } }
        .allowMainThreadQueries()
        .build()
}
