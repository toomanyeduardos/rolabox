package com.eduardoflores.rolabox.device.library.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.VolumeScanState
import com.eduardoflores.rolabox.device.library.testing.FakeAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeTagsLocalDataSource
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
    private val tags = FakeTagsLocalDataSource()

    private fun TestScope.scanner() =
        LibraryScanner(files, index, tags, backgroundScope, StandardTestDispatcher(testScheduler))

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

        // Pass 1 writes, then pass 2 does, in the same batches.
        val sizes = listOf(SCAN_BATCH_SIZE, SCAN_BATCH_SIZE, 1)
        assertEquals(sizes + sizes, index.batches.map { it.writes.size })
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
    }

    @Test
    fun aChangeDuringAScan_marksTheLibraryDirty_andOneMoreScanRuns() = runTest {
        val hold = CompletableDeferred<Unit>()
        files.holdList = hold
        val scanner = scanner()

        scanner.start()
        runCurrent()
        scanner.start()
        scanner.start()
        runCurrent()
        assertEquals(ScanPhase.RunningDirty, scanner.phase.value)

        hold.complete(Unit)
        runCurrent()

        assertEquals(2, files.listCount)
        assertEquals(ScanPhase.Finished, scanner.phase.value)
    }

    @Test
    fun manyChanges_areOneScan_onceTheyStop() = runTest {
        val scanner = scanner()
        scanner.keepCurrent()
        runCurrent()
        assertEquals(1, files.listCount)

        repeat(5) {
            files.emitChange()
            advanceTimeBy(ChangeDebounce - 1.milliseconds)
            runCurrent()
        }
        assertEquals(1, files.listCount)

        advanceTimeBy(2.milliseconds)
        runCurrent()
        assertEquals(2, files.listCount)
    }

    @Test
    fun keepingCurrentTwice_scansOnce() = runTest {
        val scanner = scanner()

        scanner.keepCurrent()
        scanner.keepCurrent()
        runCurrent()

        assertEquals(1, files.listCount)
    }

    @Test
    fun atStart_withTheSameGeneration_nothingIsReadFromMediaStore() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.files = listOf(audioFile(1))
        scan()
        index.batches.clear()

        scan()

        assertEquals(1, files.listCount)
        assertEquals(0, files.changesCount)
        assertTrue(index.batches.isEmpty())
    }

    @Test
    fun atStart_nothingChanged_stillReadsTheTagsThatWereUnread() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.files = listOf(audioFile(1))
        val uri = files.files.single().file.uri
        tags.results[uri] = LibraryError.FileUnreadable(uri).left()
        scan()
        assertNull(songOf(1).tagsReadAt)

        tags.results[uri] = FileTags(title = "From the tags").right()
        scan()

        assertEquals("From the tags", songOf(1).metadata.title.value)
    }

    @Test
    fun atStart_withALaterGeneration_onlyWhatChangedIsRead() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.files = listOf(audioFile(1), audioFile(2))
        scan()
        val kept = songOf(2)

        files.volumes = listOf(VolumeScanState("external_primary", "v1", 13))
        files.changedFiles = listOf(audioFile(1, dateModified = 200, title = "New"), audioFile(3))
        files.files = listOf(audioFile(1), audioFile(2), audioFile(3))
        scan()

        assertEquals(1, files.listCount)
        assertEquals(1, files.changesCount)
        assertEquals("New", songOf(1).metadata.title.value)
        assertEquals(kept.id, songOf(2).id)
        assertEquals(setOf(1L, 2L, 3L), index.indexed.map { it.file.mediaStoreId }.toSet())
        assertEquals(files.volumes, index.savedScanState)
    }

    @Test
    fun aDeletedFile_isFoundWithoutAnyChangedRow() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.files = listOf(audioFile(1), audioFile(2, artist = "Other", album = "Other album"))
        scan()

        files.volumes = listOf(VolumeScanState("external_primary", "v1", 13))
        files.files = listOf(audioFile(1))
        scan()

        assertEquals(listOf(1L), index.indexed.map { it.file.mediaStoreId })
        assertEquals(listOf("Album"), index.albums.map { it.title })
    }

    @Test
    fun aMovedFile_keepsItsSong_amongTheChanges() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.files = listOf(audioFile(1, path = "Old/", name = "a.mp3", size = 7))
        scan()
        val id = songOf(1).id

        val moved = audioFile(50, path = "New/", name = "a.mp3", size = 7)
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 13))
        files.changedFiles = listOf(moved)
        files.files = listOf(moved)
        scan()

        assertEquals(id, songOf(50).id)
        assertEquals(1, index.indexed.size)
    }

    @Test
    fun aCopyAmongTheChanges_isANewSong_andTheOriginalKeepsItsOwn() = runTest {
        val original = audioFile(1, path = "A/", name = "a.mp3", size = 7)
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        files.files = listOf(original)
        scan()
        val id = songOf(1).id

        val copy = audioFile(2, path = "B/", name = "a.mp3", size = 7)
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 13))
        files.changedFiles = listOf(copy)
        files.files = listOf(original, copy)
        scan()

        assertEquals(id, songOf(1).id)
        assertTrue(songOf(2).id != id)
    }

    @Test
    fun withoutGenerations_everyRowIsCompared() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", null))
        files.files = listOf(audioFile(1))
        scan()

        scan()

        assertEquals(2, files.listCount)
        assertEquals(0, files.changesCount)
    }

    @Test
    fun aNewVersionOfTheDatabase_comparesEveryRow() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        scan()

        files.volumes = listOf(VolumeScanState("external_primary", "v2", 13))
        scan()

        assertEquals(2, files.listCount)
        assertEquals(0, files.changesCount)
    }

    @Test
    fun aVolumeThatCameOrWent_comparesEveryRow() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        scan()

        files.volumes = files.volumes + VolumeScanState("sdcard-1", "v1", 3)
        scan()

        assertEquals(2, files.listCount)
    }

    @Test
    fun aGenerationThatWentBack_comparesEveryRow() = runTest {
        files.volumes = listOf(VolumeScanState("external_primary", "v1", 12))
        scan()

        files.volumes = listOf(VolumeScanState("external_primary", "v1", 3))
        scan()

        assertEquals(2, files.listCount)
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
    fun aFailedListing_writesNothing_andSaysWhy() = runTest {
        files.files = listOf(audioFile(1))
        files.listError = LibraryError.Storage(StorageError.Unavailable)
        val scanner = scanner()

        scanner.start()
        runCurrent()

        assertTrue(index.indexed.isEmpty())
        assertNull(index.savedScanState)
        assertEquals(ScanPhase.Failed(LibraryError.Storage(StorageError.Unavailable)), scanner.phase.value)
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
            audioFile(1, artist = "A", albumArtist = "A", album = "Same"),
            audioFile(2, artist = "A", albumArtist = "A", album = "Same"),
            audioFile(3, artist = "B", albumArtist = "B", album = "Same"),
        )

        scan()

        assertEquals(2, index.albums.size)
    }

    @Test
    fun aCompilation_isOneAlbum_andEachSongKeepsItsOwnArtist() = runTest {
        files.files = listOf(
            audioFile(1, artist = "A", albumArtist = "Label", album = "Hits"),
            audioFile(2, artist = "B", albumArtist = "Label", album = "Hits"),
        )

        scan()

        assertEquals(listOf("Hits"), index.albums.map { it.title })
        assertEquals(setOf("A", "B", "Label"), index.artists.map { it.name }.toSet())
        val album = index.albums.single()
        assertEquals("Label", index.artists.first { it.id == album.artistId }.name)
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

    @Test
    fun aSongRead_movesItsAlbumAsItGoes_andAnAlbumLeftEmptyIsGone() = runTest {
        files.files = listOf(audioFile(1, artist = "A", album = "Guess"))
        tags.results[files.files.single().file.uri] = FileTags(album = "Real").right()

        scan()

        assertEquals(listOf("Real"), index.albums.map { it.title })
    }

    @Test
    fun aRescan_keepsTheIdsOfAlbumsAndArtists_whenTheirKeysStay() = runTest {
        files.files = listOf(audioFile(1, artist = "A", album = "Hits"), audioFile(2, artist = "B", album = "Hits"))
        scan()
        val artists = index.artists
        val albums = index.albums

        files.files = files.files + audioFile(3, artist = "A", album = "Hits")
        scan()

        assertEquals(albums.map { it.id }, index.albums.map { it.id })
        assertEquals(artists.map { it.id }, index.artists.map { it.id })
    }
}
