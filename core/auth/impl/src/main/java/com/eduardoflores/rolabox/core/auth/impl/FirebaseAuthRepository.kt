package com.eduardoflores.rolabox.core.auth.impl

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import com.eduardoflores.rolabox.core.common.util.catchNamed
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

internal class FirebaseAuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth) : AuthRepository {
    // The auth state is read from Firebase's local cache, so observing it can't fail.
    override fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser.toAuthState()) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(credential: SignInCredential): Either<AuthError, AuthUser> =
        catchNamed(Throwable::asAuthError) {
            val result = firebaseAuth.signInWithCredential(credential.toFirebaseCredential()).await()
            checkNotNull(result.user) { "Firebase signed in without a user" }.toAuthUser()
        }

    // Firebase signs out synchronously from its local state and doesn't fail. Clearing Credential
    // Manager's saved state joins it when the sign-in UI exists (ADR-009), and that can fail.
    override suspend fun signOut(): Either<AuthError, Unit> = firebaseAuth.signOut().right()
}

private fun SignInCredential.toFirebaseCredential(): AuthCredential = when (this) {
    is SignInCredential.GoogleIdToken -> GoogleAuthProvider.getCredential(token, null)
}

private fun FirebaseUser?.toAuthState(): AuthState =
    this?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut

private fun FirebaseUser.toAuthUser() = AuthUser(id = uid, displayName = displayName, photoUrl = photoUrl?.toString())
