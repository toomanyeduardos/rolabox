package com.eduardoflores.rolabox.device.library.network.api

import arrow.core.Either

/**
 * Where the covers missing from the phone are found online (ADR-027). Only the album's title and its artist are sent.
 */
interface CoverNetworkDataSource {
    /**
     * The cover of [album], or `null` when the source answered and has none: no album matched, more than one did, or
     * the one that matched has no front image. Only that answer counts as an attempt. A `Left` is a source that
     * couldn't be asked, which says nothing about the album.
     */
    suspend fun findCover(album: CoverQuery): Either<CoverSourceError, Cover?>
}

/** An album, as the source is asked for it. [title] is never blank, and an album with no title isn't asked for. */
data class CoverQuery(val title: String, val artist: CoverArtist)

/** Who an album is by, as the source is asked for it. The unknown artist is never asked for. */
sealed interface CoverArtist {
    data class Named(val name: String) : CoverArtist

    data object Various : CoverArtist
}

/** A cover found online: the image as the source served it, a JPEG, and the source's id of the album it is for. */
class Cover(val sourceId: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is Cover && sourceId == other.sourceId && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * sourceId.hashCode() + bytes.contentHashCode()

    override fun toString(): String = "Cover($sourceId, ${bytes.size} bytes)"
}

/** Why the source couldn't be asked (ADR-007). None of these is an attempt: the album is tried again later. */
sealed interface CoverSourceError {
    /** No network, a connection that failed, or a timeout. */
    data object Unreachable : CoverSourceError

    /** The source answered with [code], an error of its own, `503` included, which is a request to slow down. */
    data class Server(val code: Int) : CoverSourceError

    /** The source answered with something that can't be read. */
    data object Malformed : CoverSourceError
}
