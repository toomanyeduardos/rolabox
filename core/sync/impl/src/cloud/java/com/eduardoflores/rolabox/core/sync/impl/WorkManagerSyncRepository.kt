package com.eduardoflores.rolabox.core.sync.impl

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.common.ApplicationScope
import com.eduardoflores.rolabox.core.sync.api.SyncRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

/**
 * Runs sync as unique WorkManager work that waits for a network connection (ADR-011). Changes made
 * offline are already in local storage with their timestamps, so the pending work is the whole
 * queue: when it runs, the merge pushes whatever is newer locally. The work survives the process
 * being killed, and WorkManager retries it with backoff.
 */
internal class WorkManagerSyncRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : SyncRepository {
    override fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = requestSync()
            },
        )
        syncRequests(authRepository.observeAuthState(), syncers.map { it.observeLocalChanges() }.merge())
            .onEach { requestSync() }
            .launchIn(applicationScope)
    }

    // A newer request replaces a pending or running one. Syncing is idempotent, so cancelling one
    // halfway loses nothing, and the new one reads the latest local changes.
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private companion object {
        const val SYNC_WORK_NAME = "sync"
    }
}
