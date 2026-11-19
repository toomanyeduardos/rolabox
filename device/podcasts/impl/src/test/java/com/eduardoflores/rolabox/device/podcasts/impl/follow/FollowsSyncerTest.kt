package com.eduardoflores.rolabox.device.podcasts.impl.follow

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.sync.testing.FakeLastWriteWins
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.Follow
import com.eduardoflores.rolabox.device.podcasts.testing.FakeFollowsNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The merge of the follows of two phones, one this phone and the other the cloud (ADR-031, Syncing follows). */
@OptIn(ExperimentalCoroutinesApi::class)
class FollowsSyncerTest {
    private val local = FakePodcastsLocalDataSource()
    private val network = FakeFollowsNetworkDataSource()
    private val followCheck = FakeFollowCheckScheduler()
    private val syncer = FollowsSyncer(local, network, FakeLastWriteWins(), followCheck)

    private fun synced(id: Long, followed: Boolean, at: Long) =
        SyncedValue(Follow(feedOf(id), followed), SyncTimestamp(at))

    private fun storeLocally(id: Long, followed: Boolean, at: Long) =
        local.storeFollow(DirectoryId.itunes(id), feedOf(id), followed, Instant.ofEpochMilli(at))

    private suspend fun localFollows() = local.getSyncedFollows().getOrNull()!!

    @Test
    fun theSameShowFollowedOnBothPhones_staysFollowed_andIsntReadAgain() = runTest {
        storeLocally(1, followed = true, at = 1_000)
        network.remote = mapOf(DAILY to synced(1, followed = true, at = 2_000))

        assertEquals(Unit.right(), syncer.sync(USER))

        assertEquals(mapOf(DAILY to synced(1, followed = true, at = 2_000)), localFollows())
        assertEquals(emptyList<DirectoryId>(), network.written)
        assertEquals(emptyList<DirectoryId?>(), followCheck.requested)
    }

    // The unfollow was made offline, after the follow that the cloud already had.
    @Test
    fun anUnfollowMadeOffline_winsOverTheOlderFollow_andIsKeptInTheCloud() = runTest {
        storeLocally(1, followed = false, at = 2_000)
        network.remote = mapOf(DAILY to synced(1, followed = true, at = 1_000))

        syncer.sync(USER)

        assertEquals(synced(1, followed = false, at = 2_000), network.remote[DAILY])
        assertEquals(listOf(DAILY), network.written)
        assertEquals(synced(1, followed = false, at = 2_000), localFollows()[DAILY])
    }

    @Test
    fun anUnfollowOnTheOtherPhone_winsOverAnOlderFollowHere() = runTest {
        storeLocally(1, followed = true, at = 1_000)
        network.remote = mapOf(DAILY to synced(1, followed = false, at = 2_000))

        syncer.sync(USER)

        assertEquals(synced(1, followed = false, at = 2_000), localFollows()[DAILY])
        assertEquals(emptyList<DirectoryId>(), network.written)
    }

    @Test
    fun aFollowOnTheOtherPhone_winsOverAnOlderUnfollowHere_andHasItsFeedRead() = runTest {
        storeLocally(1, followed = false, at = 1_000)
        network.remote = mapOf(DAILY to synced(1, followed = true, at = 2_000))

        syncer.sync(USER)

        assertEquals(synced(1, followed = true, at = 2_000), localFollows()[DAILY])
        assertEquals(listOf<DirectoryId?>(DAILY), followCheck.requested)
    }

    // Each show is resolved on its own: a whole list replaced by the newer one would drop one of them.
    @Test
    fun differentShowsFollowedOnTwoPhones_areBothKept() = runTest {
        storeLocally(1, followed = true, at = 2_000)
        network.remote = mapOf(SERIAL to synced(2, followed = true, at = 1_000))

        syncer.sync(USER)

        val both = mapOf(
            DAILY to synced(1, followed = true, at = 2_000),
            SERIAL to synced(2, followed = true, at = 1_000),
        )
        assertEquals(both, localFollows())
        assertEquals(both, network.remote)
        assertEquals(listOf<DirectoryId?>(SERIAL), followCheck.requested)
    }

    // Follows made without an account were stored with their time, so signing in merges them like any change.
    @Test
    fun followsMadeWithoutAnAccount_mergeWithTheAccountsOnSignIn_showByShow() = runTest {
        storeLocally(1, followed = true, at = 3_000)
        storeLocally(2, followed = true, at = 1_000)
        network.remote = mapOf(
            DAILY to synced(1, followed = false, at = 2_000),
            SERIAL to synced(2, followed = false, at = 2_000),
            RADIOLAB to synced(3, followed = true, at = 2_000),
        )

        syncer.sync(USER)

        val merged = mapOf(
            DAILY to synced(1, followed = true, at = 3_000),
            SERIAL to synced(2, followed = false, at = 2_000),
            RADIOLAB to synced(3, followed = true, at = 2_000),
        )
        assertEquals(merged, localFollows())
        assertEquals(merged, network.remote)
        assertEquals(listOf(DAILY), network.written)
        assertEquals(listOf<DirectoryId?>(RADIOLAB), followCheck.requested)
    }

    @Test
    fun aRemoteFailure_appliesNothing() = runTest {
        storeLocally(1, followed = true, at = 1_000)
        network.remote = mapOf(SERIAL to synced(2, followed = true, at = 1_000))
        network.error = RemoteError.Unavailable

        assertEquals(SyncError.Remote(RemoteError.Unavailable).left(), syncer.sync(USER))
        assertEquals(setOf(DAILY), localFollows().keys)
    }

    @Test
    fun aDatabaseFailure_isALocalError_andWritesNothingToTheCloud() = runTest {
        local.readError = StorageError.Corrupted

        assertEquals(SyncError.Local(StorageError.Corrupted).left(), syncer.sync(USER))
        assertEquals(emptyList<DirectoryId>(), network.written)
    }

    @Test
    fun aChangedFollow_isALocalChange_butWhatIsStoredWhenWatchingStartsIsnt() = runTest {
        storeLocally(1, followed = true, at = 1_000)
        val changes = mutableListOf<Unit>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { syncer.observeLocalChanges().toList(changes) }

        storeLocally(1, followed = false, at = 2_000)

        assertEquals(1, changes.size)
        watching.cancel()
    }

    private companion object {
        const val USER = "alice"
        val DAILY = DirectoryId.itunes(1)
        val SERIAL = DirectoryId.itunes(2)
        val RADIOLAB = DirectoryId.itunes(3)

        fun feedOf(id: Long) = "https://example.com/$id/feed"
    }
}
