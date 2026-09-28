package com.eduardoflores.rolabox.core.auth.impl

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// The offline flavor has no accounts (ADR-008), so the user is always signed out.
internal class SignedOutAuthRepository @Inject constructor() : AuthRepository {
    override fun observeCurrentUser(): Flow<AuthUser?> = flowOf(null)
}
