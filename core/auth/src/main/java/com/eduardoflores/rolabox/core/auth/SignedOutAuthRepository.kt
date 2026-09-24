package com.eduardoflores.rolabox.core.auth

import com.eduardoflores.rolabox.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

// Placeholder until the Firebase implementation is added; always reports signed out.
internal class SignedOutAuthRepository @Inject constructor() : AuthRepository {
    override val currentUser: Flow<AuthUser?> = flowOf(null)
}
