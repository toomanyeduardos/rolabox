@file:Suppress("MagicNumber") // ID3v1's genre numbers: the numbers are the format.

package com.eduardoflores.rolabox.device.library.local.impl.tags

private const val NULL = '\u0000'

/**
 * ID3v1's genres by number: the standard's 0 to 79, and Winamp's extension to 191, which every tagger since has
 * written. 255 and any other number are no genre. MP4's `gnre` is the same list, counted from one.
 */
private val ID3V1_GENRES = listOf(
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
    "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
    "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal",
    "Jazz+Funk", "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel",
    "Noise", "Alternative Rock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop",
    "Instrumental Rock", "Ethnic", "Gothic", "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance",
    "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta", "Top 40", "Christian Rap", "Pop/Funk", "Jungle",
    "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes", "Trailer", "Lo-Fi", "Tribal",
    "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock",
    // Winamp's extension.
    "Folk", "Folk-Rock", "National Folk", "Swing", "Fast Fusion", "Bebop", "Latin", "Revival", "Celtic",
    "Bluegrass", "Avantgarde", "Gothic Rock", "Progressive Rock", "Psychedelic Rock", "Symphonic Rock", "Slow Rock",
    "Big Band", "Chorus", "Easy Listening", "Acoustic", "Humour", "Speech", "Chanson", "Opera", "Chamber Music",
    "Sonata", "Symphony", "Booty Bass", "Primus", "Porn Groove", "Satire", "Slow Jam", "Club", "Tango", "Samba",
    "Folklore", "Ballad", "Power Ballad", "Rhythmic Soul", "Freestyle", "Duet", "Punk Rock", "Drum Solo",
    "A Cappella", "Euro-House", "Dance Hall", "Goa", "Drum & Bass", "Club-House", "Hardcore Techno", "Terror",
    "Indie", "BritPop",
    // 133: Winamp's name for it is a slur, so the name later taggers gave it.
    "Afro-Punk",
    "Polsk Punk", "Beat", "Christian Gangsta Rap", "Heavy Metal", "Black Metal", "Crossover",
    "Contemporary Christian", "Christian Rock", "Merengue", "Salsa", "Thrash Metal", "Anime", "Jpop", "Synthpop",
    "Abstract", "Art Rock", "Baroque", "Bhangra", "Big Beat", "Breakbeat", "Chillout", "Downtempo", "Dub", "EBM",
    "Eclectic", "Electro", "Electroclash", "Emo", "Experimental", "Garage", "Global", "IDM", "Illbient",
    "Industro-Goth", "Jam Band", "Krautrock", "Leftfield", "Lounge", "Math Rock", "New Romantic", "Nu-Breakz",
    "Post-Punk", "Post-Rock", "Psytrance", "Shoegaze", "Space Rock", "Trop Rock", "World Music", "Neoclassical",
    "Audiobook", "Audio Theatre", "Neue Deutsche Welle", "Podcast", "Indie Rock", "G-Funk", "Dubstep", "Garage Rock",
    "Psybient",
)

/** The name of ID3v1's genre [number], or null for 255 and any number past the list. */
internal fun id3v1Genre(number: Int): String? = ID3V1_GENRES.getOrNull(number)

/**
 * The genres a genre text holds, in its order and each once (ADR-024, Genre). Only the standard forms hold several:
 * ID3v2.4's values separated by nulls, and ID3v2.3's references, `(17)` or `(17)(13)`, with `(RX)` and `(CR)` for
 * Remix and Cover, and maybe a name after them. A bare number is ID3v1's too. Any other text is one genre, so
 * "Rock; Pop" and "R&B/Soul" aren't split. A number that names no genre is none.
 */
internal fun genresOf(text: String?): List<String> = text.orEmpty()
    .split(NULL)
    .flatMap(::genresOfOneValue)
    .distinctBy { it.lowercase() }

// ID3v2.3's reference at the start of a genre: ID3v1's number, Remix or Cover. A parenthesis that isn't one, as in
// "(Live) Rock", is part of a name.
private val LEADING_REFERENCE = Regex("""^\((\d+|RX|CR)\)""")

private fun genresOfOneValue(value: String): List<String> {
    val names = mutableListOf<String>()
    var rest = value.cleanGenre()
    var reference = LEADING_REFERENCE.find(rest)
    while (reference != null) {
        referenceName(reference.groupValues[1])?.let(names::add)
        rest = rest.substring(reference.range.last + 1).cleanGenre()
        reference = LEADING_REFERENCE.find(rest)
    }
    // "((" escapes a name that starts with a parenthesis.
    val name = if (rest.startsWith("((")) rest.substring(1) else rest
    val number = name.toIntOrNull()
    when {
        number != null -> id3v1Genre(number)?.let(names::add)
        name.isNotEmpty() -> names += name
    }
    return names
}

private fun referenceName(reference: String): String? = when (reference) {
    "RX" -> "Remix"
    "CR" -> "Cover"
    else -> reference.toIntOrNull()?.let(::id3v1Genre)
}

private fun String.cleanGenre(): String = cleanTagText().orEmpty()
