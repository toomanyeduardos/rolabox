package com.eduardoflores.rolabox.device.library.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.api.LibraryError

/** The system's thumbnail of a song, which is its cover (ADR-026). */
interface ArtworkLocalDataSource {
    /**
     * The cover of the file at [uri], at most [ARTWORK_SIZE] pixels on its longest side: the picture embedded in its
     * tags, or else the best image in its folder, as the system picks them. `null` when there is none. A `Left` is
     * [LibraryError.FileUnreadable].
     */
    suspend fun loadArtwork(uri: String): Either<LibraryError, ArtworkImage?>

    companion object {
        /** Large enough for Now Playing's cover on a phone's display, and for the notification. */
        const val ARTWORK_SIZE = 720
    }
}
