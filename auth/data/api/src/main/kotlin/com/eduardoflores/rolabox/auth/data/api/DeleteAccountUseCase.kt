package com.eduardoflores.rolabox.auth.data.api

import arrow.core.Either

/**
 * Deletes the signed-in user's account (ADR-037): re-authenticates them, deletes their synced data in the cloud with
 * sync paused, deletes the account, then has Android erase everything the app keeps on the phone, which closes the
 * app: the next launch is a fresh install. When it fails, the account is kept, and sync stays paused until [cancel]
 * or a retry that succeeds, so the phone doesn't write the data back into the account. Retrying runs every step
 * again, and each can be repeated.
 *
 * This combines two areas (auth and sync), holds a business rule, and its outcome is auth's, so it's a use case
 * (ADR-020, rule 19).
 */
interface DeleteAccountUseCase {
    /** Deletes the account after re-authenticating with a credential from a provider's UI step (ADR-009). */
    suspend operator fun invoke(credential: SignInCredential): Either<DeleteAccountError, Unit>

    /** Deletes the account after re-authenticating with the account's password. */
    suspend operator fun invoke(password: String): Either<DeleteAccountError, Unit>

    /** The user left without deleting the account: sync resumes. Cancelling when nothing is paused does nothing. */
    fun cancel()
}
