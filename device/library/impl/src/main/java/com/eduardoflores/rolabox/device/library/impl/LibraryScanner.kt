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
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** How many songs one transaction of the index writes: the lists fill in by this many (ADR-024). */
internal const val SCAN_BATCH_SIZE = 300

/** How quiet the system's files have to be before a scan starts, so that copying many files is one scan. */
internal val ChangeDebounce: Duration = 2.seconds

/** Where the scans of this process have got to. */
internal sealed interface ScanPhase {
    /** No scan has started. */
    data object NotStarted : ScanPhase

    data object Running : ScanPhase

    /** A scan is running, and something changed since it started, so one more runs when it ends. */
    data object RunningDirty : ScanPhase

    /** A scan finished, so an empty index means there's no music. */
    data object Finished : ScanPhase

    /** The last scan ended in [error], so an empty index says nothing about the music. */
    data class Failed(val error: LibraryError) : ScanPhase
}

private val ScanPhase.isRunning get() = this == ScanPhase.Running || this == ScanPhase.RunningDirty

/**
 * Fills the index: pass 1 from the audio files, then pass 2 from their tags (ADR-024). It runs one scan at a time in
 * the application scope, so it finishes whatever the screens do. A scan that fails leaves what it wrote, which is
 * consistent, and says why in [phase]. The next start of the process tries again, and pass 2 goes on from the songs
 * still unread.
 */
@OptIn(FlowPreview::class)
@Singleton
internal class LibraryScanner @Inject constructor(
    private val files: AudioFilesLocalDataSource,
    private val index: LibraryIndexLocalDataSource,
    private val tags: TagsLocalDataSource,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) {
    private val following = AtomicBoolean(false)
    private val mutablePhase = MutableStateFlow<ScanPhase>(ScanPhase.NotStarted)
    val phase: StateFlow<ScanPhase> = mutablePhase.asStateFlow()

    private val mutableReadingTags = MutableStateFlow<Progress?>(null)

    /** How far pass 2 has come, counting the songs earlier runs read, or null when it isn't running. */
    val readingTags: StateFlow<Progress?> = mutableReadingTags.asStateFlow()

    /** Scans now, and then again whenever the system says its files changed, for as long as the process lives. */
    fun keepCurrent() {
        if (!following.compareAndSet(false, true)) return
        scope.launch(ioDispatcher) {
            files.observeChanges().debounce(ChangeDebounce).collect { start() }
        }
        start()
    }

    /** Starts a scan. During one, it marks the library dirty instead, and one more runs when this one ends. */
    fun start() {
        val previous = mutablePhase.getAndUpdate { if (it.isRunning) ScanPhase.RunningDirty else ScanPhase.Running }
        if (previous.isRunning) return
        scope.launch(ioDispatcher) {
            do {
                val result = scan()
                val next = mutablePhase.updateAndGet { phase ->
                    if (phase == ScanPhase.RunningDirty) {
                        ScanPhase.Running
                    } else {
                        result.fold(ifLeft = ScanPhase::Failed, ifRight = { ScanPhase.Finished })
                    }
                }
            } while (next == ScanPhase.Running)
        }
    }

    // Pass 2 goes on whatever pass 1 finds, since a song can still be unread when nothing has changed.
    private suspend fun scan(): Either<LibraryError, Unit> = either {
        val saved = index.getScanState().bind()
        val volumes = files.getVolumes().bind()
        when (scanExtent(saved, volumes)) {
            ScanExtent.Nothing -> Unit

            ScanExtent.Changes -> {
                val changes = files.getAudioFileChanges(saved).bind()
                writePlan(planChanges(changes, index.getIndexedSongs().bind())).bind()
                index.saveScanState(volumes).bind()
            }

            ScanExtent.Everything -> {
                val listing = files.getAudioFiles().bind()
                writePlan(planScan(listing.files, index.getIndexedSongs().bind())).bind()
                index.saveScanState(listing.volumes).bind()
            }
        }
        readTags().bind()
    }

    private suspend fun writePlan(plan: ScanPlan): Either<LibraryError, Unit> = either {
        plan.writes.chunked(SCAN_BATCH_SIZE).forEach { index.write(IndexBatch(writes = it)).bind() }
        plan.removals.chunked(SCAN_BATCH_SIZE).forEach { index.write(IndexBatch(removals = it)).bind() }
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
