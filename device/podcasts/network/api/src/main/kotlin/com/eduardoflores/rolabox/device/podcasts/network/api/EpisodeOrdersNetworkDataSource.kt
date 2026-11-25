package com.eduardoflores.rolabox.device.podcasts.network.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.device.podcasts.api.SyncedEpisodeOrders

/**
 * The user's choices of episode order in the cloud, one document per show (ADR-043, Syncing the choice), apart from
 * the follows. Only the `Syncer` of the choices calls it, so sync, and deleting an account (ADR-037), stay the only
 * paths to Firebase, and no-account mode makes no request (ADR-022, rule 6).
 */
interface EpisodeOrdersNetworkDataSource {
    /**
     * Reads the remote choices of the user [userId], and writes back each choice [merge] returns that differs from the
     * remote one, as one transaction: a write from another phone between the read and the write makes it start over.
     * Returns the merged choices. [merge] may run more than once.
     */
    suspend fun merge(
        userId: String,
        merge: (remote: SyncedEpisodeOrders) -> SyncedEpisodeOrders,
    ): Either<RemoteError, SyncedEpisodeOrders>

    /**
     * Deletes every choice of the user [userId], because their account is being deleted (ADR-037). Sync never deletes
     * one. They are listed from the server, never from the cache, and each write waits for the server: offline, or
     * when the server doesn't answer in time, it fails with [RemoteError.Unavailable] rather than leaving the delete
     * queued as done. Deleting choices that aren't there succeeds.
     */
    suspend fun delete(userId: String): Either<RemoteError, Unit>
}
