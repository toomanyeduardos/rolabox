package com.eduardoflores.rolabox.core.domain

import arrow.core.Either
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject

/**
 * Creates an account with an email and password and signs in, leaving offline mode for it like
 * every sign-in (ADR-008, rule 7).
 *
 * This combines two areas (auth and user data) and holds a business rule, so it's a use case
 * (ADR-001).
 */
class SignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) {
    suspend operator fun invoke(name: String, email: String, password: String): Either<SignInError, AuthUser> =
        userDataRepository.leavingOfflineModeFor { authRepository.signUp(name, email, password) }
}
