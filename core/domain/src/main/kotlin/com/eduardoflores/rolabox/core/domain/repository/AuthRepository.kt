package com.eduardoflores.rolabox.core.domain.repository

import arrow.core.Either
import com.eduardoflores.rolabox.core.domain.error.FetchError
import com.eduardoflores.rolabox.core.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the signed-in user, or null when signed out. */
    fun observeCurrentUser(): Flow<Either<FetchError, AuthUser?>>
}
