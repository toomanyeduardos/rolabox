package com.eduardoflores.rolabox.device.playback.impl.downloads

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.test.core.app.ApplicationProvider
import arrow.core.Either
import com.eduardoflores.rolabox.common.userdata.api.EpisodesOnMobileData
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Stream
import com.eduardoflores.rolabox.device.library.testing.audio.Mp3Tags
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Episode downloads with Media3's real `DownloadManager`, into a real cache, from [FakeEpisodeServer] on the loopback
 * (ADR-031, Downloads). There is no service on the JVM, so starting it resumes the manager, as the service does.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
class MediaDownloadsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val bytes = FixtureContent.Mp3(Mp3Tags.None, Mp3Stream(durationMs = LENGTH_MS)).bytes()
    private val server = FakeEpisodeServer(bytes)
    private val scope = CoroutineScope(SupervisorJob() + Main.dispatcher)
    private val caches = mutableListOf<EpisodeCache>()
    private var free = Long.MAX_VALUE
    private val userData = FakeUserDataRepository()
    private var latest: Map<PlaybackItemId, ItemDownload> = emptyMap()
    private lateinit var downloads: MediaDownloads

    @Before
    fun setUp() {
        TestNetwork.wifi(context)
        downloads = newDownloads()
    }

    @After
    fun tearDown() {
        server.close()
        scope.cancel()
        caches.forEach { it.release() }
    }

    @Test
    fun play_downloadsTheWholeEpisode_intoTheCache_byItsId() {
        val result = Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }

        assertTrue(result.isRight())
        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
        assertTrue(caches.last().cache.isCached(episodeCacheKey(EPISODE), 0, bytes.size.toLong()))
    }

    @Test
    fun aDownloadThatIsDone_isntAddedAgain() {
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
        val requests = server.ranges.size

        val result = Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }

        assertTrue(result.isRight())
        Main.steps(STEPS_TO_SETTLE)
        assertEquals(requests, server.ranges.size)
        assertEquals(ItemDownload.Done, latest[ITEM])
    }

    @Test
    fun storageFull_addsNothing() {
        free = bytes.size + FREE_SPACE_MARGIN_BYTES - 1

        val result = Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }

        assertEquals(Either.Left(PlaybackError.StorageFull), result)
        Main.steps(STEPS_TO_SETTLE)
        assertEquals(null, latest[ITEM])
        assertTrue(server.ranges.isEmpty())
    }

    @Test
    fun aFeedWithNoSize_isCheckedAgainstTheMarginAlone() {
        free = FREE_SPACE_MARGIN_BYTES - 1
        assertEquals(Either.Left(PlaybackError.StorageFull), Main.run { downloads.download(EPISODE, server.url, null) })

        free = FREE_SPACE_MARGIN_BYTES

        assertTrue(Main.run { downloads.download(EPISODE, server.url, null) }.isRight())
    }

    @Test
    fun aMissingFile_fails_andPlayingAgainAddsItAgain() {
        server.missing = true
        Main.run { downloads.manager.minRetryCount = 0 }

        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("the download fails") { latest[ITEM] == ItemDownload.Failed }

        server.missing = false
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
    }

    @Test
    fun aLostConnection_resumesWhereItStopped_withARangeRequest() {
        server.stallAfter = bytes.size / 2
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("half is downloaded") { latest[ITEM].fraction() > 0f }

        server.offline = true
        server.dropConnections()
        Main.steps(STEPS_TO_SETTLE)
        server.offline = false

        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
        assertTrue("Ranges: ${server.ranges}", server.ranges.any { it.startsAfterZero() })
        assertTrue(caches.last().cache.isCached(episodeCacheKey(EPISODE), 0, bytes.size.toLong()))
    }

    @Test
    fun aDownloadAKillStopped_resumesAtTheNextStart() {
        server.stallAfter = bytes.size / 2
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("half is downloaded") { latest[ITEM].fraction() > 0f }

        // The process dies: what Media3 wrote of the record and the cache is all that is left.
        server.dropConnections()
        Main.run { downloads.manager.release() }
        caches.removeAt(caches.lastIndex).release()
        latest = emptyMap()

        downloads = newDownloads()
        Main.run { downloads.resumeUnfinished() }

        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
        assertTrue("Ranges: ${server.ranges}", server.ranges.any { it.startsAfterZero() })
    }

    @Test
    fun mobileDataOff_onAMeteredNetwork_addsNothing_andSaysSo() {
        TestNetwork.mobile(context)
        Main.run { userData.setEpisodesOnMobileData(EpisodesOnMobileData.OFF) }
        Main.steps(STEPS_TO_SETTLE)

        val result = Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }

        assertEquals(Either.Left(PlaybackError.MobileDataOff), result)
        Main.steps(STEPS_TO_SETTLE)
        assertTrue(server.ranges.isEmpty())
    }

    @Test
    fun mobileDataOn_downloadsOnAMeteredNetwork() {
        TestNetwork.mobile(context)

        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }

        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
    }

    @Test
    fun mobileDataOff_aDownloadUnderWay_waitsForWifi_andGoesOnThere() {
        server.stallAfter = bytes.size / 2
        Main.run { downloads.download(EPISODE, server.url, bytes.size.toLong()) }
        Main.until("half is downloaded") { latest[ITEM].fraction() > 0f }

        Main.run { userData.setEpisodesOnMobileData(EpisodesOnMobileData.OFF) }
        TestNetwork.mobile(context)
        server.dropConnections()
        Main.until("the download waits") { latest[ITEM] is ItemDownload.Waiting }
        val requests = server.ranges.size
        Main.steps(STEPS_TO_SETTLE)
        assertEquals("No byte over mobile data", requests, server.ranges.size)

        TestNetwork.wifi(context)

        Main.until("the download is done") { latest[ITEM] == ItemDownload.Done }
    }

    private fun newDownloads(): MediaDownloads {
        val cache = EpisodeCache(
            File(folder.root, "episodes"),
            { StandaloneDatabaseProvider(context) },
            DefaultHttpDataSource.Factory(),
        ) { free }
        caches += cache
        val made = MediaDownloads(
            context,
            cache,
            scope,
            Main.dispatcher,
            Main.io,
            EpisodeNetworkPolicy(ConnectivityPhoneNetwork(context, scope), userData, scope),
        ) { manager -> manager.resumeDownloads() }
        scope.launch { made.observe().collect { latest = it } }
        return made
    }

    private fun ItemDownload?.fraction(): Float = when (this) {
        is ItemDownload.Downloading -> fraction
        is ItemDownload.Waiting -> fraction
        else -> 0f
    }

    private fun String?.startsAfterZero(): Boolean =
        this?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull()?.let { it > 0 } == true

    private companion object {
        val EPISODE = EpisodeId(7)
        val ITEM = PlaybackItemId.Episode(EPISODE)
        const val LENGTH_MS = 60_000
        const val STEPS_TO_SETTLE = 50
    }
}
