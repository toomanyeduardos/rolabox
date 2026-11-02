package com.eduardoflores.rolabox.device.library.impl

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.util.ApplicationScope
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.Progress
import com.eduardoflores.rolabox.device.library.local.api.AudioFilesLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.IndexBatch
import com.eduardoflores.rolabox.device.library.local.api.IndexedSong
import com.eduardoflores.rolabox.device.library.local.api.LibraryIndexLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.TagsLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch

/** How many songs one transaction of the index writes: the lists fill in by this many (ADR-024). */
internal const val SCAN_BATCH_SIZE = 300

/** Where the scans of this process have got to. */
internal sealed interface ScanPhase {
    /** No scan has started. */
    data object NotStarted : ScanPhase

    data object Running : ScanPhase

    /** A scan finished, so an empty index means there's no music. */
    data object Finished : ScanPhase

    /** The last scan ended in [error], so an empty index says nothing about the music. */
    data class Failed(val error: LibraryError) : ScanPhase
}

/**
 * Fills the index: pass 1 from the audio files, then pass 2 from their tags (ADR-024). It runs one scan at a time in
 * the application scope, so it finishes whatever the screens do. A scan that fails leaves what it wrote, which is
 * consistent, and says why in [phase]. The next start of the process tries again, and pass 2 goes on from the songs
 * still unread.
 */
@Singleton
internal class LibraryScanner @Inject constructor(
    private val files: AudioFilesLocalDataSource,
    private val index: LibraryIndexLocalDataSource,
    private val tags: TagsLocalDataSource,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutablePhase = MutableStateFlow<ScanPhase>(ScanPhase.NotStarted)
    val phase: StateFlow<ScanPhase> = mutablePhase.asStateFlow()

    private val mutableReadingTags = MutableStateFlow<Progress?>(null)

    /** How far pass 2 has come, counting the songs earlier runs read, or null when it isn't running. */
    val readingTags: StateFlow<Progress?> = mutableReadingTags.asStateFlow()

    /** Starts a scan, unless one is running. Noticing changes during a scan is library/07's. */
    fun start() {
        val previous = mutablePhase.getAndUpdate { ScanPhase.Running }
        if (previous == ScanPhase.Running) return
        scope.launch(ioDispatcher) {
            mutablePhase.value = scan().fold(ifLeft = ScanPhase::Failed, ifRight = { ScanPhase.Finished })
        }
    }

    private suspend fun scan(): Either<LibraryError, Unit> = either {
        val listing = files.getAudioFiles().bind()
        val plan = planScan(listing.files, index.getIndexedSongs().bind())
        plan.writes.chunked(SCAN_BATCH_SIZE).forEach { index.write(IndexBatch(writes = it)).bind() }
        plan.removals.chunked(SCAN_BATCH_SIZE).forEach { index.write(IndexBatch(removals = it)).bind() }
        index.saveScanState(listing.volumes).bind()
        readTags().bind()
    }

    private suspend fun readTags(): Either<LibraryError, Unit> = either {
        val songs = index.getIndexedSongs().bind()
        val unread = songs.filter { it.tagsReadAt != it.file.dateModified }
        if (unread.isEmpty()) return@either
        var done = songs.size - unread.size
        mutableReadingTags.value = Progress(done, songs.size)
        try {
            unread.chunked(SCAN_BATCH_SIZE).forEach { batch ->
                val read = batch.mapNotNull { song -> readTagsOf(song) }
                val writes = regroupAfterReading(index.getIndexedSongs().bind(), read)
                if (writes.isNotEmpty()) index.write(IndexBatch(writes = writes)).bind()
                done += batch.size
                mutableReadingTags.value = Progress(done, songs.size)
            }
        } finally {
            mutableReadingTags.value = null
        }
    }

    /**
     * [song] after reading its tags. Tags that make no sense won't make more next time, so the song counts as read.
     * A file that couldn't be opened might be next time, so nothing is written and the next scan tries again.
     */
    private suspend fun readTagsOf(song: IndexedSong): SongDraft? = tags.readTags(song.file).fold(
        ifLeft = { error -> if (error is LibraryError.FileUnreadable) null else song.toDraftAfterReading(tags = null) },
        ifRight = song::toDraftAfterReading,
    )
}
