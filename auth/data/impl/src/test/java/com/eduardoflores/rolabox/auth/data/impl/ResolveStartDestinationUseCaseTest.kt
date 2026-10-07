package com.eduardoflores.rolabox.auth.data.impl

import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.StartDestination
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveStartDestinationUseCaseTest {
    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()

    private fun resolve() = DefaultResolveStartDestinationUseCase(authRepository, userDataRepository)

    @Test
    fun signedIn_isAccessGranted() = runTest {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "id", displayName = null, photoUrl = null)))

        assertEquals(StartDestination.AccessGranted, resolve()())
    }

    @Test
    fun signedOutAfterChoosingOfflineMode_isAccessGranted() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        assertEquals(StartDestination.AccessGranted, resolve()())
    }

    @Test
    fun signedOutWithoutChoosingOfflineMode_isSignIn() = runTest {
        assertEquals(StartDestination.SignIn, resolve()())
    }

    @Test
    fun offlineModeChoiceCantBeRead_isSignIn() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        userDataRepository.setReadError(StorageError.Corrupted)

        assertEquals(StartDestination.SignIn, resolve()())
    }

    @Test
    fun signedIn_ignoresAnUnreadableOfflineModeChoice() = runTest {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "id", displayName = null, photoUrl = null)))
        userDataRepository.setReadError(StorageError.Corrupted)

        assertEquals(StartDestination.AccessGranted, resolve()())
    }

    // Ticket: signing out returns the user to Sign in, not straight back to offline mode.
    @Test
    fun signedOutAfterSigningInFromOfflineMode_isSignIn() = runTest {
        userDataRepository.setOfflineModeChosen(true)
        DefaultSignInUseCase(authRepository, userDataRepository)("alex@mail.com", "password12")

        authRepository.signOut()

        assertEquals(StartDestination.SignIn, resolve()())
    }
}
