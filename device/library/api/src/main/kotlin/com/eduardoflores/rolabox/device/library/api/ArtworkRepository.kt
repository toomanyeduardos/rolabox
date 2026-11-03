package com.eduardoflores.rolabox.device.library.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * The covers of the library, loaded from where an [ArtworkRef] says they are (ADR-026): the picture in the song's
 * tags, or else an image in its folder, or else a cover downloaded for its album (ADR-027).
 */
interface ArtworkRepository {
    /**
     * The cover of [ref], as it is now. `null` when there is none, which is a valid case, and also when the song is no
     * longer in the library. A `Left` is [LibraryError.FileUnreadable] when the system wouldn't open the file, or
     * [LibraryError.Storage].
     */
    suspend fun loadArtwork(ref: ArtworkRef): Either<LibraryError, ArtworkImage?>

    /**
     * The cover of [ref], as [loadArtwork] gives it, and again whenever it changes: when a cover is downloaded for its
     * album, or one on the phone replaces it. A `Left` ends the flow.
     *
     * What is observed is what is shown, so an album observed with no cover is the one downloaded next.
     */
    fun observeArtwork(ref: ArtworkRef): Flow<Either<LibraryError, ArtworkImage?>>

    /** Whether covers missing from the phone are downloaded. On until the user turns it off (ADR-027). */
    fun observeDownloadEnabled(): Flow<Either<LibraryError, Boolean>>

    /** Turns downloading on or off. It stays on this phone, and isn't synced. Off, no request is made. */
    suspend fun setDownloadEnabled(enabled: Boolean): Either<LibraryError, Unit>
}
