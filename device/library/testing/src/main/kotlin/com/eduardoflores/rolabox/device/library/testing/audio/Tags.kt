package com.eduardoflores.rolabox.device.library.testing.audio

/**
 * The tags a fixture is written with, exactly as written: a null field isn't written at all, and the
 * number and genre fields are the raw text, so a broken value (`"x"`, `"(17)"`) can be written too.
 * A [genre] with several values separates them with `\u0000`, as ID3v2.4 does.
 */
data class Tags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val track: String? = null,
    val disc: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val compilation: Boolean = false,
    val cover: Cover? = null,
)

/** A front cover: a small square PNG of one [rgb] color, so two albums' covers tell apart. */
data class Cover(val rgb: Int) {
    fun png(): ByteArray = Png.solid(rgb)
}

/** How an MP3 is tagged. */
sealed interface Mp3Tags {
    /** No tag at all: the file is only audio frames. */
    data object None : Mp3Tags

    /** An ID3v1 tag at the end of the file, and nothing at its start. ID3v1.1 when [Tags.track] is a number. */
    data class Id3v1(val tags: Tags) : Mp3Tags

    /** An ID3v2 tag at the start of the file, with every text frame in [encoding], and optionally an ID3v1 tag too. */
    data class Id3v2(val version: Id3Version, val encoding: TextEncoding, val tags: Tags, val id3v1: Tags? = null) :
        Mp3Tags {
        init {
            require(encoding != TextEncoding.UTF_8 || version == Id3Version.V2_4) { "ID3v2.3 has no UTF-8" }
        }
    }
}

@Suppress("MagicNumber") // Each version's major number, as its header writes it.
enum class Id3Version(val major: Int) {
    V2_3(3),
    V2_4(4),
}

/** The text encoding of an ID3v2 frame, as the frame declares it. */
enum class TextEncoding {
    LATIN_1,

    /** Windows-1252 bytes in a frame that declares Latin-1, as many old Windows taggers wrote them. */
    CP1252_AS_LATIN_1,

    /** UTF-16 with a byte order mark (little-endian), the only Unicode encoding ID3v2.3 has. */
    UTF_16,

    /** UTF-8, which only ID3v2.4 has. */
    UTF_8,
}
