package com.eduardoflores.rolabox.core.auth.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// The offline flavor has no accounts (ADR-008), so the user is always signed out. Nothing in this
// flavor offers sign-in, but the call is typed rather than a crash.
internal class SignedOutAuthRepository @Inject constructor() : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.SignedOut)

    override suspend fun signIn(credential: SignInCredential): Either<AuthError, AuthUser> =
        AuthError.Unavailable.left()

    override suspend fun signOut(): Either<AuthError, Unit> = Unit.right()
}
