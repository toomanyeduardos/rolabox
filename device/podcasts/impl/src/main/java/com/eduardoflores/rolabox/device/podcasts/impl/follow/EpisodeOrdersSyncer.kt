package com.eduardoflores.rolabox.device.podcasts.impl.follow

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.Syncer
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.SyncedEpisodeOrders
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastsLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.EpisodeOrdersNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Syncs the user's choices of episode order show by show, with last-write-wins on when the user last made each
 * (ADR-043, Syncing the choice), apart from the follows: a phone that doesn't know the choices writes the follows
 * whole, and never touches these. It is the only class that uses both the local and the network side of the choices
 * (ADR-022, rule 2). Choices made without an account were stored with their time, so the first sync after signing in
 * merges them like any change. Sync never deletes a choice: a cleared one is `Feed`. Only deleting the account does,
 * every one of them (ADR-037).
 */
internal class EpisodeOrdersSyncer @Inject constructor(
    private val local: PodcastsLocalDataSource,
    private val network: EpisodeOrdersNetworkDataSource,
    private val lastWriteWins: LastWriteWins,
) : Syncer {
    // The first value is what's stored when collection starts, not a change. A read error isn't a change either: it
    // ends this flow, and sync() reads the same database and reports it there (ADR-007, rule 7).
    override fun observeLocalChanges(): Flow<Unit> = local.observeSyncedEpisodeOrders()
        .drop(1)
        .filter { it.isRight() }
        .map { }

    override suspend fun sync(userId: String): Either<SyncError, Unit> = either {
        val localOrders = local.getSyncedEpisodeOrders().mapLeft(PodcastError::toSyncError).bind()
        val merged = network.merge(userId) { remote -> localOrders.mergedWith(remote) }
            .mapLeft(SyncError::Remote)
            .bind()
        // Applying resolves each choice again, so a choice made while this sync ran isn't overwritten.
        if (merged != localOrders) local.applySyncedEpisodeOrders(merged).mapLeft(PodcastError::toSyncError).bind()
    }

    override suspend fun deleteRemote(userId: String): Either<RemoteError, Unit> = network.delete(userId)

    // Each show is resolved on its own, so a choice made for one show on one phone and another on a second keeps both.
    private fun SyncedEpisodeOrders.mergedWith(remote: SyncedEpisodeOrders): SyncedEpisodeOrders =
        (keys + remote.keys).associateWith { directoryId ->
            checkNotNull(lastWriteWins.resolve(this[directoryId], remote[directoryId])) { "$directoryId is on a side" }
        }
}

// The follows' and the choices' reads and writes fail only with the database's error.
internal fun PodcastError.toSyncError(): SyncError =
    SyncError.Local((this as? PodcastError.Storage)?.cause ?: StorageError.Unavailable)
