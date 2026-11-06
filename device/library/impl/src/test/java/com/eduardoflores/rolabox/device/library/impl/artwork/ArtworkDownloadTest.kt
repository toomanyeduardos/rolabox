package com.eduardoflores.rolabox.device.library.impl.artwork

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.impl.LibraryScanner
import com.eduardoflores.rolabox.device.library.impl.audioFile
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.network.api.Cover
import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import com.eduardoflores.rolabox.device.library.network.api.CoverSourceError
import com.eduardoflores.rolabox.device.library.testing.FakeAlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeCoverNetworkDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeTagsLocalDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val Live2012 = CoverQuery("Live 2012", CoverArtist.Named("Coldplay"))
private val Jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

/** The pass after a scan, the download, and what starts and stops them (ADR-027). */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkDownloadTest {
    private val albums = FakeAlbumArtworkLocalDataSource()
    private val thumbnails = FakeArtworkLocalDataSource()
    private val source = FakeCoverNetworkDataSource()
    private val userData = FakeUserDataRepository()
    private val clock = TestClock(1_000_000)
    private val permit = DownloadPermit(albums, userData)
    private val pass = MissingArtworkPass(albums, thumbnails, clock)
    private val wanted = WantedAlbum()
    private val downloader = ArtworkDownloader(albums, source, permit, wanted, clock)

    private fun album(key: String, title: String? = key, artist: String? = "Artist", isVarious: Boolean = false) =
        ArtworkAlbum(key, title, artist, isVarious, "content://song/$key", null)

    private val live2012 = album("live", "Live 2012", "Coldplay")

    private val records get() = albums.records.value

    // The pass after a scan

    @Test
    fun anAlbumWithACoverOnThePhone_isRecordedSo_andNotDownloaded() = runTest {
        albums.albums = listOf(live2012)
        thumbnails.results[live2012.artworkSongUri] = FakeArtworkRepository.solid(1).right()

        assertEquals(false.right(), pass.run())
        assertEquals(ArtworkRecord.OnPhone, records["live"])
    }

    @Test
    fun anAlbumWithNoCoverOnThePhone_isQueued() = runTest {
        albums.albums = listOf(live2012)

        assertEquals(true.right(), pass.run())
        assertEquals(ArtworkRecord.Pending, records["live"])
    }

    @Test
    fun anAlbumWithARecord_isntLookedAtAgain() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.Found("f.jpg", "id", 1))

        assertEquals(false.right(), pass.run())
        assertEquals(emptyList<String>(), thumbnails.loads)
    }

    @Test
    fun anAlbumWithNoTitle_orByTheUnknownArtist_isNeverLookedAt() = runTest {
        albums.albums =
            listOf(album("untitled", title = null), album("blank", title = " "), album("anon", artist = null))

        assertEquals(false.right(), pass.run())
        assertEquals(emptyMap<String, ArtworkRecord>(), records)
        assertEquals(emptyList<String>(), thumbnails.loads)
    }

    @Test
    fun aFileTheSystemWontOpen_leavesItsAlbumForTheNextPass() = runTest {
        albums.albums = listOf(live2012)
        thumbnails.results[live2012.artworkSongUri] = LibraryError.FileUnreadable(live2012.artworkSongUri).left()

        assertEquals(false.right(), pass.run())
        assertNull(records["live"])
    }

    @Test
    fun anAlbumNotFoundLongEnoughAgo_isDueAgain() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.NotFound(1, clock.millis() - 1.days.inWholeMilliseconds))

        assertEquals(true.right(), pass.run())
    }

    // The download

    @Test
    fun aCoverFound_isKept_andTheAlbumIsntAskedForAgain() = runTest {
        albums.albums = listOf(live2012.copy(record = null))
        albums.records.value = mapOf("live" to ArtworkRecord.Pending)
        source.answers[Live2012] = Cover("mbid", Jpeg).right()

        assertEquals(DownloadOutcome.Done, downloader.run())
        assertEquals(ArtworkRecord.Found("cover-1.jpg", "mbid", clock.millis()), records["live"])
        assertTrue(albums.files.getValue("cover-1.jpg").contentEquals(Jpeg))

        clock.advance(365.days)
        assertEquals(DownloadOutcome.Done, downloader.run())
        assertEquals(listOf(Live2012), source.requests)
    }

    @Test
    fun noCover_isAnAttempt() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.Pending)

        assertEquals(DownloadOutcome.Done, downloader.run())
        assertEquals(ArtworkRecord.NotFound(1, clock.millis()), records["live"])
    }

    @Test
    fun anAlbumNotFound_isTriedAfterADay_thenAWeek_andNotAfterThreeAttempts() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.Pending)
        downloader.run()

        assertAskedAfter(23.hours, expected = 1)
        assertAskedAfter(1.hours, expected = 2)
        assertEquals(ArtworkRecord.NotFound(2, clock.millis()), records["live"])
        assertAskedAfter(6.days, expected = 2)
        assertAskedAfter(1.days, expected = 3)
        assertEquals(ArtworkRecord.NotFound(3, clock.millis()), records["live"])
        assertAskedAfter(1_000.days, expected = 3)
    }

    private suspend fun assertAskedAfter(wait: Duration, expected: Int) {
        clock.advance(wait)
        downloader.run()
        assertEquals(expected, source.requests.size)
    }

    @Test
    fun aSourceThatCantBeAsked_isNoAttempt_andTheWorkRunsAgain() = runTest {
        albums.albums = listOf(live2012)
        listOf(
            CoverSourceError.Unreachable,
            CoverSourceError.Server(503),
            CoverSourceError.Malformed,
        ).forEach { error ->
            albums.records.value = mapOf("live" to ArtworkRecord.Pending)
            source.answers[Live2012] = error.left()

            assertEquals(DownloadOutcome.Retry, downloader.run())
            assertEquals(ArtworkRecord.Pending, records["live"])
        }
    }

    @Test
    fun anAnswerThatCantBeKept_runsAgain() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.Pending)
        source.answers[Live2012] = Cover("mbid", Jpeg).right()
        albums.writeError = LibraryError.Storage(StorageError.Unavailable)

        assertEquals(DownloadOutcome.Retry, downloader.run())
    }

    @Test
    fun albumsAreAskedForOneAtATime_queuedOnesFirst() = runTest {
        val due = clock.millis() - 2.days.inWholeMilliseconds
        albums.albums = listOf(album("a"), album("b"), album("c"))
        albums.records.value = mapOf(
            "a" to ArtworkRecord.NotFound(1, due),
            "b" to ArtworkRecord.Pending,
            "c" to ArtworkRecord.Pending,
        )

        downloader.run()

        assertEquals(listOf("b", "c", "a"), source.requests.map { it.title })
    }

    @Test
    fun theAlbumShown_isAskedForFirst() = runTest {
        albums.albums = listOf(album("a"), album("b"), album("c"))
        albums.records.value =
            mapOf("a" to ArtworkRecord.Pending, "b" to ArtworkRecord.Pending, "c" to ArtworkRecord.Pending)
        wanted.want("c")

        downloader.run()

        assertEquals(listOf("c", "a", "b"), source.requests.map { it.title })
    }

    @Test
    fun anAlbumShownWhileARequestIsOut_isNext_andTheRequestOutIsKept() = runTest {
        albums.albums = listOf(album("a"), album("b"), album("c"))
        albums.records.value =
            mapOf("a" to ArtworkRecord.Pending, "b" to ArtworkRecord.Pending, "c" to ArtworkRecord.Pending)
        source.answers[CoverQuery("a", CoverArtist.Named("Artist"))] = Cover("mbid-a", Jpeg).right()
        source.onRequest = { if (it.title == "a") wanted.want("c") }

        downloader.run()

        assertEquals(listOf("a", "c", "b"), source.requests.map { it.title })
        assertEquals(ArtworkRecord.Found("cover-1.jpg", "mbid-a", clock.millis()), records["a"])
    }

    @Test
    fun anAlbumShownThatWasntFound_waitsItsTurn() = runTest {
        albums.albums = listOf(album("a"), album("b"))
        albums.records.value = mapOf("a" to ArtworkRecord.Pending, "b" to ArtworkRecord.NotFound(1, clock.millis()))
        wanted.want("b")

        downloader.run()

        assertEquals(listOf("a"), source.requests.map { it.title })
    }

    @Test
    fun aVariousArtistsAlbum_isAskedForAsOne() = runTest {
        albums.albums = listOf(album("hits", title = "Hits", artist = null, isVarious = true))
        albums.records.value = mapOf("hits" to ArtworkRecord.Pending)

        downloader.run()

        assertEquals(listOf(CoverQuery("Hits", CoverArtist.Various)), source.requests)
    }

    // What the match reads of the songs goes with the album (ADR-027, How a match is judged).
    @Test
    fun anAlbum_isAskedForWithWhatItsSongsSay() = runTest {
        albums.albums = listOf(
            album("city", title = "Radio City", artist = "Dave Matthews").copy(
                songArtists = listOf("Dave Matthews & Tim Reynolds"),
                highestTrackNumber = 14,
                year = 2007,
            ),
        )
        albums.records.value = mapOf("city" to ArtworkRecord.Pending)

        downloader.run()

        assertEquals(
            listOf(
                CoverQuery(
                    "Radio City",
                    CoverArtist.Named("Dave Matthews"),
                    listOf("Dave Matthews & Tim Reynolds"),
                    highestTrackNumber = 14,
                    year = 2007,
                ),
            ),
            source.requests,
        )
    }

    @Test
    fun inOfflineMode_noRequestIsMade() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.Pending)
        userData.setOfflineModeChosen(true)

        assertEquals(DownloadOutcome.Done, downloader.run())
        assertEquals(emptyList<CoverQuery>(), source.requests)
    }

    @Test
    fun withTheSettingOff_noRequestIsMade() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.Pending)
        albums.downloadEnabled.value = false

        assertEquals(DownloadOutcome.Done, downloader.run())
        assertEquals(emptyList<CoverQuery>(), source.requests)
    }

    @Test
    fun theSettingTurnedOffWhileItRuns_stopsItBeforeTheNextRequest() = runTest {
        albums.albums = listOf(album("a"), album("b"))
        albums.records.value = mapOf("a" to ArtworkRecord.Pending, "b" to ArtworkRecord.Pending)
        source.onRequest = { albums.downloadEnabled.value = false }

        assertEquals(DownloadOutcome.Done, downloader.run())
        assertEquals(listOf("a"), source.requests.map { it.title })
    }

    // What starts and stops them

    private val scheduler = FakeArtworkDownloadScheduler()

    private fun TestScope.downloads(): Pair<ArtworkDownloads, LibraryScanner> {
        val files = FakeAudioFilesLocalDataSource().apply { this.files = listOf(audioFile(1)) }
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scanner =
            LibraryScanner(
                files,
                FakeLibraryIndexLocalDataSource(),
                FakeTagsLocalDataSource(),
                backgroundScope,
                dispatcher,
            )
        return ArtworkDownloads(scanner, permit, pass, scheduler, backgroundScope, dispatcher) to scanner
    }

    @Test
    fun aScanThatEnds_withAnAlbumQueued_enqueuesTheWork() = runTest {
        albums.albums = listOf(live2012)
        val (downloads, scanner) = downloads()
        downloads.start()

        scanner.start()
        runCurrent()

        assertEquals(listOf("enqueue"), scheduler.calls)
    }

    @Test
    fun aScanThatEnds_withNothingDue_enqueuesNothing() = runTest {
        albums.albums = listOf(live2012)
        albums.records.value = mapOf("live" to ArtworkRecord.OnPhone)
        val (downloads, scanner) = downloads()
        downloads.start()

        scanner.start()
        runCurrent()

        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun offlineMode_cancelsTheWork_andNoPassRuns() = runTest {
        albums.albums = listOf(live2012)
        userData.setOfflineModeChosen(true)
        val (downloads, scanner) = downloads()
        downloads.start()

        scanner.start()
        runCurrent()

        assertEquals(listOf("cancel"), scheduler.calls)
        assertNull(records["live"])
    }

    @Test
    fun turningTheSettingBackOn_runsThePass() = runTest {
        albums.albums = listOf(live2012)
        albums.downloadEnabled.value = false
        val (downloads, scanner) = downloads()
        downloads.start()
        scanner.start()
        runCurrent()

        albums.downloadEnabled.value = true
        runCurrent()

        assertEquals(listOf("cancel", "enqueue"), scheduler.calls)
    }
}

/** A wall clock that a test moves. */
private class TestClock(private var now: Long) : Clock() {
    fun advance(by: Duration) {
        now += by.inWholeMilliseconds
    }

    override fun millis(): Long = now

    override fun instant(): Instant = Instant.ofEpochMilli(now)

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this
}
