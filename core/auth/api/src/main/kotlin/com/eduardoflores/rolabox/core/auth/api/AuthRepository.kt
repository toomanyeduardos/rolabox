package com.eduardoflores.rolabox.core.auth.api

import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /**
     * Emits the signed-in user, or null when signed out. The state is read from a local cache, so
     * observing it can't fail (ADR-007: code that can't fail doesn't use Either).
     */
    fun observeCurrentUser(): Flow<AuthUser?>
}
