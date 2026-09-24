package com.eduardoflores.rolabox.core.auth

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.core.domain.error.FetchError
import com.eduardoflores.rolabox.core.domain.repository.AuthRepository
import com.eduardoflores.rolabox.core.model.AuthUser
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

internal class FirebaseAuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth) : AuthRepository {
    // The auth state is read from Firebase's local cache, so observing it can't fail.
    override fun observeCurrentUser(): Flow<Either<FetchError, AuthUser?>> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser?.toAuthUser().right()) }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()
}

private fun FirebaseUser.toAuthUser() = AuthUser(id = uid, displayName = displayName)
