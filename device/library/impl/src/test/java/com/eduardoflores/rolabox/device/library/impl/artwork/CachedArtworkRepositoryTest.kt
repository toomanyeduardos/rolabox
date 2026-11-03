package com.eduardoflores.rolabox.device.library.impl.artwork

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.ArtworkRef
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.library.impl.audioFile
import com.eduardoflores.rolabox.device.library.local.api.AlbumGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtistGroup
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.SongMetadata
import com.eduardoflores.rolabox.device.library.local.api.SongWrite
import com.eduardoflores.rolabox.device.library.local.api.Sourced
import com.eduardoflores.rolabox.device.library.testing.FakeAlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedArtworkRepositoryTest {
    private val index = FakeLibraryIndexLocalDataSource()
    private val artwork = FakeArtworkLocalDataSource()
    private val albums = FakeAlbumArtworkLocalDataSource()
    private val userData = FakeUserDataRepository()
    private val wanted = WantedAlbum()
    private val scheduler = FakeArtworkDownloadScheduler()
    private val repository =
        CachedArtworkRepository(index, artwork, albums, wanted, DownloadPermit(albums, userData), scheduler)

    private val red = FakeArtworkRepository.solid(RED)
    private val blue = FakeArtworkRepository.solid(BLUE)

    private fun uriOf(id: Long) = "content://media/external/audio/media/$id"

    private fun <T : Any> unknown() = Sourced<T>(null, FieldSource.Unknown)

    /** Indexes the files of [ids], at [dateModified], and returns the reference of each song. */
    private suspend fun index(vararg ids: Long, dateModified: Long = 100): List<ArtworkRef> {
        val existing = index.indexed.associate { it.file.mediaStoreId to it.id }
        val artist = ArtistGroup("artist", "Artist")
        val writes = ids.map { id ->
            SongWrite(
                id = existing[id],
                file = audioFile(id, dateModified = dateModified).file,
                metadata = SongMetadata(
                    unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(), unknown(),
                ),
                artist = artist,
                album = AlbumGroup("album", "Album", null, artist),
            )
        }
        index.write(IndexBatch(writes))
        return ids.map { id -> ArtworkRef(index.indexed.first { it.file.mediaStoreId == id }.id) }
    }

    @Test
    fun aSongsArtwork_isTheThumbnailOfItsFile() = runTest {
        val (song) = index(1)
        artwork.results[uriOf(1)] = red.right()

        assertEquals(red.right(), repository.loadArtwork(song))
        assertEquals(listOf(uriOf(1)), artwork.loads)
    }

    @Test
    fun aSongWithNoArtwork_hasNone() = runTest {
        val (song) = index(1)

        assertEquals(null.right(), repository.loadArtwork(song))
    }

    @Test
    fun aSongNoLongerInTheLibrary_hasNone_andNothingIsLoaded() = runTest {
        assertEquals(null.right(), repository.loadArtwork(ArtworkRef(SongId(99))))
        assertEquals(emptyList<String>(), artwork.loads)
    }

    @Test
    fun aFileTheSystemWontOpen_isAnError_thatIsTriedAgain() = runTest {
        val (song) = index(1)
        artwork.results[uriOf(1)] = LibraryError.FileUnreadable(uriOf(1)).left()

        assertEquals(LibraryError.FileUnreadable(uriOf(1)).left(), repository.loadArtwork(song))
        artwork.results[uriOf(1)] = red.right()
        assertEquals(red.right(), repository.loadArtwork(song))
    }

    @Test
    fun aSecondLoad_comesFromMemory_withOrWithoutArtwork() = runTest {
        val (covered, bare) = index(1, 2)
        artwork.results[uriOf(1)] = red.right()

        repository.loadArtwork(covered)
        repository.loadArtwork(bare)
        assertEquals(red.right(), repository.loadArtwork(covered))
        assertEquals(null.right(), repository.loadArtwork(bare))

        assertEquals(listOf(uriOf(1), uriOf(2)), artwork.loads)
    }

    @Test
    fun aFileThatChanged_loadsAgain() = runTest {
        val (song) = index(1)
        artwork.results[uriOf(1)] = red.right()
        repository.loadArtwork(song)

        index(1, dateModified = 200)
        artwork.results[uriOf(1)] = blue.right()

        assertEquals(blue.right(), repository.loadArtwork(song))
    }

    @Test
    fun onlyTheLastFewCovers_stayInMemory() = runTest {
        val refs = index(*LongArray(CACHED_ARTWORK + 1) { it + 1L })
        refs.forEach { repository.loadArtwork(it) }
        artwork.loads.clear()

        repository.loadArtwork(refs.last())
        assertEquals(emptyList<String>(), artwork.loads)
        repository.loadArtwork(refs.first())
        assertEquals(listOf(uriOf(1)), artwork.loads)
    }

    @Test
    fun aRefIsKeptByItsSong_soTwoSongsDontShareACover() = runTest {
        val (first, second) = index(1, 2)
        artwork.results[uriOf(1)] = red.right()
        artwork.results[uriOf(2)] = blue.right()

        assertEquals(red, repository.loadArtwork(first).getOrNull())
        assertEquals(blue, repository.loadArtwork(second).getOrNull())
        assertNull(repository.loadArtwork(ArtworkRef(SongId(99))).getOrNull())
    }

    // Downloaded covers (ADR-027)

    private val green = FakeArtworkRepository.solid(GREEN)

    /** Puts the songs of [refs] on one album, whose cover stands for it is the first's. */
    private fun album(vararg refs: ArtworkRef, record: ArtworkRecord? = null) {
        albums.albums = listOf(ArtworkAlbum(KEY, "Album", "Artist", false, uriOf(1), null))
        refs.forEach { albums.albumOfSong[it.song] = KEY }
        if (record != null) albums.records.value = mapOf(KEY to record)
    }

    private fun found(): ArtworkRecord.Found {
        albums.files["cover.jpg"] = byteArrayOf(1)
        albums.images["cover.jpg"] = green
        return ArtworkRecord.Found("cover.jpg", "mbid", 1)
    }

    @Test
    fun anAlbumWithNoCoverOnThePhone_showsItsDownloadedOne() = runTest {
        val (song) = index(1)
        album(song, record = found())

        assertEquals(green.right(), repository.loadArtwork(song))
    }

    @Test
    fun aCoverOnThePhone_winsOverADownloadedOne_whichIsDeleted() = runTest {
        val (song) = index(1)
        album(song, record = found())
        artwork.results[uriOf(1)] = red.right()

        assertEquals(red.right(), repository.loadArtwork(song))
        assertEquals(ArtworkRecord.OnPhone, albums.records.value[KEY])
        assertEquals(emptySet<String>(), albums.files.keys)
    }

    @Test
    fun aCoverOnThePhone_isRecorded_soItsAlbumIsntDownloaded() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.Pending)
        artwork.results[uriOf(1)] = red.right()

        repository.loadArtwork(song)

        assertEquals(ArtworkRecord.OnPhone, albums.records.value[KEY])
    }

    @Test
    fun aDownloadedCoverWhoseFileIsGone_isNone_andTheAlbumIsQueuedAgain() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.Found("gone.jpg", "mbid", 1))

        assertEquals(null.right(), repository.loadArtwork(song))
        assertEquals(ArtworkRecord.Pending, albums.records.value[KEY])
    }

    @Test
    fun anAlbumOnThePhone_whoseCoverIsGone_isQueuedAgain() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.OnPhone)

        assertEquals(null.right(), repository.loadArtwork(song))
        assertEquals(ArtworkRecord.Pending, albums.records.value[KEY])
    }

    @Test
    fun aSongWithNoCoverOfItsOwn_onAnAlbumWhoseOtherSongHasOne_leavesTheAlbumOnThePhone() = runTest {
        val (first, second) = index(1, 2)
        album(first, second, record = ArtworkRecord.OnPhone)
        artwork.results[uriOf(1)] = red.right()

        assertEquals(null.right(), repository.loadArtwork(second))
        assertEquals(ArtworkRecord.OnPhone, albums.records.value[KEY])
    }

    @Test
    fun aCoverThatArrives_isEmitted_whileTheSongIsObserved() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.Pending)
        val seen = mutableListOf<ArtworkImage?>()
        val observing = launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeArtwork(song).collect { seen += it.getOrNull() }
        }

        // The fake names the first file it keeps cover-1.jpg.
        albums.images["cover-1.jpg"] = green
        albums.saveCover(KEY, "mbid", byteArrayOf(1), foundAt = 2)

        observing.cancel()
        assertEquals(null, seen.first())
        assertEquals(green, seen.last())
    }

    // The album shown goes first (ADR-027, The work)

    private fun TestScope.observe(ref: ArtworkRef): Job =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.observeArtwork(ref).collect {} }

    @Test
    fun observingAQueuedAlbum_wantsIt_andEnqueuesTheWork() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.Pending)

        observe(song)

        assertEquals(KEY, wanted.get())
        assertEquals(listOf("enqueue"), scheduler.calls)
    }

    @Test
    fun observingAnAlbumThePassHasntSeen_queuesIt_andWantsIt() = runTest {
        val (song) = index(1)
        album(song)

        observe(song)

        assertEquals(ArtworkRecord.Pending, albums.records.value[KEY])
        assertEquals(KEY, wanted.get())
    }

    @Test
    fun observingAnAlbumWhoseOtherSongHasACover_recordsItOnThePhone_andWantsNothing() = runTest {
        val (first, second) = index(1, 2)
        album(first, second)
        artwork.results[uriOf(1)] = red.right()

        observe(second)

        assertEquals(ArtworkRecord.OnPhone, albums.records.value[KEY])
        assertNull(wanted.get())
        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun observingAnAlbumFound_onThePhone_orNotFound_wantsNothing() = runTest {
        val (song) = index(1)
        listOf(found(), ArtworkRecord.OnPhone, ArtworkRecord.NotFound(1, 0)).forEach { record ->
            artwork.results[uriOf(1)] = if (record == ArtworkRecord.OnPhone) red.right() else null.right()
            album(song, record = record)

            val observing = observe(song)

            assertNull("$record", wanted.get())
            observing.cancel()
        }
        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun observingAnAlbum_inOfflineMode_wantsIt_butEnqueuesNothing() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.Pending)
        userData.setOfflineModeChosen(true)

        observe(song)

        assertEquals(KEY, wanted.get())
        assertEquals(emptyList<String>(), scheduler.calls)
    }

    @Test
    fun loadingForTheNotification_wantsNothing() = runTest {
        val (song) = index(1)
        album(song, record = ArtworkRecord.Pending)

        repository.loadArtwork(song)

        assertNull(wanted.get())
    }

    @Test
    fun observingASongNoLongerInTheLibrary_isOneNone() = runTest {
        assertEquals(listOf(null.right()), repository.observeArtwork(ArtworkRef(SongId(99))).toList())
    }

    @Test
    fun theSetting_isTheLocalSides() = runTest {
        repository.setDownloadEnabled(false)

        assertEquals(false.right(), repository.observeDownloadEnabled().first())
        assertEquals(false, albums.downloadEnabled.value)
    }

    private companion object {
        const val KEY = "album|artist"
        const val GREEN = 0xFF00FF00.toInt()
        const val RED = 0xFFFF0000.toInt()
        const val BLUE = 0xFF0000FF.toInt()
    }
}
