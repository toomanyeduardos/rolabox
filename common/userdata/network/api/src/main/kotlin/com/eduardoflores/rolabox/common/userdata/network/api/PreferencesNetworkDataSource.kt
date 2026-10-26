package com.eduardoflores.rolabox.common.userdata.network.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences

/**
 * The user's synced preferences in the cloud (ADR-011). Only the `Syncer` of the preferences calls it,
 * so sync stays the only path to the network, and offline mode makes no request (ADR-022, rule 6).
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
}
