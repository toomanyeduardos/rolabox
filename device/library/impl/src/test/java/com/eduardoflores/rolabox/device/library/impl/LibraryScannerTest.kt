package com.eduardoflores.rolabox.device.library.impl

import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import com.eduardoflores.rolabox.device.library.testing.FakeAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryScannerTest {
    private val files = FakeAudioFilesLocalDataSource()
    private val index = FakeLibraryIndexLocalDataSource()

    private fun TestScope.scanner() =
        LibraryScanner(files, index, backgroundScope, StandardTestDispatcher(testScheduler))

    private fun TestScope.scan() {
        scanner().start()
        runCurrent()
    }

    private fun songOf(id: Long): IndexedSong = index.indexed.first { it.file.mediaStoreId == id }

    @Test
    fun newFiles_areAdded() = runTest {
        files.files = listOf(audioFile(1), audioFile(2))

        scan()

        assertEquals(listOf(1L, 2L), index.indexed.map { it.file.mediaStoreId })
    }

    @Test
    fun aValue_takesMediaStoreAsItsSource_andAMissingOneIsUnknown() = runTest {
        files.files = listOf(audioFile(1, title = "Song", artist = null))

        scan()

        val metadata = songOf(1).metadata
        assertEquals("Song", metadata.title.value)
        assertEquals(FieldSource.MediaStore, metadata.title.source)
        assertNull(metadata.artist.value)
        assertEquals(FieldSource.Unknown, metadata.artist.source)
    }

    @Test
    fun aRescanOfTheSameFiles_writesNothing() = runTest {
        files.files = listOf(audioFile(1), audioFile(2))
        scan()
        index.batches.clear()

        scan()

        assertTrue(index.batches.isEmpty())
    }

    @Test
    fun aChangedFile_isUpdated_andKeepsItsId() = runTest {
        files.files = listOf(audioFile(1))
        scan()
        val id = songOf(1).id

        files.files = listOf(audioFile(1, dateModified = 200, title = "New title"))
        scan()

        assertEquals(id, songOf(1).id)
        assertEquals("New title", songOf(1).metadata.title.value)
        assertEquals(200, songOf(1).file.dateModified)
    }

    @Test
    fun aRemovedFile_isRemoved_withAnAlbumAndArtistNothingElseUses() = runTest {
        files.files = listOf(audioFile(1), audioFile(2, artist = "Other", album = "Other album"))
        scan()

        files.files = listOf(audioFile(1))
        scan()

        assertEquals(listOf(1L), index.indexed.map { it.file.mediaStoreId })
        assertEquals(listOf("Artist"), index.artists.map { it.name })
        assertEquals(listOf("Album"), index.albums.map { it.title })
    }

    @Test
    fun matching_startsWithTheMediaStoreId() = runTest {
        files.files = listOf(audioFile(1, path = "Old/", name = "old.mp3", size = 1))
        scan()
        val id = songOf(1).id

        files.files = listOf(audioFile(1, path = "New/", name = "new.mp3", size = 2))
        scan()

        assertEquals(id, songOf(1).id)
    }

    @Test
    fun matching_thenUsesThePathAndName_whenTheSystemRenumberedTheFile() = runTest {
        files.files = listOf(audioFile(1, path = "Music/", name = "a.mp3", size = 1))
        scan()
        val id = songOf(1).id

        files.files = listOf(audioFile(50, path = "Music/", name = "a.mp3", size = 2))
        scan()

        assertEquals(id, songOf(50).id)
        assertEquals(1, index.indexed.size)
    }

    @Test
    fun matching_thenUsesTheNameAndSize_whenTheFileMoved() = runTest {
        files.files = listOf(audioFile(1, path = "Old/", name = "a.mp3", size = 7))
        scan()
        val id = songOf(1).id

        files.files = listOf(audioFile(50, path = "New/", name = "a.mp3", size = 7))
        scan()

        assertEquals(id, songOf(50).id)
    }

    @Test
    fun matching_prefersThePathAndName_overTheNameAndSize() = runTest {
        files.files = listOf(
            audioFile(1, path = "A/", name = "a.mp3", size = 7),
            audioFile(2, path = "B/", name = "a.mp3", size = 7),
        )
        scan()
        val second = songOf(2).id

        files.files = listOf(audioFile(51, path = "B/", name = "a.mp3", size = 7))
        scan()

        assertEquals(second, songOf(51).id)
    }

    @Test
    fun aSongIsTakenOnce_soACopyOfAFileIsANewSong() = runTest {
        files.files = listOf(audioFile(1, path = "A/", name = "a.mp3", size = 7))
        scan()
        val original = songOf(1).id

        files.files = listOf(
            audioFile(1, path = "A/", name = "a.mp3", size = 7),
            audioFile(2, path = "B/", name = "a.mp3", size = 7),
        )
        scan()

        assertEquals(original, songOf(1).id)
        assertTrue(songOf(2).id != original)
    }

    @Test
    fun aFileWithNoMatch_isNew_andTheOldSongIsRemoved() = runTest {
        files.files = listOf(audioFile(1, path = "A/", name = "a.mp3", size = 1))
        scan()
        val old = songOf(1).id

        files.files = listOf(audioFile(2, path = "B/", name = "b.mp3", size = 2))
        scan()

        assertTrue(songOf(2).id != old)
        assertEquals(1, index.indexed.size)
    }

    @Test
    fun writes_areBatched() = runTest {
        files.files = (1L..(SCAN_BATCH_SIZE * 2 + 1)).map { audioFile(it) }

        scan()

        assertEquals(listOf(SCAN_BATCH_SIZE, SCAN_BATCH_SIZE, 1), index.batches.map { it.writes.size })
    }

    @Test
    fun removals_areBatched() = runTest {
        files.files = (1L..(SCAN_BATCH_SIZE + 1)).map { audioFile(it) }
        scan()
        index.batches.clear()

        files.files = emptyList()
        scan()

        assertEquals(listOf(SCAN_BATCH_SIZE, 1), index.batches.map { it.removals.size })
    }

    @Test
    fun oneScanRunsAtATime() = runTest {
        val hold = CompletableDeferred<Unit>()
        files.holdList = hold
        val scanner = scanner()

        scanner.start()
        runCurrent()
        scanner.start()
        runCurrent()

        assertEquals(1, files.listCount)
        assertEquals(ScanPhase.Running, scanner.phase.value)

        hold.complete(Unit)
        runCurrent()

        assertEquals(1, files.listCount)
        assertEquals(ScanPhase.Finished, scanner.phase.value)
    }

    @Test
    fun aFinishedScan_canBeFollowedByAnother() = runTest {
        val scanner = scanner()

        scanner.start()
        runCurrent()
        scanner.start()
        runCurrent()

        assertEquals(2, files.listCount)
    }

    @Test
    fun aScan_savesTheScanState_whenItFinishes() = runTest {
        val volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.volumes = volumes

        scan()

        assertEquals(volumes, index.savedScanState)
    }

    @Test
    fun aFailedListing_writesNothing_andIsNotFinished() = runTest {
        files.files = listOf(audioFile(1))
        files.listError = LibraryError.Storage(StorageError.Unavailable)
        val scanner = scanner()

        scanner.start()
        runCurrent()

        assertTrue(index.indexed.isEmpty())
        assertNull(index.savedScanState)
        assertEquals(ScanPhase.NotFinished, scanner.phase.value)
    }

    @Test
    fun aFailedWrite_doesNotSaveTheScanState() = runTest {
        files.files = listOf(audioFile(1))
        index.writeError = LibraryError.Storage(StorageError.Unavailable)

        scan()

        assertNull(index.savedScanState)
    }

    @Test
    fun albums_areGroupedByTitleAndAlbumArtist() = runTest {
        files.files = listOf(
            audioFile(1, artist = "A", album = "Same"),
            audioFile(2, artist = "A", album = "Same"),
            audioFile(3, artist = "B", album = "Same"),
        )

        scan()

        assertEquals(2, index.albums.size)
    }

    @Test
    fun aCompilation_isOneAlbum_andEachSongKeepsItsOwnArtist() = runTest {
        files.files = listOf(
            audioFile(1, artist = "A", albumArtist = "Various", album = "Hits"),
            audioFile(2, artist = "B", albumArtist = "Various", album = "Hits"),
        )

        scan()

        assertEquals(listOf("Hits"), index.albums.map { it.title })
        assertEquals(setOf("A", "B", "Various"), index.artists.map { it.name }.toSet())
        val album = index.albums.single()
        assertEquals("Various", index.artists.first { it.id == album.artistId }.name)
        val songArtists = index.songs.map { song -> index.artists.first { it.id == song.artistId }.name }
        assertEquals(setOf("A", "B"), songArtists.toSet())
    }

    @Test
    fun anArtistsNameIsNeverSplit() = runTest {
        files.files = listOf(audioFile(1, artist = "U2 & Coldplay"))

        scan()

        assertEquals(listOf("U2 & Coldplay"), index.artists.map { it.name })
    }

    @Test
    fun aMissingName_isAGroupOfItsOwn_andAlbumsAndArtistsKeepTheirIds() = runTest {
        files.files = listOf(audioFile(1, artist = null, album = null), audioFile(2, artist = null, album = null))
        scan()
        val artist = index.artists.single()
        val album = index.albums.single()
        assertNull(artist.name)
        assertNull(album.title)

        files.files = files.files + audioFile(3, artist = null, album = null)
        scan()

        assertEquals(artist, index.artists.single())
        assertEquals(album, index.albums.single())
    }
}
