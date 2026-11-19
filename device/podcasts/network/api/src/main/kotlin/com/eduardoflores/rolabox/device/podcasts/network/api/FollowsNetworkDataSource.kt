package com.eduardoflores.rolabox.device.podcasts.network.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.device.podcasts.api.SyncedFollows

/**
 * The user's follows in the cloud, one document per show (ADR-031, Syncing follows). Only the `Syncer` of the follows
 * calls it, so sync stays the only path to Firebase, and no-account mode makes no request (ADR-022, rule 6).
 */
interface FollowsNetworkDataSource {
    /**
     * Reads the remote follows of the user [userId], and writes back each follow [merge] returns that differs from the
     * remote one, as one transaction: a write from another phone between the read and the write makes it start over.
     * Returns the merged follows. [merge] may run more than once.
     */
    suspend fun merge(
        userId: String,
        merge: (remote: SyncedFollows) -> SyncedFollows,
    ): Either<RemoteError, SyncedFollows>
}
