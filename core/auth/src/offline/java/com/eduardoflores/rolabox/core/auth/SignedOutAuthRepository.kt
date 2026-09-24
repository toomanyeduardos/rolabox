package com.eduardoflores.rolabox.core.auth

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.core.domain.error.FetchError
import com.eduardoflores.rolabox.core.domain.repository.AuthRepository
import com.eduardoflores.rolabox.core.model.AuthUser
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// The offline flavor has no accounts (ADR-008), so the user is always signed out.
internal class SignedOutAuthRepository @Inject constructor() : AuthRepository {
    override fun observeCurrentUser(): Flow<Either<FetchError, AuthUser?>> = flowOf(null.right())
}
