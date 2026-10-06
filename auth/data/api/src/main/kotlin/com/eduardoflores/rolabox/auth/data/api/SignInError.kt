package com.eduardoflores.rolabox.auth.data.api

import com.eduardoflores.rolabox.common.storage.api.StorageError

/**
 * Why signing in, or creating an account, failed. A sign-in leaves offline mode first (ADR-008,
 * rule 7), so it can fail in auth or in storage, and this wraps each one's error (ADR-007).
 */
sealed interface SignInError {
    /** The sign-in itself failed. */
    data class Auth(val cause: AuthError) : SignInError

    /** Offline mode couldn't be turned off, so no Firebase request was made (ADR-008, rule 6). */
    data class Storage(val cause: StorageError) : SignInError
}
