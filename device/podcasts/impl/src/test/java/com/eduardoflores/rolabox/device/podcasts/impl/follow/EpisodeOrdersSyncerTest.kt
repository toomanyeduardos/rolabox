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
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice.Feed
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice.Newest
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice.Oldest
import com.eduardoflores.rolabox.device.podcasts.testing.FakeEpisodeOrdersNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The merge of the choices of episode order of two phones, this one and the cloud (ADR-043, Syncing the choice). */
@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeOrdersSyncerTest {
    private val local = FakePodcastsLocalDataSource()
    private val network = FakeEpisodeOrdersNetworkDataSource()
    private val syncer = EpisodeOrdersSyncer(local, network, FakeLastWriteWins())

    private fun synced(choice: EpisodeOrderChoice, at: Long) = SyncedValue(choice, SyncTimestamp(at))

    private fun storeLocally(directoryId: DirectoryId, choice: EpisodeOrderChoice, at: Long) =
        local.storeEpisodeOrder(directoryId, choice, Instant.ofEpochMilli(at))

    @Test
    fun aChoiceMadeHere_isWrittenToTheCloud() = runTest {
        storeLocally(SERIAL, Oldest, at = 1_000)

        assertEquals(Unit.right(), syncer.sync(USER))

        assertEquals(mapOf(SERIAL to synced(Oldest, 1_000)), network.remote)
        assertEquals(listOf(SERIAL), network.written)
    }

    @Test
    fun aChoiceMadeOnTheOtherPhone_isApplied_andNotWrittenBack() = runTest {
        network.remote = mapOf(SERIAL to synced(Oldest, 1_000))

        syncer.sync(USER)

        assertEquals(mapOf(SERIAL to synced(Oldest, 1_000)), local.episodeOrders)
        assertEquals(emptyList<DirectoryId>(), network.written)
    }

    @Test
    fun theNewerChoice_winsOnBothSides() = runTest {
        storeLocally(SERIAL, Newest, at = 2_000)
        storeLocally(DAILY, Oldest, at = 1_000)
        network.remote = mapOf(SERIAL to synced(Oldest, 1_000), DAILY to synced(Newest, 2_000))

        syncer.sync(USER)

        val expected = mapOf(SERIAL to synced(Newest, 2_000), DAILY to synced(Newest, 2_000))
        assertEquals(expected, local.episodeOrders)
        assertEquals(expected, network.remote)
        assertEquals(listOf(SERIAL), network.written)
    }

    // A cleared choice is Feed, kept so it wins over an older choice from another phone, never a deleted document.
    @Test
    fun aClearedChoice_winsOverAnOlderOne_andIsKept() = runTest {
        storeLocally(SERIAL, Feed, at = 2_000)
        network.remote = mapOf(SERIAL to synced(Oldest, 1_000))

        syncer.sync(USER)

        assertEquals(synced(Feed, 2_000), network.remote[SERIAL])
        assertEquals(synced(Feed, 2_000), local.episodeOrders[SERIAL])
    }

    // Each show is resolved on its own: a whole list replaced by the newer one would drop one of them.
    @Test
    fun choicesForDifferentShowsOnTwoPhones_areBothKept() = runTest {
        storeLocally(SERIAL, Oldest, at = 2_000)
        network.remote = mapOf(DAILY to synced(Newest, 1_000))

        syncer.sync(USER)

        val both = mapOf(SERIAL to synced(Oldest, 2_000), DAILY to synced(Newest, 1_000))
        assertEquals(both, local.episodeOrders)
        assertEquals(both, network.remote)
    }

    // Choices made without an account were stored with their time, so the first sync merges them like any change.
    @Test
    fun choicesMadeWithoutAnAccount_areMergedOnTheFirstSync() = runTest {
        storeLocally(SERIAL, Oldest, at = 3_000)
        network.remote = mapOf(SERIAL to synced(Newest, 1_000), DAILY to synced(Oldest, 2_000))

        syncer.sync(USER)

        assertEquals(mapOf(SERIAL to synced(Oldest, 3_000), DAILY to synced(Oldest, 2_000)), local.episodeOrders)
    }

    @Test
    fun aRemoteFailure_isTheRemoteError_andChangesNothingHere() = runTest {
        storeLocally(SERIAL, Oldest, at = 1_000)
        network.error = RemoteError.Unavailable

        assertEquals(SyncError.Remote(RemoteError.Unavailable).left(), syncer.sync(USER))
        assertEquals(mapOf(SERIAL to synced(Oldest, 1_000)), local.episodeOrders)
    }

    @Test
    fun aLocalFailure_isTheStorageError() = runTest {
        local.readError = StorageError.Corrupted

        assertEquals(SyncError.Local(StorageError.Corrupted).left(), syncer.sync(USER))
    }

    @Test
    fun aChoiceMadeHere_isALocalChange() = runTest {
        val changes = mutableListOf<Unit>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) { syncer.observeLocalChanges().toList(changes) }

        storeLocally(SERIAL, Oldest, at = 1_000)

        assertEquals(1, changes.size)
        watching.cancel()
    }

    // ADR-037: deleting the account deletes every choice in the cloud, cleared ones too.
    @Test
    fun deleteRemote_deletesTheUsersChoices() = runTest {
        network.remote = mapOf(SERIAL to synced(Oldest, 1_000), DAILY to synced(Feed, 1_000))

        assertEquals(Unit.right(), syncer.deleteRemote(USER))

        assertEquals(listOf(USER), network.deletedFor)
        assertEquals(emptyMap<DirectoryId, SyncedValue<EpisodeOrderChoice>>(), network.remote)
    }

    @Test
    fun deleteRemote_failing_isTheRemoteError() = runTest {
        network.deleteError = RemoteError.Unavailable

        assertEquals(RemoteError.Unavailable.left(), syncer.deleteRemote(USER))
    }

    // Sync never deletes a choice, even one this phone doesn't have.
    @Test
    fun sync_neverDeletes() = runTest {
        network.remote = mapOf(SERIAL to synced(Oldest, 1_000))

        syncer.sync(USER)

        assertEquals(emptyList<String>(), network.deletedFor)
        assertEquals(mapOf(SERIAL to synced(Oldest, 1_000)), network.remote)
    }

    private companion object {
        const val USER = "alice"
        val DAILY = DirectoryId.itunes(1)
        val SERIAL = DirectoryId.itunes(2)
    }
}
