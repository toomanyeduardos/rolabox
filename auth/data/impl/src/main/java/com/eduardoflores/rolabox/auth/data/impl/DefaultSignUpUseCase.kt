package com.eduardoflores.rolabox.auth.data.impl

import arrow.core.Either
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInError
import com.eduardoflores.rolabox.auth.data.api.SignUpUseCase
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import javax.inject.Inject

internal class DefaultSignUpUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
) : SignUpUseCase {
    override suspend operator fun invoke(name: String, email: String, password: String): Either<SignInError, AuthUser> =
        userDataRepository.leavingOfflineModeFor { authRepository.signUp(name, email, password) }
}
