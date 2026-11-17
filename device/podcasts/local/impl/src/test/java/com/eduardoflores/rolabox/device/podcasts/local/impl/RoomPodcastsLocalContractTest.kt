package com.eduardoflores.rolabox.device.podcasts.local.impl

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeProgressLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The contract against the Room database, in memory, and what only the database keeps. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RoomPodcastsLocalContractTest : PodcastsLocalContract() {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        PodcastsDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val report = FakeNonFatalReport()

    override val local: PodcastsLocalDataSource =
        RoomPodcastsLocalDataSource(database.podcastsDao(), UnconfinedTestDispatcher(), report)
    override val progress: EpisodeProgressLocalDataSource =
        RoomEpisodeProgressLocalDataSource(database.progressDao(), report)

    @After
    fun closeDatabase() = database.close()

    @Test
    fun anUnfollow_isKept_withTheFeedSyncNeeds() = runTest {
        val showId = addShow(1)
        local.setFollow(showId, followed = true, NOW)
        local.setFollow(showId, followed = false, LATER)

        val follow = database.podcastsDao().getFollow(DirectoryId.itunes(1).value)

        assertEquals(FollowEntity("itunes-1", "https://example.com/1/feed", false, LATER.toEpochMilli()), follow)
    }
}
