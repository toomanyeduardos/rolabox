package com.eduardoflores.rolabox.auth.data.api

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
     * Signs in with an email and password. With email enumeration protection on, a wrong password
     * and an unknown email are the same [AuthError.InvalidCredential].
     */
    suspend fun signInWithEmail(email: String, password: String): Either<AuthError, AuthUser>

    /**
     * Creates an account with an email and password, saves [name] as its display name, and signs in.
     * If the account is created but saving the name fails, the sign-up still succeeds with a user
     * that has no name: the account exists, so failing would strand the user (the email is taken).
     */
    suspend fun signUp(name: String, email: String, password: String): Either<AuthError, AuthUser>

    /**
     * Sends a link to [email] for setting a new password. It succeeds whether or not an account
     * exists for [email], so that the answer can't be used to find out who has one. Fails with
     * [AuthError.InvalidEmail] for a malformed address, [AuthError.Network] and [AuthError.TooManyRequests].
     */
    suspend fun sendPasswordResetEmail(email: String): Either<AuthError, Unit>

    /** Ends the session. Signing out while signed out succeeds. */
    suspend fun signOut(): Either<AuthError, Unit>
}
