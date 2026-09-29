package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import com.eduardoflores.rolabox.core.userdata.api.SyncedPreferences

/** The user's synced preferences in the cloud, backed by Firestore. */
internal interface RemotePreferences {
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
