package com.eduardoflores.rolabox.core.auth.api

/**
 * Signing in failed after the UI step (ADR-007, ADR-009). Errors of the UI step itself, such as the
 * user closing the account picker, are handled by the feature.
 */
sealed interface AuthError {
    /** The backend couldn't be reached. Retrying may work. */
    data object Network : AuthError

    /** The backend rejected the credential, for example because it expired. Starting over may work. */
    data object InvalidCredential : AuthError

    /** The account exists but has been disabled. Retrying won't help. */
    data object AccountDisabled : AuthError
}
