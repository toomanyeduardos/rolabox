package com.eduardoflores.rolabox.device.library.impl.artwork

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkAlbum
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import com.eduardoflores.rolabox.device.library.network.api.Cover
import com.eduardoflores.rolabox.device.library.network.api.CoverNetworkDataSource
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** How a run of the download ends, which the work turns into its result. */
internal enum class DownloadOutcome {
    /** Nothing is due, or downloading isn't allowed now. */
    Done,

    /** The source couldn't be asked, or the answer couldn't be kept: the work runs again later, with a backoff. */
    Retry,
}

/**
 * The download of the covers missing from the phone (ADR-027, The work): one album at a time, the one shown first,
 * then the queued ones, until none is due. Each pick reads the albums again, so an album shown while a request is out
 * is the next one asked for, and the request out isn't cancelled. It checks that downloading is allowed before each
 * request (rule 3). Only an answer of no cover is an attempt (rule 5).
 */
internal class ArtworkDownloader @Inject constructor(
    private val albums: AlbumArtworkLocalDataSource,
    private val source: CoverNetworkDataSource,
    private val permit: DownloadPermit,
    private val wanted: WantedAlbum,
    private val clock: Clock,
) {
    suspend fun run(): DownloadOutcome {
        var outcome: DownloadOutcome? = null
        while (outcome == null) outcome = downloadNext()
        return outcome
    }

    /** Asks for the next album that is due, and keeps the answer. How the run ends, or `null` to go on. */
    private suspend fun downloadNext(): DownloadOutcome? = either {
        ensure(permit.observe().first()) { DownloadOutcome.Done }
        val all = albums.getAlbums().mapLeft { DownloadOutcome.Retry }.bind()
        val album = ensureNotNull(nextDue(all, clock.millis())) { DownloadOutcome.Done }
        val cover = source.findCover(checkNotNull(album.toQuery())).mapLeft { DownloadOutcome.Retry }.bind()
        keep(album, cover).mapLeft { DownloadOutcome.Retry }.bind()
    }.leftOrNull()

    private suspend fun keep(album: ArtworkAlbum, cover: Cover?): Either<LibraryError, Unit> {
        val now = clock.millis()
        return if (cover != null) {
            albums.saveCover(album.key, cover.sourceId, cover.bytes, now)
        } else {
            val attempts = (album.record as? ArtworkRecord.NotFound)?.attempts ?: 0
            albums.setRecord(album.key, ArtworkRecord.NotFound(attempts + 1, now))
        }
    }

    // The album shown first, then the queued albums, then the ones not found that are due again, each in the index's
    // order. The album shown waits its day or week too, if it wasn't found.
    private fun nextDue(all: List<ArtworkAlbum>, now: Long): ArtworkAlbum? {
        val due = all.filter { it.toQuery() != null && it.record.isDueAt(now) }
        val wantedKey = wanted.get()
        return due.firstOrNull { it.key == wantedKey }
            ?: due.firstOrNull { it.record == ArtworkRecord.Pending }
            ?: due.firstOrNull()
    }
}
