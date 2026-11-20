package com.eduardoflores.rolabox.device.podcasts.impl.follow

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.Syncer
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.SyncedFollows
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.FollowsNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Syncs the follows show by show, with last-write-wins on when the user last set each (ADR-031, Syncing follows). It
 * is the only class that uses both the local and the network side of the follows (ADR-022, rule 2). Follows made
 * without an account were stored with their time, so the first sync after signing in merges them like any change.
 * Sync never deletes a follow: only deleting the account does, every one of them (ADR-037).
 */
internal class FollowsSyncer @Inject constructor(
    private val local: PodcastsLocalDataSource,
    private val network: FollowsNetworkDataSource,
    private val lastWriteWins: LastWriteWins,
    private val followCheck: FollowCheckScheduler,
) : Syncer {
    // The first value is what's stored when collection starts, not a change. A read error isn't a change either: it
    // ends this flow, and sync() reads the same database and reports it there (ADR-007, rule 7).
    override fun observeLocalChanges(): Flow<Unit> = local.observeSyncedFollows()
        .drop(1)
        .filter { it.isRight() }
        .map { }

    override suspend fun sync(userId: String): Either<SyncError, Unit> = either {
        val localFollows = local.getSyncedFollows().mapLeft(PodcastError::toSyncError).bind()
        val merged = network.merge(userId) { remote -> localFollows.mergedWith(remote) }
            .mapLeft(SyncError::Remote)
            .bind()
        if (merged != localFollows) {
            // Applying resolves each follow again, so a Follow set while this sync ran isn't overwritten.
            val turnedOn = local.applySyncedFollows(merged).mapLeft(PodcastError::toSyncError).bind()
            // Each show followed on another phone has its feed read, as turning Follow On here does. Its own unique
            // work, which a check already running can't drop (ADR-031, Follow checks).
            turnedOn.forEach(followCheck::checkShow)
        }
    }

    override suspend fun deleteRemote(userId: String): Either<RemoteError, Unit> = network.delete(userId)

    // Each show is resolved on its own, so following one show on one phone and another on a second keeps both.
    private fun SyncedFollows.mergedWith(remote: SyncedFollows): SyncedFollows =
        (keys + remote.keys).associateWith { directoryId ->
            checkNotNull(lastWriteWins.resolve(this[directoryId], remote[directoryId])) { "$directoryId is on a side" }
        }
}

// The follows' reads and writes fail only with the database's error.
private fun PodcastError.toSyncError(): SyncError =
    SyncError.Local((this as? PodcastError.Storage)?.cause ?: StorageError.Unavailable)
