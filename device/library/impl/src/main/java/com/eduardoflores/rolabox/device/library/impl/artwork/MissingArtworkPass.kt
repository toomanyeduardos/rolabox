package com.eduardoflores.rolabox.device.library.impl.artwork

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.AlbumArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkLocalDataSource
import com.eduardoflores.rolabox.device.library.local.api.ArtworkRecord
import java.time.Clock
import javax.inject.Inject

/**
 * The pass after a scan (ADR-027, When): each album with no record, that the source can be asked for, is looked at
 * once, through the thumbnail of the song that stands for it, one song per album. One with a cover is on the phone,
 * and one without is queued.
 */
internal class MissingArtworkPass @Inject constructor(
    private val albums: AlbumArtworkLocalDataSource,
    private val artwork: ArtworkLocalDataSource,
    private val clock: Clock,
) {
    /** Records what it finds, and says whether any album is due a request now. */
    suspend fun run(): Either<LibraryError, Boolean> = either {
        val now = clock.millis()
        var due = false
        albums.getAlbums().bind().filter { it.toQuery() != null }.forEach { album ->
            when (val record = album.record) {
                null -> artwork.loadArtwork(album.artworkSongUri).fold(
                    // A file the system won't open is looked at again by the next scan's pass.
                    ifLeft = { },
                    ifRight = { cover ->
                        val found = if (cover != null) ArtworkRecord.OnPhone else ArtworkRecord.Pending
                        albums.setRecord(album.key, found).bind()
                        due = due || found == ArtworkRecord.Pending
                    },
                )

                else -> due = due || record.isDueAt(now)
            }
        }
        due
    }
}
