package com.eduardoflores.rolabox.common.sync.impl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import com.eduardoflores.rolabox.common.sync.api.SyncUserProvider
import com.eduardoflores.rolabox.common.sync.api.Syncer
import com.eduardoflores.rolabox.common.util.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * Runs sync as unique WorkManager work that waits for a network connection (ADR-011). Changes made
 * offline are already in local storage with their timestamps, so the pending work is the whole
 * queue: when it runs, the merge pushes whatever is newer locally. The work survives the process
 * being killed, and WorkManager retries it with backoff.
 *
 * Nothing is requested while the [SyncUserProvider] has nobody to sync for, which is while signed out
 * or in offline mode. So in offline mode no work is ever enqueued (ADR-008 rule 6). Nor is anything
 * requested for a user whose account's data is being deleted (ADR-037).
 */
internal class WorkManagerSyncRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val syncUserProvider: SyncUserProvider,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val pause: SyncPause,
    private val deletion: SyncedDataDeletion,
) : SyncRepository {
    // Null until start(), while there is nobody to sync for, and while their data is being deleted.
    private val allowedUser = MutableStateFlow<String?>(null)
    private val foreground = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun start() {
        // The process lifecycle must be observed from the main thread, which start() is called on.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    foreground.tryEmit(Unit)
                }
            },
        )
        allowedSyncUser(syncUserProvider.observeSyncUser(), pause.pausedFor)
            .onEach { allowedUser.value = it }
            .launchIn(applicationScope)
        syncRequests(allowedUser, syncers.map { it.observeLocalChanges() }.merge(), foreground)
            .onEach { enqueueSync() }
            .launchIn(applicationScope)
    }

    override fun requestSync() {
        if (allowedUser.value != null) enqueueSync()
    }

    // Pending work is cancelled too, though the runner would skip it: there's no point waiting for a
    // network to do nothing.
    override suspend fun deleteRemoteData(userId: String): Either<RemoteError, Unit> {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        return deletion.deleteRemoteData(userId)
    }

    override fun resume() = deletion.resume()

    // A newer request replaces a pending or running one. Syncing is idempotent, so cancelling one
    // halfway loses nothing, and the new one reads the latest local changes.
    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val SYNC_WORK_NAME = "sync"
    }
}
