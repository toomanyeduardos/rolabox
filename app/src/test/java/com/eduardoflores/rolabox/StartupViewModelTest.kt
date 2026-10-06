package com.eduardoflores.rolabox

import com.eduardoflores.rolabox.auth.data.api.StartDestination
import com.eduardoflores.rolabox.auth.data.testing.FakeResolveStartDestinationUseCase
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

// The use case is a fake (ADR-021): how the destination is decided is tested in :auth:data:impl.
class StartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val resolveStartDestination = FakeResolveStartDestinationUseCase()

    private fun viewModel() = DefaultStartupViewModel(resolveStartDestination)

    @Test
    fun accessGranted_isReadyForIt() {
        resolveStartDestination.destination = StartDestination.AccessGranted

        assertEquals(StartupUiState.Ready(StartDestination.AccessGranted), viewModel().uiState.value)
    }

    @Test
    fun signIn_isReadyForIt() {
        resolveStartDestination.destination = StartDestination.SignIn

        assertEquals(StartupUiState.Ready(StartDestination.SignIn), viewModel().uiState.value)
    }
}
