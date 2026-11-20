package com.eduardoflores.rolabox.common.sync.api

import arrow.core.Either

/**
 * Keeps the user's synced data in step with the cloud (ADR-011). Local storage stays the source of
 * truth: sync runs in the background, and nothing waits on it. While the user is signed out, or has
 * chosen offline mode, it does nothing, and makes no Firebase requests (ADR-008).
 *
 * It also deletes the synced data in the cloud when an account is deleted (ADR-037). Only
 * `DeleteAccountUseCase` calls [deleteRemoteData] (ADR-037, rule 2).
 */
interface SyncRepository {
    /**
     * Starts syncing on sign-in, after local changes, and when the app comes to the foreground. Call
     * it once, from `Application.onCreate`, on the main thread.
     */
    fun start()

    /** Syncs as soon as there is a network connection. Returns without waiting for it. */
    fun requestSync()

    /**
     * Pauses sync for the user [userId], waits for a sync that is running to finish, and deletes
     * every kind of synced data of theirs in the cloud. Each kind is tried even when another fails,
     * and the first error is returned. Sync stays paused for that user, whether or not this
     * succeeds, until [resume], so the phone doesn't write the data back while the account still
     * exists.
     */
    suspend fun deleteRemoteData(userId: String): Either<RemoteError, Unit>

    /** Lifts the pause of [deleteRemoteData]. Resuming when not paused does nothing. */
    fun resume()
}
