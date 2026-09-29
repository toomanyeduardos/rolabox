package com.eduardoflores.rolabox.core.domain

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** The screen the app opens on once it has started. */
enum class StartDestination {
    Home,
    SignIn,
}

/**
 * Decides where the app opens: Home for a signed-in user, or for one who chose to use the app
 * without an account, and Sign In for everyone else. Every build has accounts, so this is decided
 * only at runtime (ADR-008).
 *
 * This combines two areas (auth and user data), so it's a use case (ADR-001).
 */
class ResolveStartDestinationUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) {
    suspend operator fun invoke(): StartDestination = when {
        authRepository.observeAuthState().first() is AuthState.SignedIn -> StartDestination.Home
        offlineModeChosen() -> StartDestination.Home
        else -> StartDestination.SignIn
    }

    // If the choice can't be read, the user is asked to sign in again, which is safe: they can
    // choose offline mode again from there.
    private suspend fun offlineModeChosen(): Boolean =
        userDataRepository.observeOfflineModeChosen().first().getOrNull() ?: false
}
