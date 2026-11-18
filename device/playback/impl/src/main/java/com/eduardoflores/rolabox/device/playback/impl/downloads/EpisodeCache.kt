package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where episodes are downloaded to and played from: one [SimpleCache] in `filesDir/episodes/`, which never evicts
 * (ADR-031, Downloads), and the database where Media3 keeps its record of it and of the downloads. A `SimpleCache` may
 * be opened once per folder in a process, so the download and the player share this one. Both are made on first use,
 * since opening them reads the disk.
 *
 * [upstream] is how an episode's bytes are fetched: Media3's own HTTP, never OkHttp (ADR-027, rule 7). The tests give
 * their own folder, database, HTTP and [freeSpace].
 */
@OptIn(UnstableApi::class)
@Singleton
internal class EpisodeCache(
    private val folder: File,
    databaseProvider: () -> DatabaseProvider,
    val upstream: DataSource.Factory,
    private val freeSpace: (File) -> Long = File::getUsableSpace,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, EPISODES_FOLDER),
        { StandaloneDatabaseProvider(context) },
        DefaultHttpDataSource.Factory()
            // Enclosures go through tracking redirects, some of which go from http to https.
            .setAllowCrossProtocolRedirects(true),
    )

    val databaseProvider: DatabaseProvider by lazy(databaseProvider)

    val cache: Cache by lazy { SimpleCache(folder, NoOpCacheEvictor(), this.databaseProvider) }

    /** The folder's free space, which a download is checked against before it is added. */
    fun freeBytes(): Long = freeSpace(folder.apply { mkdirs() })

    /**
     * What the player reads an item through. A song's `content://` URI is read by [DefaultDataSource] as it is. An
     * episode's URL goes through the cache, read-only: what is downloaded is read from it, and what isn't yet is
     * fetched from [upstream] without being written, since only the download writes (ADR-031, Stream while
     * downloading). The cache key is the item's own, which [episodeCacheKey] gives.
     *
     * A fetch is refused while [needsWifi] says so, as a connection that failed, so no byte of an episode comes over a
     * metered network with Use Mobile Data Off (ADR-031, rule 9).
     */
    fun playerDataSource(context: Context, needsWifi: () -> Boolean = { false }): DataSource.Factory =
        DefaultDataSource.Factory(
            context,
            CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(
                    ResolvingDataSource.Factory(upstream) { spec ->
                        if (needsWifi()) {
                            throw DataSourceException(
                                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            )
                        }
                        spec
                    },
                )
                .setCacheWriteDataSinkFactory(null),
        )

    /** Closes the cache, which only the tests do: the app's lives as long as the process. */
    fun release() = cache.release()
}

/**
 * The cache key and the download's id of an episode: its id, not its URL, which goes through tracking redirects that
 * change from one request to the next (ADR-031, Downloads).
 */
internal fun episodeCacheKey(episode: EpisodeId): String = "$EPISODE_KEY_PREFIX${episode.value}"

/** The episode of a cache key or download id, or `null` when it isn't one of [episodeCacheKey]'s. */
internal fun episodeOfCacheKey(key: String): EpisodeId? =
    key.removePrefix(EPISODE_KEY_PREFIX).takeIf { it != key }?.toLongOrNull()?.let(::EpisodeId)

private const val EPISODES_FOLDER = "episodes"
private const val EPISODE_KEY_PREFIX = "episode-"
