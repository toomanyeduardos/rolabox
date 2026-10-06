package com.eduardoflores.rolabox.auth.data.impl

import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ADR-008 rule 6: the part of "offline mode makes no Firebase requests" that auth answers for sync.
class AuthSyncUserProviderTest {
    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()
    private val provider = AuthSyncUserProvider(authRepository, userDataRepository)

    @Test
    fun signedOut_isNobody() = runTest {
        assertNull(provider.observeSyncUser().first())
    }

    @Test
    fun signedIn_isTheUser() = runTest {
        signIn(ALICE)

        assertEquals(ALICE, provider.observeSyncUser().first())
    }

    @Test
    fun offlineModeChosen_isNobodyEvenWhenSignedIn() = runTest {
        signIn(ALICE)
        userDataRepository.setOfflineModeChosen(true)

        assertNull(provider.observeSyncUser().first())
    }

    @Test
    fun offlineModeChoiceCantBeRead_isNobody() = runTest {
        signIn(ALICE)
        userDataRepository.setReadError(StorageError.Corrupted)

        assertNull(provider.observeSyncUser().first())
    }

    @Test
    fun choosingAndLeavingOfflineMode_stopsAndStartsAgain() = runTest {
        signIn(ALICE)
        val users = collectUsers()

        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.setOfflineModeChosen(false)

        assertEquals(listOf(ALICE, null, ALICE), users)
    }

    @Test
    fun switchingUsers_emitsEachOne() = runTest {
        val users = collectUsers()

        signIn(ALICE)
        authRepository.signOut()
        signIn(BOB)

        assertEquals(listOf(null, ALICE, null, BOB), users)
    }

    @Test
    fun signingUp_emitsTheNewUser() = runTest {
        val users = collectUsers()

        val user = authRepository.signUp("Alex", "alex@mail.com", "password12").getOrNull()

        assertEquals(listOf(null, user?.id), users)
    }

    @Test
    fun failedSignUp_emitsNothingNew() = runTest {
        authRepository.signUpError = AuthError.EmailAlreadyInUse
        val users = collectUsers()

        authRepository.signUp("Alex", "alex@mail.com", "password12")

        assertEquals(listOf<String?>(null), users)
    }

    private fun TestScope.collectUsers(): List<String?> {
        val users = mutableListOf<String?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { provider.observeSyncUser().toList(users) }
        return users
    }

    private fun signIn(id: String) {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = id, displayName = null, photoUrl = null)))
    }

    private companion object {
        const val ALICE = "alice"
        const val BOB = "bob"
    }
}
