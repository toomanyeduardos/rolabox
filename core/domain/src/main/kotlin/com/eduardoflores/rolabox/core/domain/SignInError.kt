package com.eduardoflores.rolabox.core.domain

import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.storage.api.StorageError

/**
 * Why signing in, or creating an account, failed. It spans the auth and user data areas, so it's
 * owned by the use cases that combine them, and wraps each area's error (ADR-007).
 */
sealed interface SignInError {
    /** The sign-in itself failed. */
    data class Auth(val cause: AuthError) : SignInError

    /** Offline mode couldn't be turned off, so no Firebase request was made (ADR-008, rule 6). */
    data class Storage(val cause: StorageError) : SignInError
}
