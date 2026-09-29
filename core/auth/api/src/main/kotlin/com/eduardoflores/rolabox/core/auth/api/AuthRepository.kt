package com.eduardoflores.rolabox.core.auth.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /**
     * Emits the current auth state, and again when it changes. The state is read from a local
     * cache, so observing it can't fail (ADR-007: code that can't fail doesn't use Either).
     */
    fun observeAuthState(): Flow<AuthState>

    /** Exchanges a credential from the UI's sign-in step for a session. */
    suspend fun signIn(credential: SignInCredential): Either<AuthError, AuthUser>

    /**
     * Creates an account with an email and password, saves [name] as its display name, and signs in.
     * If the account is created but saving the name fails, the sign-up still succeeds with a user
     * that has no name: the account exists, so failing would strand the user (the email is taken).
     */
    suspend fun signUp(name: String, email: String, password: String): Either<AuthError, AuthUser>

    /** Ends the session. Signing out while signed out succeeds. */
    suspend fun signOut(): Either<AuthError, Unit>
}
