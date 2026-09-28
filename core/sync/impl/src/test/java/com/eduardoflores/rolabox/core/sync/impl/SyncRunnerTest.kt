package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.storage.api.StorageError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRunnerTest {
    private val authRepository = FakeAuthRepository()

    @Test
    fun signedOut_syncsNothing() = runTest {
        val syncer = RecordingSyncer()

        assertEquals(SyncOutcome.Done, SyncRunner(authRepository, setOf(syncer)).sync())
        assertEquals(emptyList<String>(), syncer.userIds)
    }

    @Test
    fun signedIn_syncsEverySyncerForTheUser() = runTest {
        signIn()
        val first = RecordingSyncer()
        val second = RecordingSyncer()

        assertEquals(SyncOutcome.Done, SyncRunner(authRepository, setOf(first, second)).sync())
        assertEquals(listOf(USER), first.userIds)
        assertEquals(listOf(USER), second.userIds)
    }

    @Test
    fun oneSyncerFailing_stillRunsTheOthers() = runTest {
        signIn()
        val failing = RecordingSyncer(SyncError.Remote(RemoteError.Rejected).left())
        val other = RecordingSyncer()

        assertEquals(SyncOutcome.Failed, SyncRunner(authRepository, setOf(failing, other)).sync())
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

    private suspend fun runWith(error: SyncError) =
        SyncRunner(authRepository, setOf(RecordingSyncer(error.left()))).sync()

    private fun signIn() {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = USER, displayName = null, photoUrl = null)))
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
