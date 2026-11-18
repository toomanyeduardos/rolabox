package com.eduardoflores.rolabox.device.podcasts.impl.artwork

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The unique work of the download (ADR-031, Artwork). */
internal const val PODCAST_ARTWORK_DOWNLOAD_WORK = "podcast-artwork-download"

private const val EPISODE_ID = "episodeId"

/** Where the download runs: WorkManager's work in the app, a fake in tests. */
internal interface PodcastArtworkScheduler {
    /** Downloads [episodeId]'s artwork, after any download already enqueued, once there is a network. */
    fun enqueue(episodeId: EpisodeId)
}

/**
 * Each download as a run of one unique WorkManager work, with a network of any kind: it survives the process, waits
 * for the network, and backs off when the network fails. Runs are appended, so one image is downloaded at a time
 * (ADR-031, Artwork).
 */
internal class WorkManagerPodcastArtworkScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PodcastArtworkScheduler {
    override fun enqueue(episodeId: EpisodeId) {
        val request = OneTimeWorkRequestBuilder<PodcastArtworkWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(workDataOf(EPISODE_ID to episodeId.value))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(PODCAST_ARTWORK_DOWNLOAD_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}

/**
 * WorkManager creates workers itself, so this one gets the downloader from a Hilt entry point instead of its
 * constructor, as the library's covers do (ADR-027). The outcome is folded into its result (ADR-007).
 */
internal class PodcastArtworkWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val episodeId = inputData.getLong(EPISODE_ID, -1).takeIf { it >= 0 } ?: return Result.success()
        val downloader = EntryPointAccessors.fromApplication<PodcastArtworkEntryPoint>(applicationContext)
            .podcastArtworkDownloader()
        return when (downloader.download(EpisodeId(episodeId))) {
            ArtworkOutcome.Done -> Result.success()
            ArtworkOutcome.Retry -> Result.retry()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface PodcastArtworkEntryPoint {
    // Named for the part: every entry point's getters are methods of one component, so the library's downloader()
    // would clash with it.
    fun podcastArtworkDownloader(): PodcastArtworkDownloader
}
