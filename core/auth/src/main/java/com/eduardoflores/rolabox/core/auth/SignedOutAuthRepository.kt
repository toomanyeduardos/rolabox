package com.eduardoflores.rolabox.core.auth

import com.eduardoflores.rolabox.core.model.AuthUser
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Placeholder until the Firebase implementation is added; always reports signed out.
internal class SignedOutAuthRepository @Inject constructor() : AuthRepository {
    override val currentUser: Flow<AuthUser?> = flowOf(null)
}
