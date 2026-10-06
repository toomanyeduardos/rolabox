package com.eduardoflores.rolabox.common.sync.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.testing.FakeSyncUserProvider
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Lazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SyncRunnerTest {
    private val syncUserProvider = FakeSyncUserProvider()
    private val userDataRepository = FakeUserDataRepository()

    // Who there is to sync for, and so whether offline mode is on, is the provider's answer
    // (ADR-020, rule 18). What sync owns of ADR-008 rule 6 is obeying it, which these tests cover.
    @Test
    fun nobodyToSyncFor_syncsNothing() = runTest {
        val syncer = RecordingSyncer()

        assertEquals(SyncOutcome.Done, runner(syncer).sync())
        assertEquals(emptyList<String>(), syncer.userIds)
    }

    @Test
    fun signedIn_syncsEverySyncerForTheUser() = runTest {
        signIn()
        val first = RecordingSyncer()
        val second = RecordingSyncer()

        assertEquals(SyncOutcome.Done, runner(first, second).sync())
        assertEquals(listOf(USER), first.userIds)
        assertEquals(listOf(USER), second.userIds)
    }

    @Test
    fun oneSyncerFailing_stillRunsTheOthers() = runTest {
        signIn()
        val failing = RecordingSyncer(SyncError.Remote(RemoteError.Rejected).left())
        val other = RecordingSyncer()

        assertEquals(SyncOutcome.Failed, runner(failing, other).sync())
        assertEquals(listOf(USER), other.userIds)
    }

    @Test
    fun remoteUnavailable_isRetried() = runTest {
        signIn()

        assertEquals(SyncOutcome.Retry, runWith(SyncError.Remote(RemoteError.Unavailable)))
    }

    @Test
    fun storageUnavailable_isRetried() = runTest {
        signIn()

        assertEquals(SyncOutcome.Retry, runWith(SyncError.Local(StorageError.Unavailable)))
    }

    @Test
    fun rejectedOrCorrupted_isNotRetried() = runTest {
        signIn()

        assertEquals(SyncOutcome.Failed, runWith(SyncError.Remote(RemoteError.Rejected)))
        assertEquals(SyncOutcome.Failed, runWith(SyncError.Local(StorageError.Corrupted)))
    }

    @Test
    fun noLongerAnyoneToSyncFor_syncsNothing() = runTest {
        signIn()
        syncUserProvider.syncUser.value = null
        val syncer = RecordingSyncer()

        assertEquals(SyncOutcome.Done, runner(syncer).sync())
        assertEquals(emptyList<String>(), syncer.userIds)
    }

    // ADR-008 rules 6 and 8: the real preferences syncer, with the Firestore remote, never gets as
    // far as creating Firestore while there is nobody to sync for, which is what offline mode is to sync.
    @Test
    fun nobodyToSyncFor_neverCreatesFirestore() = runTest {
        var created = false
        val firestore = Lazy<FirebaseFirestore> {
            created = true
            error("Firestore was created with nobody to sync for")
        }
        val syncer = PreferencesSyncer(userDataRepository, FirestoreRemotePreferences(firestore))

        runner(syncer).sync()

        assertFalse(created)
    }

    private fun runner(vararg syncers: Syncer) = SyncRunner(syncUserProvider, syncers.toSet())

    private suspend fun runWith(error: SyncError) = runner(RecordingSyncer(error.left())).sync()

    private fun signIn() {
        syncUserProvider.syncUser.value = USER
    }

    private class RecordingSyncer(private val result: Either<SyncError, Unit> = Unit.right()) : Syncer {
        val userIds = mutableListOf<String>()

        override fun observeLocalChanges(): Flow<Unit> = emptyFlow()

        override suspend fun sync(userId: String): Either<SyncError, Unit> {
            userIds += userId
            return result
        }
    }

    private companion object {
        const val USER = "alice"
    }
}
