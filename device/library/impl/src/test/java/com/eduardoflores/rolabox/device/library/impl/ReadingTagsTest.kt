package com.eduardoflores.rolabox.device.library.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Progress
import com.eduardoflores.rolabox.device.library.local.api.FieldSource
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.testing.FakeAudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeLibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.testing.FakeTagsLocalDataSource
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
class ReadingTagsTest {
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

    private fun uriOf(id: Long) = "content://media/external/audio/media/$id"

    @Test
    fun theTags_replaceMediaStore_andMarkTheSongAsRead() = runTest {
        files.files = listOf(audioFile(1, dateModified = 50))
        tags.results[uriOf(1)] = FileTags(title = "Tagged", hasEmbeddedPicture = true).right()

        scan()

        val song = songOf(1)
        assertEquals("Tagged", song.metadata.title.value)
        assertEquals(FieldSource.Tags, song.metadata.title.source)
        assertEquals(FieldSource.MediaStore, song.metadata.artist.source)
        assertEquals(true, song.hasEmbeddedPicture)
        assertEquals(50L, song.tagsReadAt)
    }

    @Test
    fun theTags_regroupTheSong() = runTest {
        files.files = listOf(audioFile(1, artist = "Wrong", album = "Wrong"))
        tags.results[uriOf(1)] = FileTags(artist = "Right", album = "Right album").right()

        scan()

        assertEquals(listOf("Right"), index.artists.map { it.name })
        assertEquals(listOf("Right album"), index.albums.map { it.title })
    }

    @Test
    fun aRescanOfUnchangedFiles_readsNothing_andKeepsTheTags() = runTest {
        files.files = listOf(audioFile(1))
        tags.results[uriOf(1)] = FileTags(title = "Tagged").right()
        scan()
        tags.reads.clear()
        index.batches.clear()

        scan()

        assertTrue(tags.reads.isEmpty())
        assertTrue(index.batches.isEmpty())
        assertEquals("Tagged", songOf(1).metadata.title.value)
    }

    @Test
    fun aFileChanged_isReadAgain() = runTest {
        files.files = listOf(audioFile(1, dateModified = 50))
        tags.results[uriOf(1)] = FileTags(title = "Old").right()
        scan()

        files.files = listOf(audioFile(1, dateModified = 60))
        tags.results[uriOf(1)] = FileTags(title = "New").right()
        scan()

        assertEquals("New", songOf(1).metadata.title.value)
        assertEquals(60L, songOf(1).tagsReadAt)
    }

    @Test
    fun malformedTags_keepTheFallbacks_failNothing_andAreNotReadAgain() = runTest {
        files.files = listOf(audioFile(1), audioFile(2))
        tags.results[uriOf(1)] = LibraryError.TagsMalformed(uriOf(1)).left()
        tags.results[uriOf(2)] = FileTags(title = "Tagged").right()
        val scanner = scanner()

        scanner.start()
        runCurrent()

        assertEquals(ScanPhase.Finished, scanner.phase.value)
        assertEquals("Song 1", songOf(1).metadata.title.value)
        assertEquals("Tagged", songOf(2).metadata.title.value)
        tags.reads.clear()
        scan()
        assertTrue(tags.reads.isEmpty())
    }

    @Test
    fun aFileThatCantBeOpened_failsNothing_andIsTriedAgainByTheNextScan() = runTest {
        files.files = listOf(audioFile(1), audioFile(2))
        tags.results[uriOf(1)] = LibraryError.FileUnreadable(uriOf(1)).left()
        val scanner = scanner()

        scanner.start()
        runCurrent()

        assertEquals(ScanPhase.Finished, scanner.phase.value)
        assertNull(songOf(1).tagsReadAt)
        assertEquals(100L, songOf(2).tagsReadAt)
        tags.reads.clear()
        scan()
        assertEquals(listOf(uriOf(1)), tags.reads)
    }

    @Test
    fun aKilledProcess_goesOnFromTheSongsItHadntRead() = runTest {
        val songs = SCAN_BATCH_SIZE + 2
        files.files = (1L..songs).map { audioFile(it) }
        // Pass 1's writes and pass 2's first batch get through.
        index.failWritesAfter = songs + SCAN_BATCH_SIZE
        scan()
        val readBefore = index.indexed.count { it.tagsReadAt != null }
        index.failWritesAfter = null
        tags.reads.clear()

        // A new scanner, as a new process has.
        scan()

        assertEquals(SCAN_BATCH_SIZE, readBefore)
        assertEquals(2, tags.reads.size)
        assertTrue(index.indexed.all { it.tagsReadAt != null })
    }

    @Test
    fun theProgress_countsEachBatch_andEndsAsNull() = runTest {
        files.files = (1L..(SCAN_BATCH_SIZE + 1)).map { audioFile(it) }
        val hold = CompletableDeferred<Unit>()
        tags.hold = hold
        val scanner = scanner()

        scanner.start()
        runCurrent()

        assertEquals(Progress(0, SCAN_BATCH_SIZE + 1), scanner.readingTags.value)

        hold.complete(Unit)
        runCurrent()

        assertNull(scanner.readingTags.value)
    }

    @Test
    fun aWriteThatFails_endsTheScan_andSaysWhy() = runTest {
        files.files = listOf(audioFile(1))
        index.failWritesAfter = 1
        val scanner = scanner()

        scanner.start()
        runCurrent()

        assertEquals(ScanPhase.Failed(LibraryError.Storage(StorageError.Unavailable)), scanner.phase.value)
        assertNull(scanner.readingTags.value)
    }
}
