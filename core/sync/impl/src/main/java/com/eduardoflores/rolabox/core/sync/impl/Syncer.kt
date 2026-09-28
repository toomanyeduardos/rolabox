package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * Syncs one kind of data, such as preferences, and later favorites or playlists (ADR-011). Each
 * kind is bound into a set, and a sync runs all of them.
 */
internal interface Syncer {
    /** Emits whenever the local data changes, so sync can follow up. */
    fun observeLocalChanges(): Flow<Unit>

    /** Merges the local and remote data of the user [userId], and stores the result on both sides. */
    suspend fun sync(userId: String): Either<SyncError, Unit>
}
