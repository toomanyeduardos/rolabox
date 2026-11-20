package com.eduardoflores.rolabox.common.userdata.network.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences

/**
 * The user's synced preferences in the cloud (ADR-011). Only the `Syncer` of the preferences calls it,
 * so sync, and deleting an account (ADR-037), stay the only paths to the network, and offline mode
 * makes no request (ADR-022, rule 6).
 */
interface PreferencesNetworkDataSource {
    /**
     * Reads the remote preferences of the user [userId], and writes back what [merge] returns
     * wherever it differs, as one transaction: a write from another device between the read and the
     * write makes it start over. Returns the merged preferences. [merge] may run more than once.
     */
    suspend fun merge(
        userId: String,
        merge: (remote: SyncedPreferences) -> SyncedPreferences,
    ): Either<RemoteError, SyncedPreferences>

    /**
     * Deletes the remote preferences of the user [userId], because their account is being deleted (ADR-037). Deleting
     * preferences that aren't there succeeds. It waits for the server, and fails with [RemoteError.Unavailable] when
     * the server doesn't answer in time, rather than leaving the delete queued as done.
     */
    suspend fun delete(userId: String): Either<RemoteError, Unit>
}
