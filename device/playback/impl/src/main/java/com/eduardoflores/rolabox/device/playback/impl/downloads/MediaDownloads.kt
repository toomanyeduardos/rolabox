package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.scheduler.Requirements
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.Main
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.ItemDownloads
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.impl.Tick
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How playback downloads an episode, which only playing it does (ADR-031, rule 8). */
internal interface EpisodeDownloader {
    /** Each item's download, as [ItemDownloads] reports it. */
    val downloads: Flow<Map<PlaybackItemId, ItemDownload>>

    /**
     * What an episode that runs out of bytes would wait for now: no network, or one that Use Mobile Data keeps it from.
     * `null` when its bytes may come (ADR-031, rule 15).
     */
    val waitsFor: StateFlow<PlaybackError.Waiting?>

    /**
     * Adds the download of [episode] from [url], unless it is done or under way. A download that failed is added
     * again. One that isn't under way isn't added while episodes need Wi-Fi ([PlaybackError.MobileDataOff]), since it
     * couldn't play (ADR-031, Mobile data). Before it is added, the free space is checked against [sizeBytes], the
     * feed's size, plus [FREE_SPACE_MARGIN_BYTES] ([PlaybackError.StorageFull]), or against the margin alone when the
     * feed gives no size.
     */
    suspend fun download(episode: EpisodeId, url: String, sizeBytes: Long?): Either<PlaybackError, Unit>
}

/** How the download service is started, which the tests replace: they have no service, and resume the manager. */
@OptIn(UnstableApi::class)
internal fun interface DownloadServiceStarter {
    /** Starts the service, or does nothing when it runs or can't be started now. */
    fun start(manager: DownloadManager)
}

/**
 * Media3's [DownloadManager], into [EpisodeCache]'s cache, with Media3's own HTTP (ADR-031, Downloads). It keeps its
 * own record of each download in the cache's database, and resumes what was under way when it is made again, with a
 * range request. Media3 starts it paused: [EpisodeDownloadService] resumes it when it starts, and holds the process in
 * the foreground while a download runs.
 *
 * The state is the record as it was read once the manager started, kept up to date by its listener, with the fraction
 * read again every [Tick] while a download runs: Media3 doesn't push it. The manager is made, and called, on the main
 * thread, whose looper its listener answers on.
 */
// One parameter for each of what it downloads with: the context, the cache, the scope, the two threads, the network and
// the service.
@Suppress("LongParameterList")
@OptIn(UnstableApi::class)
@Singleton
internal class MediaDownloads @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodes: EpisodeCache,
    @ApplicationScope private val scope: CoroutineScope,
    @Dispatcher(Main) private val main: CoroutineDispatcher,
    @Dispatcher(IO) private val io: CoroutineDispatcher,
    private val network: EpisodeNetworkPolicy,
    private val service: DownloadServiceStarter,
) : ItemDownloads,
    EpisodeDownloader {
    /** Media3's record of each download, by its id, or `null` until it was read. */
    private val record = MutableStateFlow<Map<String, Download>?>(null)

    private val items = MutableStateFlow<Map<PlaybackItemId, ItemDownload>?>(null)

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            scope.launch { read(downloadManager) }
        }

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            record.update { it?.plus(download.request.id to download) }
            publish()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            record.update { it?.minus(download.request.id) }
            publish()
        }
    }

    /** The manager, made on first use. Main thread only. */
    val manager: DownloadManager by lazy {
        DownloadManager(
            context,
            episodes.databaseProvider,
            episodes.cache,
            episodes.upstream,
            Executors.newFixedThreadPool(DOWNLOAD_THREADS),
        ).apply {
            requirements = requirementsFor(network.state.value.mobileData)
            addListener(listener)
        }
    }

    init {
        // With Use Mobile Data Off a download waits for an unmetered network (ADR-031, rule 9).
        scope.launch {
            network.state.map { it.mobileData }.distinctUntilChanged().collect { mobileData ->
                withContext(main) { manager.requirements = requirementsFor(mobileData) }
            }
        }
        scope.launch {
            items.map { downloads -> downloads?.values?.any { it is ItemDownload.Downloading } == true }
                .distinctUntilChanged()
                .collectLatest { running ->
                    while (running) {
                        delay(Tick)
                        withContext(main) { publish() }
                    }
                }
        }
    }

    override fun observe(): Flow<Map<PlaybackItemId, ItemDownload>> = downloads

    override val downloads: Flow<Map<PlaybackItemId, ItemDownload>> =
        items.onStart { withContext(main) { manager } }.filterNotNull()

    override val waitsFor: StateFlow<PlaybackError.Waiting?> =
        network.state.map { it.waitsFor }.stateIn(scope, SharingStarted.Eagerly, network.state.value.waitsFor)

    override suspend fun download(episode: EpisodeId, url: String, sizeBytes: Long?): Either<PlaybackError, Unit> {
        withContext(main) { manager }
        val id = episodeCacheKey(episode)
        val existing = record.filterNotNull().first()[id]
        return when (existing?.state) {
            Download.STATE_COMPLETED -> Unit.right()

            Download.STATE_QUEUED, Download.STATE_DOWNLOADING, Download.STATE_RESTARTING ->
                withContext(main) { service.start(manager) }.right()

            else -> if (network.state.value.needsWifi) {
                PlaybackError.MobileDataOff.left()
            } else if (withContext(io) { episodes.freeBytes() } < (sizeBytes ?: 0) + FREE_SPACE_MARGIN_BYTES) {
                PlaybackError.StorageFull.left()
            } else {
                withContext(main) {
                    manager.addDownload(DownloadRequest.Builder(id, url.toUri()).setCustomCacheKey(id).build())
                    service.start(manager)
                }.right()
            }
        }
    }

    /**
     * Makes the manager, which resumes what was under way, and starts the service when a download is unfinished
     * (ADR-031, Downloads). There is no `Scheduler`, so this is how a download stopped by a kill goes on: at the next
     * start of the app.
     */
    suspend fun resumeUnfinished() {
        withContext(main) { manager }
        record.filterNotNull().first()
        withContext(main) { if (manager.currentDownloads.isNotEmpty()) service.start(manager) }
    }

    /** Reads every download of the record, done and failed ones included, which the manager's current ones aren't. */
    private suspend fun read(manager: DownloadManager) {
        val stored = withContext(io) {
            buildMap {
                manager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) put(cursor.download.request.id, cursor.download)
                }
            }
        }
        withContext(main) {
            // What changed while it was read is newer than what was read.
            record.update { changed -> stored + changed.orEmpty() }
            publish()
        }
    }

    /** Main thread only: the current downloads are read again, for their fraction. */
    private fun publish() {
        val now = record.value ?: return
        val current = manager.currentDownloads.associateBy { it.request.id }
        items.value = (now + current).entries.mapNotNull { (id, download) ->
            val item = episodeOfCacheKey(id) ?: return@mapNotNull null
            download.toItemDownload()?.let { PlaybackItemId.Episode(item) to it }
        }.toMap()
    }
}

/** Any network with Use Mobile Data On, and only an unmetered one with it Off (ADR-031, rule 9). */
@OptIn(UnstableApi::class)
private fun requirementsFor(mobileData: Boolean) =
    Requirements(if (mobileData) Requirements.NETWORK else Requirements.NETWORK_UNMETERED)

/** What playback says of a download. One being removed is gone already, which nothing does yet. */
@OptIn(UnstableApi::class)
private fun Download.toItemDownload(): ItemDownload? = when (state) {
    Download.STATE_QUEUED, Download.STATE_STOPPED, Download.STATE_RESTARTING -> ItemDownload.Waiting(fraction())
    Download.STATE_DOWNLOADING -> ItemDownload.Downloading(fraction())
    Download.STATE_COMPLETED -> ItemDownload.Done
    Download.STATE_FAILED -> ItemDownload.Failed
    else -> null
}

/** The fraction of the bytes downloaded, from Media3's percentage, or from the bytes when it has none yet. */
@OptIn(UnstableApi::class)
private fun Download.fraction(): Float {
    val percent = percentDownloaded
    return when {
        percent != C.PERCENTAGE_UNSET.toFloat() -> percent / PERCENT
        contentLength > 0 -> bytesDownloaded.toFloat() / contentLength
        else -> 0f
    }.coerceIn(0f, 1f)
}

/** What is left free beyond an episode's size, so a download never fills the phone (ADR-031, Downloads). */
internal const val FREE_SPACE_MARGIN_BYTES: Long = 50L * 1024 * 1024

private const val DOWNLOAD_THREADS = 2
private const val PERCENT = 100f
