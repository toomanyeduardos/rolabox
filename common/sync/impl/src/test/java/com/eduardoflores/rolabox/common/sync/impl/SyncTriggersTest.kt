package com.eduardoflores.rolabox.common.sync.impl

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
import org.junit.Test

class SyncTriggersTest {
    // What a SyncUserProvider emits: the user to sync for, or null for nobody. Auth decides it, from
    // the auth state and the offline-mode choice, and tests that in :auth:data:impl (ADR-020, rule 18).
    private val syncUser = MutableStateFlow<String?>(null)
    private val localChanges = MutableSharedFlow<Unit>()
    private val foreground = MutableSharedFlow<Unit>()

    @Test
    fun nobodyToSyncFor_requestsNothing() = runTest {
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

        syncUser.value = ALICE
        runCurrent()

        assertEquals(1, requests.size)
    }

    @Test
    fun alreadySignedInAtStart_requests() = runTest {
        syncUser.value = ALICE
        val requests = collectRequests()

        runCurrent()

        assertEquals(1, requests.size)
    }

    @Test
    fun switchingUsers_requestsAgain() = runTest {
        val requests = collectRequests()

        syncUser.value = ALICE
        runCurrent()
        syncUser.value = null
        runCurrent()
        syncUser.value = BOB
        runCurrent()

        assertEquals(2, requests.size)
    }

    @Test
    fun localChanges_requestOnceTheySettle() = runTest {
        syncUser.value = ALICE
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
        syncUser.value = ALICE
        val requests = collectRequests(localChanges = emptyFlow())

        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(1, requests.size)
    }

    @Test
    fun foreground_requests() = runTest {
        syncUser.value = ALICE
        val requests = collectRequests()
        runCurrent()

        foreground.emit(Unit)
        runCurrent()

        assertEquals(2, requests.size)
    }

    // What signing out and choosing offline mode are to sync (ADR-008 rule 6).
    @Test
    fun noLongerAnyoneToSyncFor_stopsRequests() = runTest {
        syncUser.value = ALICE
        val requests = collectRequests()
        runCurrent()

        syncUser.value = null
        runCurrent()
        localChanges.emit(Unit)
        foreground.emit(Unit)
        advanceTimeBy(DEBOUNCE * 2)

        assertEquals(1, requests.size)
    }

    @Test
    fun someoneToSyncForAgain_requests() = runTest {
        val requests = collectRequests()
        runCurrent()

        syncUser.value = ALICE
        runCurrent()

        assertEquals(1, requests.size)
    }

    // ADR-037: while a user's data is deleted, sync has nobody to request work for, and nothing is
    // observed. It comes back when the pause is lifted.
    @Test
    fun pausedForTheUser_requestsNothing_untilResumed() = runTest {
        val pausedFor = MutableStateFlow<String?>(ALICE)
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            syncRequests(allowedSyncUser(syncUser, pausedFor), localChanges, foreground, DEBOUNCE).toList(requests)
        }
        syncUser.value = ALICE
        runCurrent()

        localChanges.emit(Unit)
        foreground.emit(Unit)
        advanceTimeBy(DEBOUNCE * 2)
        assertEquals(0, requests.size)

        pausedFor.value = null
        runCurrent()
        assertEquals(1, requests.size)
    }

    @Test
    fun allowedSyncUser_pausedForAnotherUser_isTheUser() = runTest {
        assertEquals(ALICE, allowedSyncUser(MutableStateFlow(ALICE), MutableStateFlow(BOB)).first())
    }

    @Test
    fun allowedSyncUser_pausedForTheUser_isNobody() = runTest {
        assertEquals(null, allowedSyncUser(MutableStateFlow(ALICE), MutableStateFlow(ALICE)).first())
    }

    private fun TestScope.collectRequests(localChanges: Flow<Unit> = this@SyncTriggersTest.localChanges): List<Unit> {
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(StandardTestDispatcher(testScheduler)) {
            syncRequests(syncUser, localChanges, foreground, DEBOUNCE)
                .toList(requests)
        }
        return requests
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
        val DEBOUNCE = 100.milliseconds
    }
}
