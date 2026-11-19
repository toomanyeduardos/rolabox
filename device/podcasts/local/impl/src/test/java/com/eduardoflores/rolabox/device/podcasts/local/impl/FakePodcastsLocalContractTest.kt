package com.eduardoflores.rolabox.device.podcasts.local.impl

import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The contract against the fake the repositories in `:device:podcasts:impl` are tested with, so those tests stand for
 * the database.
 */
class FakePodcastsLocalContractTest : PodcastsLocalContract() {
    private val fake = FakePodcastsLocalDataSource()

    override val local = fake
    override val progress = fake

    override suspend fun storeFollow(directoryId: DirectoryId, feedUrl: String, followed: Boolean) =
        fake.storeFollow(directoryId, feedUrl, followed, NOW)

    override suspend fun assertFollowFeedUrl(directoryId: DirectoryId, feedUrl: String) {
        assertEquals(feedUrl, fake.followFeedUrls[directoryId])
        assertEquals(NOW, fake.follows[directoryId]?.second)
    }

    @Test
    fun anUnfollow_isKept() = runTest {
        val showId = addShow(1)
        local.setFollow(showId, followed = true, NOW)
        local.setFollow(showId, followed = false, LATER)

        assertEquals(false to LATER, fake.follows[DirectoryId.itunes(1)])
    }
}
