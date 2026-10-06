package com.eduardoflores.rolabox.common.sync.impl

import com.eduardoflores.rolabox.common.sync.api.SyncUserProvider
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Runs every [Syncer] for the user to sync for. The worker calls it. It asks the [SyncUserProvider]
 * again itself, so work enqueued before the user signed out or chose offline mode makes no Firebase
 * requests (ADR-008 rule 6).
 */
internal class SyncRunner @Inject constructor(
    private val syncUserProvider: SyncUserProvider,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
) {
    suspend fun sync(): SyncOutcome {
        val userId = syncUserProvider.observeSyncUser().first() ?: return SyncOutcome.Done
        // Each kind of data syncs on its own, so one failing doesn't hold the others back.
        val errors = syncers.mapNotNull { it.sync(userId).leftOrNull() }
        return when {
            errors.isEmpty() -> SyncOutcome.Done
            errors.any { it.isRetryable } -> SyncOutcome.Retry
            else -> SyncOutcome.Failed
        }
    }
}

internal enum class SyncOutcome {
    /** Everything synced, or there is nobody to sync for: signed out, or offline mode chosen. */
    Done,

    /** Something failed in a way that trying again later can fix. */
    Retry,

    /** Something failed in a way that trying again won't fix. */
    Failed,
}
