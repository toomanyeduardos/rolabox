package com.eduardoflores.rolabox.auth.data.impl

import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

private const val USER_DISABLED = "ERROR_USER_DISABLED"
private const val INVALID_EMAIL = "ERROR_INVALID_EMAIL"

/**
 * Names the Firebase Auth exceptions that become errors (ADR-007). Returns null for anything else,
 * which the caller rethrows.
 */
internal fun Throwable.asAuthError(): AuthError? = when (this) {
    is FirebaseAuthInvalidUserException ->
        if (errorCode == USER_DISABLED) AuthError.AccountDisabled else AuthError.InvalidCredential

    // A weak-password exception is a kind of invalid-credentials exception, so it comes first.
    is FirebaseAuthWeakPasswordException -> AuthError.WeakPassword

    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == INVALID_EMAIL) AuthError.InvalidEmail else AuthError.InvalidCredential

    is FirebaseAuthUserCollisionException -> AuthError.EmailAlreadyInUse

    is FirebaseNetworkException -> AuthError.Network

    is FirebaseTooManyRequestsException -> AuthError.TooManyRequests

    // Last, since the ones above are kinds of FirebaseAuthException. It's Firebase's own answer with a
    // code we haven't named, so it's an error and not a bug.
    is FirebaseAuthException -> AuthError.Unknown

    else -> null
}
