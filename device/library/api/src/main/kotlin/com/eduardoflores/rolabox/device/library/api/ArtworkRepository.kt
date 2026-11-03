package com.eduardoflores.rolabox.device.library.api

import arrow.core.Either

/** The covers of the library, loaded from where an [ArtworkRef] says they are (ADR-026). */
interface ArtworkRepository {
    /**
     * The cover of [ref]: the picture in the song's tags, or else an image in its folder. `null` when there is none,
     * which is a valid case, and also when the song is no longer in the library. A `Left` is
     * [LibraryError.FileUnreadable] when the system wouldn't open the file, or [LibraryError.Storage].
     */
    suspend fun loadArtwork(ref: ArtworkRef): Either<LibraryError, ArtworkImage?>
}
