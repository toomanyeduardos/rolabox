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
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
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
 * Nothing is requested while signed out or in offline mode ([syncUser]), so in offline mode no work
 * is ever enqueued (ADR-008 rule 6).
 */
internal class WorkManagerSyncRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val authRepository: AuthRepository,
    private val userDataRepository: UserDataRepository,
    private val syncers: Set<@JvmSuppressWildcards Syncer>,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : SyncRepository {
    // Null until start(), and while sync isn't allowed.
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
        syncUser(authRepository.observeAuthState(), userDataRepository.observeOfflineModeChosen())
            .onEach { allowedUser.value = it }
            .launchIn(applicationScope)
        syncRequests(allowedUser, syncers.map { it.observeLocalChanges() }.merge(), foreground)
            .onEach { enqueueSync() }
            .launchIn(applicationScope)
    }

    override fun requestSync() {
        if (allowedUser.value != null) enqueueSync()
    }

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
