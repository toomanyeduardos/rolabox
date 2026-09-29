package com.eduardoflores.rolabox.core.domain

import arrow.core.Either
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject

/**
 * Signs in, leaving offline mode for it (ADR-008, rule 7): offline mode is turned off before the
 * Firebase request, and back on if the sign-in fails.
 *
 * This combines two areas (auth and user data), holds a business rule, and is used by more than
 * one ViewModel, so it's a use case (ADR-001).
 */
class SignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) {
    /** Exchanges a credential from a provider's UI step (ADR-009) for a session. */
    suspend operator fun invoke(credential: SignInCredential): Either<SignInError, AuthUser> =
        userDataRepository.leavingOfflineModeFor { authRepository.signIn(credential) }

    suspend operator fun invoke(email: String, password: String): Either<SignInError, AuthUser> =
        userDataRepository.leavingOfflineModeFor { authRepository.signInWithEmail(email, password) }
}
