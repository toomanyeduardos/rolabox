package com.eduardoflores.rolabox.device.library.network.impl

import java.text.Normalizer
import java.util.Locale

// How a match reads the album's names and the release group's (ADR-027, How a match is judged).

private val Punctuation = Regex("[\\p{P}\\p{S}]+")
private val Whitespace = Regex("\\s+")
private val CombiningMarks = Regex("\\p{M}+")

// The last word of a store's edition note, before its closing bracket if it has one (ADR-027, How a match is judged).
private val StoreNoteEnd =
    Regex("\\b(version|edition|remaster(ed)?(\\s+\\d{4})?|ep|single)\\s*[)\\]]?\\s*$", RegexOption.IGNORE_CASE)
private val NoteStarts = listOf("(", "[", " - ")

private val FirstPartEnds = listOf("(", "[", ":", " - ")

/** A title as pass 1 compares it: [withoutStoreNotes], then [normalized]. */
internal fun comparedTitle(title: String): String = normalized(withoutStoreNotes(title))

/** A title as pass 2 compares it: [firstPartOf], then [normalized]. */
internal fun comparedFirstPart(title: String): String = normalized(firstPartOf(title))

/**
 * The first part of [title] (ADR-027, How a match is judged, pass 2): what comes before its first `(`, `[`, `:` or
 * ` - `, once its store's notes are dropped, or the whole of it when nothing would be left before one.
 */
internal fun firstPartOf(title: String): String {
    val whole = withoutStoreNotes(title)
    val end = FirstPartEnds.map { whole.indexOf(it) }.filter { it > 0 }.minOrNull() ?: return whole
    return whole.substring(0, end).trimEnd().ifEmpty { whole }
}

/** The words of a title as the ranking counts them: [withoutStoreNotes], then [normalized]. */
internal fun wordsOf(title: String): Set<String> = comparedTitle(title).split(' ').filter { it.isNotEmpty() }.toSet()

/**
 * [title] without a store's edition notes at its end (ADR-027, How a match is judged): from its last `(`, `[` or
 * ` - `, a note whose last word is Version, Edition, Remaster or Remastered (with a year or not), EP or Single. They
 * name an edition of the same album, which MusicBrainz's release group merges. Dropped until none is left, and never
 * down to nothing. A note that names other music, "(Live)" or "(Acoustic)", is part of the title.
 */
internal fun withoutStoreNotes(title: String): String {
    var rest = title.trim()
    while (true) {
        val start = NoteStarts.maxOf { rest.lastIndexOf(it) }
        val kept = if (start > 0) rest.substring(0, start).trimEnd() else ""
        if (kept.isEmpty() || !StoreNoteEnd.containsMatchIn(rest.substring(start))) return rest
        rest = kept
    }
}

/**
 * A name as a match compares it: Unicode NFKC, accents folded, lower case, punctuation dropped and whitespace
 * collapsed.
 */
internal fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .let { Normalizer.normalize(it, Normalizer.Form.NFD).replace(CombiningMarks, "") }
    .lowercase(Locale.ROOT)
    .replace(Punctuation, " ")
    .replace(Whitespace, " ")
    .trim()

/** An artist's name as a match compares it: [normalized], and without a leading "The". */
internal fun normalizedArtist(name: String): String = normalized(name).removePrefix("the ")
