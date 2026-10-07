package com.eduardoflores.rolabox.auth.ui.impl

import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SignOutViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val authRepository = FakeAuthRepository().apply {
        setAuthState(AuthState.SignedIn(AuthUser(id = "1", displayName = "Eduardo", photoUrl = null)))
    }
    private val viewModel = SignOutViewModelImpl(authRepository)
    private val state get() = viewModel.uiState.value

    @Test
    fun startsWithNothingHappening() {
        assertEquals(SignOutUiState(), state)
    }

    @Test
    fun signOut_signsTheUserOutAndFinishes() = runTest {
        viewModel.onSignOutClick()

        assertEquals(SignOutUiState(isFinished = true), state)
        assertEquals(AuthState.SignedOut, authRepository.observeAuthState().first())
    }

    @Test
    fun signOut_failure_staysSignedInAndShowsAnError() {
        authRepository.signOutError = AuthError.Unknown

        viewModel.onSignOutClick()

        assertEquals(SignOutUiState(hasError = true), state)
    }

    @Test
    fun signOut_afterAFailure_canBeTriedAgain() {
        authRepository.signOutError = AuthError.Unknown
        viewModel.onSignOutClick()
        authRepository.signOutError = null

        viewModel.onSignOutClick()

        assertEquals(SignOutUiState(isFinished = true), state)
    }
}
