package com.eduardoflores.rolabox.device.library.impl.artwork

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The unique work of the download (ADR-027, rule 4). */
internal const val ARTWORK_DOWNLOAD_WORK = "artwork-download"

/**
 * The download as one unique WorkManager work, with a network of any kind: it survives the process, waits for the
 * network, and backs off when the source can't be asked (ADR-027, The work). A running one is kept, since it takes
 * every due album before it ends.
 */
internal class WorkManagerArtworkDownloadScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ArtworkDownloadScheduler {
    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<ArtworkDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(ARTWORK_DOWNLOAD_WORK, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(ARTWORK_DOWNLOAD_WORK)
    }
}

/**
 * WorkManager creates workers itself, so this one gets the downloader from a Hilt entry point instead of its
 * constructor, as sync's does (ADR-011). The `Either`s are folded into its result (ADR-007).
 */
internal class ArtworkDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val downloader = EntryPointAccessors.fromApplication<ArtworkDownloadEntryPoint>(applicationContext).downloader()
        return when (downloader.run()) {
            DownloadOutcome.Done -> Result.success()
            DownloadOutcome.Retry -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ArtworkDownloadEntryPoint {
    fun downloader(): ArtworkDownloader
}
