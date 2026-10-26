package com.eduardoflores.rolabox.common.sync.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * Syncs one kind of data, such as preferences, and later favorites or playlists (ADR-011). The part
 * that owns the data implements it and adds it to the Hilt set that `:common:sync:impl` declares, and
 * a sync runs all of them (ADR-022). Only the sync runner calls it.
 */
interface Syncer {
    /** Emits whenever the local data changes, so sync can follow up. */
    fun observeLocalChanges(): Flow<Unit>

    /** Merges the local and remote data of the user [userId], and stores the result on both sides. */
    suspend fun sync(userId: String): Either<SyncError, Unit>
}
