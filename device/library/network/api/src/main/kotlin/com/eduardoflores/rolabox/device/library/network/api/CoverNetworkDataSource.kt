package com.eduardoflores.rolabox.device.library.network.api

import arrow.core.Either

/**
 * Where the covers missing from the phone are found online (ADR-027). Only the album's title and its artist are sent,
 * and the source's own ids: the rest of a [CoverQuery] is read on the phone.
 */
interface CoverNetworkDataSource {
    /**
     * The cover of [album], or `null` when the source answered and has none: no album matched, or the ones that did
     * have no front image. Only that answer counts as an attempt. A `Left` is a source that couldn't be asked, which
     * says nothing about the album.
     */
    suspend fun findCover(album: CoverQuery): Either<CoverSourceError, Cover?>
}

/**
 * An album, as the source is asked for it and its answer is judged (ADR-027, How a match is judged). [title] is never
 * blank, and an album with no title isn't asked for. [songArtists] are the artists of its songs, [highestTrackNumber]
 * the highest of their track numbers, and [year] the one year all of its songs that have one share: `null` when the
 * index has none, or the songs don't agree.
 */
data class CoverQuery(
    val title: String,
    val artist: CoverArtist,
    val songArtists: List<String> = emptyList(),
    val highestTrackNumber: Int? = null,
    val year: Int? = null,
)

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
