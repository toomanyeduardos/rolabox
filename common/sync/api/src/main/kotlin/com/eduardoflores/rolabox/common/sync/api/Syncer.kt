package com.eduardoflores.rolabox.common.sync.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * Syncs one kind of data, such as preferences, and later favorites or playlists (ADR-011). The part
 * that owns the data implements it and adds it to the Hilt set that `:common:sync:impl` declares, and
 * a sync runs all of them (ADR-022). Only `:common:sync:impl` calls it: the sync runner, and
 * deleting an account (ADR-037).
 *
 * Every kind of data that syncs is data that deleting an account deletes in the cloud, so the same
 * interface says how (ADR-037, rule 1). On the phone, Android erases the app's data afterwards.
 */
interface Syncer {
    /** Emits whenever the local data changes, so sync can follow up. */
    fun observeLocalChanges(): Flow<Unit>

    /** Merges the local and remote data of the user [userId], and stores the result on both sides. */
    suspend fun sync(userId: String): Either<SyncError, Unit>

    /**
     * Deletes all of this kind of data in the cloud for the user [userId], because their account is
     * being deleted. Deleting what is already gone succeeds, so a retry finishes a delete that failed
     * halfway. Offline, it fails with [RemoteError.Unavailable] rather than leaving the delete queued
     * as done (ADR-037, rule 4).
     */
    suspend fun deleteRemote(userId: String): Either<RemoteError, Unit>
}
