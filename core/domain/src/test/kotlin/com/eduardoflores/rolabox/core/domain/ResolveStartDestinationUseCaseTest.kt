package com.eduardoflores.rolabox.core.domain

import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ResolveStartDestinationUseCaseTest {
    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()

    private fun resolve() = ResolveStartDestinationUseCase(authRepository, userDataRepository)

    @Test
    fun signedIn_isHome() = runTest {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "id", displayName = null, photoUrl = null)))

        assertEquals(StartDestination.Home, resolve()())
    }

    @Test
    fun signedOutAfterChoosingOfflineMode_isHome() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        assertEquals(StartDestination.Home, resolve()())
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

        assertEquals(StartDestination.Home, resolve()())
    }
}
