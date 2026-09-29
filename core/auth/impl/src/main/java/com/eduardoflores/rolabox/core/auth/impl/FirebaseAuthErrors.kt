package com.eduardoflores.rolabox.core.auth.impl

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.google.firebase.FirebaseNetworkException
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

    else -> null
}
