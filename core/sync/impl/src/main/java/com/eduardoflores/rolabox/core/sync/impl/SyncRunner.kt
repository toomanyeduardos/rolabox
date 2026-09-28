package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Runs every [Syncer] for the signed-in user. The cloud flavor's worker calls it. */
internal class SyncRunner @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
) {
    suspend fun sync(): SyncOutcome {
        val user = (authRepository.observeAuthState().first() as? AuthState.SignedIn)?.user
            ?: return SyncOutcome.Done
        // Each kind of data syncs on its own, so one failing doesn't hold the others back.
        val errors = syncers.mapNotNull { it.sync(user.id).leftOrNull() }
        return when {
            errors.isEmpty() -> SyncOutcome.Done
            errors.any { it.isRetryable } -> SyncOutcome.Retry
            else -> SyncOutcome.Failed
        }
    }
}

internal enum class SyncOutcome {
    /** Everything synced, or there was nothing to sync because the user is signed out. */
    Done,

    /** Something failed in a way that trying again later can fix. */
    Retry,

    /** Something failed in a way that trying again won't fix. */
    Failed,
}
