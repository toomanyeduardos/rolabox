package com.eduardoflores.rolabox.core.auth.api

/**
 * Signing in or signing up failed (ADR-007). After the UI step (ADR-009), errors of the UI step
 * itself, such as the user closing the account picker, are handled by the feature.
 */
sealed interface AuthError {
    /** The backend couldn't be reached. Retrying may work. */
    data object Network : AuthError

    /** The backend rejected the credential, for example because it expired. Starting over may work. */
    data object InvalidCredential : AuthError

    /** The account exists but has been disabled. Retrying won't help. */
    data object AccountDisabled : AuthError

    /** Sign-up: an account with this email already exists, whichever way it was created. */
    data object EmailAlreadyInUse : AuthError

    /** Sign-up: the email address isn't valid. */
    data object InvalidEmail : AuthError

    /** Sign-up: the backend's password policy rejected the password. */
    data object WeakPassword : AuthError

    /** The backend is rate limiting this device or account. Retrying now won't help, later may. */
    data object TooManyRequests : AuthError

    /**
     * The backend answered with an auth error we don't have a case for. It's a named Firebase
     * response, not a bug: anything that isn't one still crashes (ADR-007).
     */
    data object Unknown : AuthError
}
