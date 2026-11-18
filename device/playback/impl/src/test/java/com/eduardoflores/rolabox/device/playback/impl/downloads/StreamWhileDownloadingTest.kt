package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.FakeClock
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.common.userdata.api.EpisodesOnMobileData
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.impl.buildPlayer
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The player reading an episode through the cache that its download writes (ADR-031, Stream while downloading): a
 * real [ExoPlayer] with the service's data source, a real download, and [FakeEpisodeServer] on the loopback.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class StreamWhileDownloadingTest {
    @get:Rule
    val codecs: ShadowMediaCodecConfig = ShadowMediaCodecConfig.withAllDefaultSupportedCodecs()

    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bytes = FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = LENGTH_MS)).bytes()
    private val server = FakeEpisodeServer(bytes)
    private val scope = CoroutineScope(SupervisorJob() + Main.dispatcher)
    private var latest: Map<PlaybackItemId, ItemDownload> = emptyMap()
    private lateinit var cache: EpisodeCache
    private lateinit var downloads: MediaDownloads
    private lateinit var player: ExoPlayer
    private val userData = FakeUserDataRepository()
    private lateinit var network: EpisodeNetworkPolicy

    @Before
    fun setUp() {
        TestNetwork.wifi(context)
        cache = EpisodeCache(
            File(folder.root, "episodes"),
            { StandaloneDatabaseProvider(context) },
            DefaultHttpDataSource.Factory(),
        )
        network = EpisodeNetworkPolicy(ConnectivityPhoneNetwork(context, scope), userData, scope)
        downloads = MediaDownloads(context, cache, scope, Main.dispatcher, Main.io, network) { it.resumeDownloads() }
        scope.launch { downloads.observe().collect { latest = it } }
        player = buildPlayer(context, cache.playerDataSource(context) { network.state.value.needsWifi }) {
            setClock(FakeClock(true))
            setStuckBufferingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingDetectionTimeoutMs(Int.MAX_VALUE)
            setStuckPlayingNotEndingTimeoutMs(Int.MAX_VALUE)
            setStuckSuppressedDetectionTimeoutMs(Int.MAX_VALUE)
        }
    }

    @After
    fun tearDown() {
        player.release()
        server.close()
        scope.cancel()
        cache.release()
    }

    @Test
    fun anEpisodePartlyDownloaded_playsToItsEnd_fetchingTheRestItself_withoutWritingIt() {
        server.stallAfter = bytes.size / 3
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("a third is downloaded") { latest[ITEM].isUnderWay() }
        // The download keeps its third, and its retry stalls at once: a download that is still arriving.
        server.stallAfter = 1
        server.dropConnections()
        Main.until("the download retries") { server.ranges.size >= 2 && cache.cachedBytes() > 0 }
        val retries = server.ranges.size

        play()

        Main.until("the episode ends") { player.playbackState == Player.STATE_ENDED }
        // The player read the third from the cache, and asked for the rest.
        assertTrue("Ranges: ${server.ranges}", server.ranges.drop(retries).all { it.startsAfterZero() })
        // Only the download writes, and it is still stalled.
        assertFalse(cache.cache.isCached(KEY, 0, bytes.size.toLong()))
    }

    // The player's own read of the data source, since Robolectric's audio doesn't start while a read is stalled.
    @Test
    fun withMobileDataOffOnAMeteredNetwork_thePlayerReadsTheDownload_andFetchesNothingMore() {
        server.stallAfter = bytes.size / 3
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("a third is downloaded") { latest[ITEM].isUnderWay() }
        server.stallEvery = 1
        server.dropConnections()
        Main.until("the download retries") { server.ranges.size >= 2 && cache.cachedBytes() > 0 }
        Main.run { userData.setEpisodesOnMobileData(EpisodesOnMobileData.OFF) }
        TestNetwork.mobile(context)
        Main.steps(STEPS_TO_SETTLE)
        val requests = server.ranges.size
        val downloaded = cache.cachedBytes()
        val source = cache.playerDataSource(context) { network.state.value.needsWifi }.createDataSource()

        source.open(DataSpec.Builder().setUri(server.url).setKey(KEY).build())
        val read = ByteArray(bytes.size)
        var at = 0
        val refused = runCatching {
            while (true) {
                val count = source.read(read, at, read.size - at)
                if (count == C.RESULT_END_OF_INPUT) break
                at += count
            }
        }.exceptionOrNull()

        assertEquals(downloaded, at.toLong())
        assertTrue(read.copyOf(at).contentEquals(bytes.copyOf(at)))
        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            (refused as? DataSourceException)?.reason,
        )
        assertEquals("No byte over mobile data", requests, server.ranges.size)
        source.close()
    }

    @Test
    fun anEpisodeDownloaded_playsWithNoConnection() {
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
        server.offline = true
        val requests = server.ranges.size

        play()

        Main.until("the episode ends") { player.playbackState == Player.STATE_ENDED }
        assertEquals(requests, server.ranges.size)
    }

    private fun play() {
        player.setMediaItem(MediaItem.Builder().setUri(server.url).setCustomCacheKey(KEY).build())
        player.prepare()
        player.play()
    }

    private fun ItemDownload?.isUnderWay(): Boolean =
        (this is ItemDownload.Downloading && fraction > 0f) || (this is ItemDownload.Waiting && fraction > 0f)

    private fun EpisodeCache.cachedBytes(): Long = cache.getCachedBytes(KEY, 0, bytes.size.toLong())

    private fun String?.startsAfterZero(): Boolean =
        this?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull()?.let { it > 0 } == true

    private companion object {
        val EPISODE = EpisodeId(3)
        val ITEM = PlaybackItemId.Episode(EPISODE)
        val KEY = episodeCacheKey(EPISODE)
        const val LENGTH_MS = 20_000

        const val STEPS_TO_SETTLE = 50
    }
}
