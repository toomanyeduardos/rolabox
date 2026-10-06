package com.eduardoflores.rolabox.auth.data.impl

import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.ResolveStartDestinationUseCase
import com.eduardoflores.rolabox.auth.data.api.StartDestination
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal class DefaultResolveStartDestinationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) : ResolveStartDestinationUseCase {
    override suspend operator fun invoke(): StartDestination = when {
        authRepository.observeAuthState().first() is AuthState.SignedIn -> StartDestination.AccessGranted
        offlineModeChosen() -> StartDestination.AccessGranted
        else -> StartDestination.SignIn
    }

    // If the choice can't be read, the user is asked to sign in again, which is safe: they can
    // choose offline mode again from there.
    private suspend fun offlineModeChosen(): Boolean =
        userDataRepository.observeOfflineModeChosen().first().getOrNull() ?: false
}
