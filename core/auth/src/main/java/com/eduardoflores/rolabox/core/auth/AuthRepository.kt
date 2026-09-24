package com.eduardoflores.rolabox.core.auth

import com.eduardoflores.rolabox.core.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Emits the signed-in user, or null when signed out. */
    val currentUser: Flow<AuthUser?>
}
