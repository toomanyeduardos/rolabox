package com.eduardoflores.rolabox.auth.data.api

import arrow.core.Either

/**
 * Signs in, leaving offline mode for it (ADR-008, rule 7): offline mode is turned off before the
 * Firebase request, and back on if the sign-in fails.
 *
 * This combines two areas (auth and user data), holds a business rule, and is used by more than
 * one ViewModel, so it's a use case (ADR-001).
 */
interface SignInUseCase {
    /** Exchanges a credential from a provider's UI step (ADR-009) for a session. */
    suspend operator fun invoke(credential: SignInCredential): Either<SignInError, AuthUser>

    suspend operator fun invoke(email: String, password: String): Either<SignInError, AuthUser>
}
