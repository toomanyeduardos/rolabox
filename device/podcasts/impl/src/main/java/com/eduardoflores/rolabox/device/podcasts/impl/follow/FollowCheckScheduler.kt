package com.eduardoflores.rolabox.device.podcasts.impl.follow

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eduardoflores.rolabox.common.util.ProcessStartTask
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** The unique work of the check of every followed show (ADR-031, Follow checks). */
internal const val PODCAST_FOLLOW_CHECK_WORK = "podcast-follow-check"

/** How long after a check was requested the app coming back to the front requests it again. */
internal val FOLLOW_CHECK_AGAIN_AFTER: Duration = Duration.ofHours(1)

private const val DIRECTORY_ID = "directoryId"

/** The unique work of the read of one show's feed, apart from the check's, which `KEEP` would drop it behind. */
internal fun followCheckWorkOf(directoryId: DirectoryId): String = "$PODCAST_FOLLOW_CHECK_WORK-${directoryId.value}"

/** Where the check runs: WorkManager's work in the app, a fake in tests. */
internal interface FollowCheckScheduler {
    /** Checks every followed show once there is a connection, unless a check is already waiting or running. */
    fun checkAll()

    /** Reads the feed of the show with [directoryId] once there is a connection, as turning its Follow On does. */
    fun checkShow(directoryId: DirectoryId)
}

/**
 * Each check as one unique WorkManager work with a network of any kind, kept when one is already enqueued: it waits
 * for the network, and survives the process. With no connection nothing runs, and nothing is shown (ADR-031).
 */
internal class WorkManagerFollowCheckScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : FollowCheckScheduler {
    override fun checkAll() = enqueue(PODCAST_FOLLOW_CHECK_WORK, directoryId = null)

    override fun checkShow(directoryId: DirectoryId) = enqueue(followCheckWorkOf(directoryId), directoryId)

    private fun enqueue(name: String, directoryId: DirectoryId?) {
        val request = OneTimeWorkRequestBuilder<FollowCheckWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(DIRECTORY_ID to directoryId?.value))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.KEEP, request)
    }
}

/**
 * WorkManager creates workers itself, so this one gets the check from a Hilt entry point instead of its constructor,
 * as the artwork download does. The outcome is folded into its result (ADR-007).
 */
internal class FollowCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val check = EntryPointAccessors.fromApplication<FollowCheckEntryPoint>(applicationContext).followCheck()
        val outcome = inputData.getString(DIRECTORY_ID)
            ?.let { check.checkShow(DirectoryId(it)) }
            ?: check.checkAll()
        return when (outcome) {
            FollowCheckOutcome.Done -> Result.success()
            FollowCheckOutcome.Retry -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface FollowCheckEntryPoint {
    fun followCheck(): FollowCheck
}

/**
 * When the check is requested: at each start of the process, and when the app comes back to the front more than
 * [FOLLOW_CHECK_AGAIN_AFTER] after it was last requested. There is no periodic work (ADR-031, Follow checks).
 */
@Singleton
internal class FollowCheckRequests @Inject constructor(
    private val scheduler: FollowCheckScheduler,
    private val clock: Clock,
) {
    private var lastRequestedAt: Instant? = null

    fun onProcessStart() = request()

    fun onForeground() {
        val last = lastRequestedAt
        if (last == null || Duration.between(last, clock.instant()) > FOLLOW_CHECK_AGAIN_AFTER) request()
    }

    private fun request() {
        lastRequestedAt = clock.instant()
        scheduler.checkAll()
    }
}

/** Requests the follow check at each start of the process, and watches the app come back to the front (ADR-025). */
internal class FollowCheckOnProcessStart @Inject constructor(private val requests: FollowCheckRequests) :
    ProcessStartTask {
    override fun onProcessStart() {
        requests.onProcessStart()
        // The process lifecycle must be observed from the main thread, which a task runs on.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = requests.onForeground()
            },
        )
    }
}
