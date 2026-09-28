package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.userdata.api.SyncedPreferences

/** Remote preferences for many users, in memory, merged the way the Firestore transaction merges them. */
internal class FakeRemotePreferences : RemotePreferences {
    val byUser = mutableMapOf<String, SyncedPreferences>()

    /** When set, [merge] fails with this error and changes nothing. */
    var error: RemoteError? = null

    /** How many merges wrote something. */
    var writes = 0
        private set

    override suspend fun merge(
        userId: String,
        merge: (remote: SyncedPreferences) -> SyncedPreferences,
    ): Either<RemoteError, SyncedPreferences> {
        error?.let { return it.left() }
        val remote = byUser[userId] ?: SyncedPreferences()
        val merged = merge(remote)
        if (merged != remote) {
            byUser[userId] = merged
            writes++
        }
        return merged.right()
    }
}
