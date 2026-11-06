package com.eduardoflores.rolabox.device.library.network.impl

import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery

/** MusicBrainz's own id of Various Artists, the artist of its compilations. */
internal const val VARIOUS_ARTISTS_ID = "89ad4ac3-39f7-470e-963a-56509c546377"

/** The lowest score of MusicBrainz's search that a match can have (ADR-027, How a match is judged). */
internal const val MIN_SCORE = 95

/**
 * Pass 1 (ADR-027, How a match is judged): the release group of [answer] that is [album], or `null` when none is, or
 * more than one is. Its title must be equal to the album's, after [comparedTitle] on both sides, by an artist that
 * matches, with a score of at least [MIN_SCORE].
 */
internal fun matchOf(answer: List<ReleaseGroup>, album: CoverQuery): ReleaseGroup? =
    equalsOf(answer, album).singleOrNull()

/** The release groups of [answer] that pass 1 finds equal to [album]: one is a match, several are ambiguous. */
internal fun equalsOf(answer: List<ReleaseGroup>, album: CoverQuery): List<ReleaseGroup> {
    val title = comparedTitle(album.title)
    return answer.filter { it.score >= MIN_SCORE && comparedTitle(it.title) == title && it.isBy(album) }
}

/**
 * Pass 2's candidates (ADR-027, How a match is judged): the release groups of [answer] whose first part of the title
 * is the album's, after [comparedFirstPart] on both sides, by an artist that matches, with a score of at least
 * [MIN_SCORE]. Each once, in the answer's order.
 */
internal fun candidatesOf(answer: List<ReleaseGroup>, album: CoverQuery): List<ReleaseGroup> {
    val firstPart = comparedFirstPart(album.title)
    return answer
        .filter { it.score >= MIN_SCORE && comparedFirstPart(it.title) == firstPart && it.isBy(album) }
        .distinctBy { it.id }
}

/**
 * Whether the release group is by the album's artist: its credit as one name, or its first artist, is the album's
 * artist or the artist of one of its songs. A Various Artists album is MusicBrainz's Various Artists.
 */
private fun ReleaseGroup.isBy(album: CoverQuery): Boolean = when (val artist = album.artist) {
    CoverArtist.Various -> credits.any { it.artistId == VARIOUS_ARTISTS_ID }

    is CoverArtist.Named -> {
        val wanted = (listOf(artist.name) + album.songArtists).map(::normalizedArtist).toSet()
        val names = listOfNotNull(
            credits.joinToString("") { it.name + it.joinPhrase },
            credits.firstOrNull()?.name,
            credits.firstOrNull()?.artistName,
        )
        names.any { normalizedArtist(it) in wanted }
    }
}

/**
 * The search for [album], in the query syntax of MusicBrainz: [title] as a phrase, the album's title
 * [withoutStoreNotes] unless another is given, and its artist as one, or the id of Various Artists.
 */
internal fun searchQueryOf(album: CoverQuery, title: String = withoutStoreNotes(album.title)): String {
    val artist = when (val artist = album.artist) {
        CoverArtist.Various -> "arid:$VARIOUS_ARTISTS_ID"
        is CoverArtist.Named -> "artist:${phrase(artist.name)}"
    }
    return "releasegroup:${phrase(title)} AND $artist"
}

/** The search of a release group's releases, for their track counts. */
internal fun releasesQueryOf(group: ReleaseGroup): String = "rgid:${group.id}"

private fun phrase(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
