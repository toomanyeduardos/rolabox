package com.eduardoflores.rolabox.auth.data.api

import arrow.core.Either

/**
 * Creates an account with an email and password and signs in, leaving offline mode for it like
 * every sign-in (ADR-008, rule 7).
 *
 * This combines two areas (auth and user data) and holds a business rule, so it's a use case
 * (ADR-001).
 */
interface SignUpUseCase {
    suspend operator fun invoke(name: String, email: String, password: String): Either<SignInError, AuthUser>
}
