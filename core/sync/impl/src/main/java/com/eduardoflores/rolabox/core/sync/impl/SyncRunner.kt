package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Runs every [Syncer] for the signed-in user. The worker calls it. It checks [syncUser] again
 * itself, so work enqueued before the user signed out or chose offline mode makes no Firebase
 * requests (ADR-008 rule 6).
 */
internal class SyncRunner @Inject constructor(
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
) {
    suspend fun sync(): SyncOutcome {
        val userId = syncUser(authRepository.observeAuthState(), userDataRepository.observeOfflineModeChosen())
            .first()
            ?: return SyncOutcome.Done
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
    /** Everything synced, or sync isn't allowed: signed out, or offline mode chosen. */
    Done,

    /** Something failed in a way that trying again later can fix. */
    Retry,

    /** Something failed in a way that trying again won't fix. */
    Failed,
}
