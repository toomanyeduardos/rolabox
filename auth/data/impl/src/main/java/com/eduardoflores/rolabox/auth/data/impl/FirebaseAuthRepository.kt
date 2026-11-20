package com.eduardoflores.rolabox.auth.data.impl

import android.util.Log
import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.api.SignInMethod
import com.eduardoflores.rolabox.common.util.catchNamed
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

private const val TAG = "FirebaseAuthRepository"

internal class FirebaseAuthRepository @Inject constructor(private val firebaseAuth: FirebaseAuth) : AuthRepository {
    // The auth state is read from Firebase's local cache, so observing it can't fail.
    override fun observeAuthState(): Flow<AuthState> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            Log.d(TAG, "auth state: uid=${auth.currentUser?.uid}, displayName='${auth.currentUser?.displayName}'")
            trySend(auth.currentUser.toAuthState())
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(credential: SignInCredential): Either<AuthError, AuthUser> =
        catchNamed(Throwable::asAuthError) {
            val result = firebaseAuth.signInWithCredential(credential.toFirebaseCredential()).await()
            checkNotNull(result.user) { "Firebase signed in without a user" }.toAuthUser()
        }

    override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, AuthUser> =
        catchNamed(Throwable::asAuthError) {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            checkNotNull(result.user) { "Firebase signed in without a user" }.toAuthUser()
        }

    override suspend fun signUp(name: String, email: String, password: String): Either<AuthError, AuthUser> =
        catchNamed(Throwable::asAuthError) {
            val user = checkNotNull(firebaseAuth.createUserWithEmailAndPassword(email, password).await().user) {
                "Firebase created an account without a user"
            }
            saveDisplayName(user, name)
            Log.d(TAG, "sign-up: displayName='${user.displayName}' (typed='$name')")
            user.toAuthUser()
        }

    // The account exists by now, so a failure here (say, the connection dropping) doesn't fail the
    // sign-up: the user would be told the email is taken when they retry. They're signed in without a
    // name instead. Anything Firebase throws that isn't named as an error still crashes.
    private suspend fun saveDisplayName(user: FirebaseUser, name: String) {
        val request = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        val result = catchNamed(Throwable::asAuthError) { user.updateProfile(request).await() }
        Log.d(TAG, "sign-up: updateProfile result=$result")
    }

    // With email enumeration protection on, Firebase succeeds for an unknown email. Without it, it fails
    // with an invalid-user error, which is swallowed here so that both answer the same (ADR-013).
    override suspend fun sendPasswordResetEmail(email: String): Either<AuthError, Unit> =
        catchNamed(Throwable::asAuthError) {
            try {
                firebaseAuth.sendPasswordResetEmail(email).await()
            } catch (ignored: FirebaseAuthInvalidUserException) {
                Log.d(TAG, "password reset: no usable account for the email")
            }
            Unit
        }

    // Firebase signs out synchronously from its local state and doesn't fail. Clearing Credential
    // Manager's saved state joins it when the sign-in UI exists (ADR-009), and that can fail.
    override suspend fun signOut(): Either<AuthError, Unit> = firebaseAuth.signOut().right()

    // Another account's credential fails with Firebase's user-mismatch error, an invalid-user exception, which is
    // InvalidCredential: the user signed in with something that isn't this account.
    override suspend fun reauthenticate(credential: SignInCredential): Either<AuthError, Unit> =
        catchNamed(Throwable::asAuthError) {
            signedInUser().reauthenticate(credential.toFirebaseCredential()).await()
            Unit
        }

    override suspend fun reauthenticateWithPassword(password: String): Either<AuthError, Unit> =
        catchNamed(Throwable::asAuthError) {
            val user = signedInUser()
            val email = checkNotNull(user.email) { "An account with a password has an email" }
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            Unit
        }

    // Firebase signs out by itself once the account is deleted.
    override suspend fun deleteUser(): Either<AuthError, Unit> = catchNamed(Throwable::asAuthError) {
        firebaseAuth.currentUser?.delete()?.await()
        Unit
    }

    // Re-authenticating is only offered while signed in, so being signed out here is a bug.
    private fun signedInUser(): FirebaseUser = checkNotNull(firebaseAuth.currentUser) { "Nobody is signed in" }
}

private fun SignInCredential.toFirebaseCredential(): AuthCredential = when (this) {
    is SignInCredential.GoogleIdToken -> GoogleAuthProvider.getCredential(token, null)
}

private fun FirebaseUser?.toAuthState(): AuthState =
    this?.let { AuthState.SignedIn(it.toAuthUser()) } ?: AuthState.SignedOut

private fun FirebaseUser.toAuthUser() = AuthUser(
    id = uid,
    displayName = displayName,
    photoUrl = photoUrl?.toString(),
    signInMethods = providerData.mapNotNullTo(mutableSetOf()) { it.providerId.toSignInMethod() },
)

// The user's own entry, "firebase", and providers Rolabox doesn't offer, aren't ways to re-authenticate.
private fun String.toSignInMethod(): SignInMethod? = when (this) {
    EmailAuthProvider.PROVIDER_ID -> SignInMethod.PASSWORD
    GoogleAuthProvider.PROVIDER_ID -> SignInMethod.GOOGLE
    else -> null
}
