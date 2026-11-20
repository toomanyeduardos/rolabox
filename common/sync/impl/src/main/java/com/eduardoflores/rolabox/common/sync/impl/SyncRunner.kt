package com.eduardoflores.rolabox.common.sync.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.SyncUserProvider
import com.eduardoflores.rolabox.common.sync.api.Syncer
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

/**
 * Runs every [Syncer] for the user to sync for. The worker calls it. It asks the [SyncUserProvider]
 * again itself, so work enqueued before the user signed out or chose offline mode makes no Firebase
 * requests (ADR-008 rule 6). Nothing runs for a user whose account's data is being deleted, and a
 * sync that was running when the delete started finishes before it (ADR-037).
 */
internal class SyncRunner @Inject constructor(
    private val syncUserProvider: SyncUserProvider,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
    private val failureLog: SyncFailureLog,
    private val pause: SyncPause,
) {
    suspend fun sync(): SyncOutcome = pause.running.withLock { syncUnlocked() }

    private suspend fun syncUnlocked(): SyncOutcome {
        val userId = syncUserProvider.observeSyncUser().first()?.takeUnless(pause::isPaused) ?: return SyncOutcome.Done
        // Each kind of data syncs on its own, so one failing doesn't hold the others back.
        val errors = syncers.mapNotNull { it.sync(userId).leftOrNull() }
        val outcome = when {
            errors.isEmpty() -> SyncOutcome.Done
            errors.any { it.isRetryable } -> SyncOutcome.Retry
            else -> SyncOutcome.Failed
        }
        // Nobody sees a sync fail, so every error is logged (ADR-029, rule 1).
        errors.forEach { failureLog.failed(it, outcome) }
        return outcome
    }
}

internal enum class SyncOutcome {
    /**
     * Everything synced, or there is nobody to sync for: signed out, offline mode chosen, or the
     * account's data being deleted.
     */
    Done,

    /** Something failed in a way that trying again later can fix. */
    Retry,

    /** Something failed in a way that trying again won't fix. */
    Failed,
}

/** Whether WorkManager should try the sync again later, instead of giving up until the next trigger. */
internal val SyncError.isRetryable: Boolean
    get() = when (this) {
        is SyncError.Local -> cause == StorageError.Unavailable
        is SyncError.Remote -> cause == RemoteError.Unavailable
    }
