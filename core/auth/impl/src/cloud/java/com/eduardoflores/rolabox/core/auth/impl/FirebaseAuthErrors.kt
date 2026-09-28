package com.eduardoflores.rolabox.core.auth.impl

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

private const val USER_DISABLED = "ERROR_USER_DISABLED"

/**
 * Names the Firebase Auth exceptions that become errors (ADR-007). Returns null for anything else,
 * which the caller rethrows.
 */
internal fun Throwable.asAuthError(): AuthError? = when (this) {
    is FirebaseAuthInvalidUserException ->
        if (errorCode == USER_DISABLED) AuthError.AccountDisabled else AuthError.InvalidCredential

    is FirebaseAuthInvalidCredentialsException -> AuthError.InvalidCredential

    is FirebaseNetworkException -> AuthError.Network

    else -> null
}
