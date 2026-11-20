package com.eduardoflores.rolabox.common.sync.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.Syncer
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ADR-037: deleting an account deletes each kind of synced data, with sync paused for that user.
class SyncedDataDeletionTest {
    private val pause = SyncPause()

    @Test
    fun deleteRemoteData_deletesEveryKindForTheUser() = runTest {
        val first = RecordingSyncer()
        val second = RecordingSyncer()

        assertEquals(Unit.right(), deletion(first, second).deleteRemoteData(USER))

        assertEquals(listOf(USER), first.deletedFor)
        assertEquals(listOf(USER), second.deletedFor)
    }

    @Test
    fun deleteRemoteData_pausesSyncForTheUser_andKeepsItPausedUntilResumed() = runTest {
        val deletion = deletion(RecordingSyncer())

        deletion.deleteRemoteData(USER)
        assertTrue(pause.isPaused(USER))

        deletion.resume()
        assertNull(pause.pausedFor.value)
    }

    @Test
    fun deleteRemoteData_failing_staysPaused_soThePhoneDoesntWriteTheDataBack() = runTest {
        deletion(RecordingSyncer(deleteResult = RemoteError.Unavailable.left())).deleteRemoteData(USER)

        assertTrue(pause.isPaused(USER))
    }

    @Test
    fun deleteRemoteData_oneKindFailing_stillDeletesTheOthers_andReturnsTheError() = runTest {
        val failing = RecordingSyncer(deleteResult = RemoteError.Unavailable.left())
        val other = RecordingSyncer()

        assertEquals(RemoteError.Unavailable.left(), deletion(failing, other).deleteRemoteData(USER))
        assertEquals(listOf(USER), other.deletedFor)
    }

    @Test
    fun deleteRemoteData_waitsForASyncThatIsRunning() = runTest {
        val syncer = RecordingSyncer()
        pause.running.lock()

        val delete = async { deletion(syncer).deleteRemoteData(USER) }
        runCurrent()
        // The pause is set straight away, so no sync starts after the running one.
        assertTrue(pause.isPaused(USER))
        assertEquals(emptyList<String>(), syncer.deletedFor)

        pause.running.unlock()
        delete.await()
        assertEquals(listOf(USER), syncer.deletedFor)
    }

    private fun deletion(vararg syncers: Syncer) = SyncedDataDeletion(pause, syncers.toSet())

    private class RecordingSyncer(private val deleteResult: Either<RemoteError, Unit> = Unit.right()) : Syncer {
        val deletedFor = mutableListOf<String>()

        override fun observeLocalChanges(): Flow<Unit> = emptyFlow()

        override suspend fun sync(userId: String): Either<SyncError, Unit> = Unit.right()

        override suspend fun deleteRemote(userId: String): Either<RemoteError, Unit> {
            deletedFor += userId
            return deleteResult
        }
    }

    private companion object {
        const val USER = "alice"
    }
}
