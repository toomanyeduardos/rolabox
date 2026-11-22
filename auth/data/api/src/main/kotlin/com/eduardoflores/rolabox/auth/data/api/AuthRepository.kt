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

    /**
     * Ends the session. The phone's saved sign-in state is cleared first, so the next Google sign-in starts from the
     * account picker. If that fails, it fails with [AuthError.CredentialStateNotCleared] and the user is still signed
     * in. Signing out while signed out succeeds.
     */
    suspend fun signOut(): Either<AuthError, Unit>

    /**
     * Re-authenticates the signed-in user with a credential from the UI's sign-in step (ADR-009), so the backend
     * accepts deleting the account (ADR-037). A credential of another account fails with
     * [AuthError.InvalidCredential].
     */
    suspend fun reauthenticate(credential: SignInCredential): Either<AuthError, Unit>

    /** Re-authenticates the signed-in user with their password, as [reauthenticate] does. */
    suspend fun reauthenticateWithPassword(password: String): Either<AuthError, Unit>

    /**
     * Deletes the signed-in user's account, which signs them out. Fails with [AuthError.RequiresRecentLogin] when they
     * haven't signed in recently enough. Deleting while signed out succeeds: there is no account to delete. Only
     * `DeleteAccountUseCase` calls it, after the account's synced data is deleted (ADR-037, rule 2).
     */
    suspend fun deleteUser(): Either<AuthError, Unit>
}
