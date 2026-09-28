package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTriggersTest {
    private val authStates = MutableStateFlow<AuthState>(AuthState.SignedOut)
    private val localChanges = MutableSharedFlow<Unit>()

    @Test
    fun signedOut_requestsNothing() = runTest {
        val requests = collectRequests()

        runCurrent()

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
        val requests = collectRequests()
        runCurrent()

        repeat(3) {
            localChanges.emit(Unit)
            advanceTimeBy(DEBOUNCE / 2)
        }
        assertEquals(0, requests.size)

        advanceTimeBy(DEBOUNCE)
        assertEquals(1, requests.size)
    }

    @Test
    fun noLocalChanges_requestNothing() = runTest {
        val requests = collectRequests(localChanges = emptyFlow())

        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(0, requests.size)
    }

    private fun TestScope.collectRequests(localChanges: Flow<Unit> = this@SyncTriggersTest.localChanges): List<Unit> {
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            syncRequests(authStates, localChanges, DEBOUNCE).toList(requests)
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
