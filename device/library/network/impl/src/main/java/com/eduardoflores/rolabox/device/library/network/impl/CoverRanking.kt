package com.eduardoflores.rolabox.device.library.network.impl

import com.eduardoflores.rolabox.device.library.network.api.CoverQuery

/** The most candidates of pass 2 asked for their track counts, and for their cover (ADR-027, How a match is judged). */
internal const val MAX_CANDIDATES = 3

private const val ALBUM = "Album"
private const val LIVE = "Live"
private const val NO_DATE = "9999"
private const val YEAR_LENGTH = 4

/** What, after an album's first part, says it is a live recording (ADR-027, hint 2). */
private val LiveWords = setOf("live", "concert", "unplugged")

/**
 * The candidates whose track counts the ranking needs (ADR-027, hint 3): the ones hints 1 and 2 leave first and equal,
 * at most [MAX_CANDIDATES] of them, and none when the album has no track numbers or only one candidate is left.
 */
internal fun needingTrackCounts(candidates: List<ReleaseGroup>, album: CoverQuery): List<ReleaseGroup> {
    val best = candidates.minOfOrNull { closeness(it, album) }
    val tied = candidates.filter { closeness(it, album) == best }
    return if (album.highestTrackNumber == null || tied.size < 2) emptyList() else tied.take(MAX_CANDIDATES)
}

/**
 * Pass 2's [candidates], the closest first (ADR-027, How a match is judged): by the full title, then live when the
 * album says so, then enough tracks by [trackCounts] (each release group's, by its id), then the album's year, then
 * an album, then the earliest. Each hint decides only between the ones the hints before it left equal, and the
 * answer's order between the ones none tells apart.
 */
internal fun ranked(
    candidates: List<ReleaseGroup>,
    album: CoverQuery,
    trackCounts: Map<String, List<Int>>,
): List<ReleaseGroup> = candidates.sortedWith(
    compareBy<ReleaseGroup>(
        { closeness(it, album) },
        { tracksHint(it, album, trackCounts) },
        { yearHint(it, album) },
        { if (it.primaryType == ALBUM) 0 else 1 },
        { it.firstReleaseDate ?: NO_DATE },
    ),
)

// Hints 1 and 2, which need nothing but the answer: the number of words in one full title and not in the other, then,
// when the album says it is live, whether the release group isn't. Lower is closer.
private fun closeness(group: ReleaseGroup, album: CoverQuery): Int {
    val words = wordsOf(album.title)
    val difference = (words - wordsOf(group.title)).size + (wordsOf(group.title) - words).size
    val notLive = if (album.saysLive() && LIVE !in group.secondaryTypes) 1 else 0
    // The difference decides first: a live mismatch only orders release groups of equal titles.
    return difference * 2 + notLive
}

private fun CoverQuery.saysLive(): Boolean = (wordsOf(title) - wordsOf(firstPartOf(title))).any { it in LiveWords }

// Hint 3: a release of at least as many tracks as the album's highest track number. Skipped with no track numbers.
private fun tracksHint(group: ReleaseGroup, album: CoverQuery, trackCounts: Map<String, List<Int>>): Int {
    val highest = album.highestTrackNumber ?: return 0
    return if (trackCounts[group.id].orEmpty().any { it >= highest }) 0 else 1
}

// Hint 4: first released in the album's year. Skipped with no single year, and equal when no candidate has it.
private fun yearHint(group: ReleaseGroup, album: CoverQuery): Int {
    val year = album.year ?: return 0
    return if (group.firstReleaseDate?.take(YEAR_LENGTH)?.toIntOrNull() == year) 0 else 1
}
