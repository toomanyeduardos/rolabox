package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.storage.api.StorageError
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncTriggersTest {
    private val authStates = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val offlineModeChosen = MutableStateFlow<Either<StorageError, Boolean>>(false.right())
    private val localChanges = MutableSharedFlow<Unit>()
    private val foreground = MutableSharedFlow<Unit>()

    @Test
    fun syncUser_signedOut_isNull() = runTest {
        assertNull(syncUser(authStates, offlineModeChosen).first())
    }

    @Test
    fun syncUser_signedIn_isTheUser() = runTest {
        authStates.value = signedIn(ALICE)

        assertEquals(ALICE, syncUser(authStates, offlineModeChosen).first())
    }

    @Test
    fun syncUser_offlineModeChosen_isNullEvenWhenSignedIn() = runTest {
        authStates.value = signedIn(ALICE)
        offlineModeChosen.value = true.right()

        assertNull(syncUser(authStates, offlineModeChosen).first())
    }

    @Test
    fun syncUser_offlineModeChoiceCantBeRead_isNull() = runTest {
        authStates.value = signedIn(ALICE)
        offlineModeChosen.value = StorageError.Corrupted.left()

        assertNull(syncUser(authStates, offlineModeChosen).first())
    }

    @Test
    fun signedOut_requestsNothing() = runTest {
        val requests = collectRequests()
        runCurrent()

        localChanges.emit(Unit)
        foreground.emit(Unit)
        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(0, requests.size)
    }

    @Test
    fun signingIn_requestsOnce() = runTest {
        val requests = collectRequests()

        authStates.value = signedIn(ALICE)
        runCurrent()

        assertEquals(1, requests.size)
    }

    @Test
    fun alreadySignedInAtStart_requests() = runTest {
        authStates.value = signedIn(ALICE)
        val requests = collectRequests()

        runCurrent()

        assertEquals(1, requests.size)
    }

    @Test
    fun switchingUsers_requestsAgain() = runTest {
        val requests = collectRequests()

        authStates.value = signedIn(ALICE)
        runCurrent()
        authStates.value = AuthState.SignedOut
        runCurrent()
        authStates.value = signedIn(BOB)
        runCurrent()

        assertEquals(2, requests.size)
    }

    @Test
    fun localChanges_requestOnceTheySettle() = runTest {
        authStates.value = signedIn(ALICE)
        val requests = collectRequests()
        runCurrent()
        assertEquals(1, requests.size)

        repeat(3) {
            localChanges.emit(Unit)
            advanceTimeBy(DEBOUNCE / 2)
        }
        assertEquals(1, requests.size)

        advanceTimeBy(DEBOUNCE)
        assertEquals(2, requests.size)
    }

    @Test
    fun noLocalChanges_requestOnlyOnSignIn() = runTest {
        authStates.value = signedIn(ALICE)
        val requests = collectRequests(localChanges = emptyFlow())

        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(1, requests.size)
    }

    @Test
    fun foreground_requests() = runTest {
        authStates.value = signedIn(ALICE)
        val requests = collectRequests()
        runCurrent()

        foreground.emit(Unit)
        runCurrent()

        assertEquals(2, requests.size)
    }

    @Test
    fun offlineMode_requestsNothing() = runTest {
        authStates.value = signedIn(ALICE)
        offlineModeChosen.value = true.right()
        val requests = collectRequests()
        runCurrent()

        localChanges.emit(Unit)
        foreground.emit(Unit)
        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(0, requests.size)
    }

    @Test
    fun choosingOfflineMode_stopsRequests() = runTest {
        authStates.value = signedIn(ALICE)
        val requests = collectRequests()
        runCurrent()

        offlineModeChosen.value = true.right()
        runCurrent()
        localChanges.emit(Unit)
        foreground.emit(Unit)
        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(1, requests.size)
    }

    @Test
    fun leavingOfflineMode_requests() = runTest {
        authStates.value = signedIn(ALICE)
        offlineModeChosen.value = true.right()
        val requests = collectRequests()
        runCurrent()

        offlineModeChosen.value = false.right()
        runCurrent()

        assertEquals(1, requests.size)
    }

    private fun TestScope.collectRequests(localChanges: Flow<Unit> = this@SyncTriggersTest.localChanges): List<Unit> {
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            syncRequests(syncUser(authStates, offlineModeChosen), localChanges, foreground, DEBOUNCE)
                .toList(requests)
        }
        return requests
    }

    private fun signedIn(id: String) = AuthState.SignedIn(AuthUser(id = id, displayName = null, photoUrl = null))

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        val DEBOUNCE = 100.milliseconds
    }
}
