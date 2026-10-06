package com.eduardoflores.rolabox.auth.data.impl

import arrow.core.Either
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignInUseCase
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import javax.inject.Inject

internal class DefaultSignInUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) : SignInUseCase {
    override suspend operator fun invoke(credential: SignInCredential): Either<SignInError, AuthUser> =
        userDataRepository.leavingOfflineModeFor { authRepository.signIn(credential) }

    override suspend operator fun invoke(email: String, password: String): Either<SignInError, AuthUser> =
        userDataRepository.leavingOfflineModeFor { authRepository.signInWithEmail(email, password) }
}
