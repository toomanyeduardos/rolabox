package com.eduardoflores.rolabox.common.userdata.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.network.api.PreferencesNetworkDataSource
import javax.inject.Inject
import javax.inject.Singleton

/** Remote preferences for many users, in memory, merged the way the Firestore transaction merges them. */
@Singleton
class FakePreferencesNetworkDataSource @Inject constructor() : PreferencesNetworkDataSource {
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
