package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.SyncedEpisodeOrders
import com.eduardoflores.rolabox.device.podcasts.network.api.EpisodeOrdersNetworkDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The choices of episode order in the cloud, in memory: [remote] is what Firestore holds. A merge writes back each
 * choice that differs from it, as the real one does, and records the directory ids it wrote in [written]. [error]
 * makes the merge fail without writing.
 */
@Singleton
class FakeEpisodeOrdersNetworkDataSource @Inject constructor() : EpisodeOrdersNetworkDataSource {
    var remote: SyncedEpisodeOrders = emptyMap()
    var error: RemoteError? = null
    val written = mutableListOf<DirectoryId>()

    override suspend fun merge(
        userId: String,
        merge: (remote: SyncedEpisodeOrders) -> SyncedEpisodeOrders,
    ): Either<RemoteError, SyncedEpisodeOrders> {
        error?.let { return it.left() }
        val merged = merge(remote)
        merged.forEach { (directoryId, choice) -> if (remote[directoryId] != choice) written += directoryId }
        remote = remote + merged
        return merged.right()
    }

    /** When set, [delete] fails with this error and deletes nothing. */
    var deleteError: RemoteError? = null

    /** The users whose choices [delete] deleted, in order. */
    val deletedFor = mutableListOf<String>()

    override suspend fun delete(userId: String): Either<RemoteError, Unit> {
        deleteError?.let { return it.left() }
        deletedFor += userId
        remote = emptyMap()
        return Unit.right()
    }
}
