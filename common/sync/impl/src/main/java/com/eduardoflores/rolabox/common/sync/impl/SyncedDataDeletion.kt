package com.eduardoflores.rolabox.common.sync.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.Syncer
import javax.inject.Inject
import kotlinx.coroutines.sync.withLock

/**
 * Deletes the synced data in the cloud of an account being deleted, through each [Syncer] (ADR-037). It runs in the
 * foreground, as the user waits on it, not as WorkManager work (ADR-037, which supersedes ADR-022's
 * rule 5 for this).
 */
internal class SyncedDataDeletion @Inject constructor(
    private val pause: SyncPause,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
) {
    // The pause is set before waiting, so no sync starts after the one running, if any, ends.
    suspend fun deleteRemoteData(userId: String): Either<RemoteError, Unit> {
        pause.pausedFor.value = userId
        return pause.running.withLock {
            // Each kind is deleted even when another fails, so a retry has less left to do.
            syncers.mapNotNull { it.deleteRemote(userId).leftOrNull() }.firstOrNull()?.left() ?: Unit.right()
        }
    }

    fun resume() {
        pause.pausedFor.value = null
    }
}
