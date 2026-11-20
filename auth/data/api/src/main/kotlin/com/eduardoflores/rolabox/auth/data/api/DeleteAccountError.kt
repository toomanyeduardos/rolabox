package com.eduardoflores.rolabox.auth.data.api

import com.eduardoflores.rolabox.common.sync.api.RemoteError

/**
 * Why deleting the account failed (ADR-037). It re-authenticates and deletes the account in auth, and deletes the
 * synced data in the cloud, so it wraps each one's error (ADR-007, rule 3). The account is kept whenever this fails.
 */
sealed interface DeleteAccountError {
    /** Re-authenticating, or deleting the account itself, failed. */
    data class Auth(val cause: AuthError) : DeleteAccountError

    /** Deleting the synced data in the cloud failed, so the account wasn't deleted. */
    data class Remote(val cause: RemoteError) : DeleteAccountError
}
