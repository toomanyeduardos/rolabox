package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.testing.FakeAuthRepository
import com.eduardoflores.rolabox.core.domain.ResolveStartDestination
import com.eduardoflores.rolabox.core.domain.StartDestination
import com.eduardoflores.rolabox.core.testing.MainDispatcherRule
import com.eduardoflores.rolabox.core.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository()
    private val userDataRepository = FakeUserDataRepository()

    private fun viewModel(accountsAvailable: Boolean = true) = StartupViewModel(
        ResolveStartDestination(authRepository, userDataRepository, accountsAvailable),
    )

    @Test
    fun signedIn_isReadyForHome() = runTest {
        authRepository.setAuthState(AuthState.SignedIn(AuthUser(id = "id", displayName = null, photoUrl = null)))

        assertEquals(StartupUiState.Ready(StartDestination.Home), viewModel().uiState.value)
    }

    @Test
    fun offlineModeChosen_isReadyForHome() = runTest {
        userDataRepository.setOfflineModeChosen(true)

        assertEquals(StartupUiState.Ready(StartDestination.Home), viewModel().uiState.value)
    }

    @Test
    fun signedOutWithoutOfflineMode_isReadyForSignIn() = runTest {
        assertEquals(StartupUiState.Ready(StartDestination.SignIn), viewModel().uiState.value)
    }

    @Test
    fun withoutAccounts_isReadyForHome() = runTest {
        assertEquals(
            StartupUiState.Ready(StartDestination.Home),
            viewModel(accountsAvailable = false).uiState.value,
        )
    }
}
