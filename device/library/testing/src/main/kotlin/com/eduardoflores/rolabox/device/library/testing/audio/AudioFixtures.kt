package com.eduardoflores.rolabox.device.library.testing.audio

import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent.M4a
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent.Mp3

/**
 * The library's audio fixtures (ADR-024): generated MP3 and M4A files of silence, with clean and
 * broken tags. Every artist, album and title is made up.
 *
 * - [clean]: full tags, laid out as `Artist/Album/NN - Title.ext`, with an album on two discs.
 * - [broken]: the tags and layouts real libraries have, one case per file.
 * - [formats]: the same tags over the encodings a phone meets: bitrates, sample rates, VBR, mono, and
 *   M4A with its index first and last.
 */
object AudioFixtures {
    val clean: AudioFixtureSet = AudioFixtureSet("clean", cleanFixtures())

    val broken: AudioFixtureSet = AudioFixtureSet("broken", brokenFixtures())

    val formats: AudioFixtureSet = AudioFixtureSet("formats", formatFixtures())

    val sets: List<AudioFixtureSet> = listOf(clean, broken, formats)

    fun named(name: String): AudioFixtureSet? = sets.firstOrNull { it.name == name }
}

internal const val AURORA_LANE = "Aurora Lane"
internal const val LOW_TIDES = "The Low Tides"
internal const val MIREILLE_OKAFOR = "Mireille Okafor"

internal const val COVER_PAPER_LANTERNS = 0xE8A33D
private const val COVER_NORTHBOUND = 0x3D7EE8
private const val COVER_SALTWATER_RADIO = 0x2E8B57
private const val COVER_TWO_ROOMS = 0x8B2E6A
internal const val COVER_GREATEST_HITS = 0xC0392B
internal const val COVER_MIXTAPE = 0xF1C40F

private enum class Format(val extension: String) {
    MP3("mp3"),
    M4A("m4a"),
}

private fun cleanFixtures(): List<AudioFixture> = album(
    AURORA_LANE,
    "Paper Lanterns",
    year = "2019",
    genre = "Pop",
    cover = Cover(COVER_PAPER_LANTERNS),
    format = Format.MP3,
    titles = listOf("Morning Signal", "Glass Harbor", "Small Hours", "Paper Lanterns"),
) +
    album(
        AURORA_LANE,
        "Northbound",
        year = "2022",
        genre = "Pop",
        cover = Cover(COVER_NORTHBOUND),
        format = Format.M4A,
        titles = listOf("Northbound", "Cold Light", "Long Way Home"),
    ) +
    album(
        LOW_TIDES,
        "Saltwater Radio",
        year = "2015",
        genre = "Rock",
        cover = Cover(COVER_SALTWATER_RADIO),
        format = Format.MP3,
        titles = listOf("Undertow", "Breakwater", "Static on the Line", "Last Ferry"),
        encoding = TextEncoding.UTF_16,
        version = Id3Version.V2_3,
    ) +
    album(
        MIREILLE_OKAFOR,
        "Two Rooms",
        year = "2020",
        genre = "Jazz",
        cover = Cover(COVER_TWO_ROOMS),
        format = Format.M4A,
        titles = listOf("Front Room", "Hallway", "Lamp Light"),
        disc = 1 to 2,
    ) +
    album(
        MIREILLE_OKAFOR,
        "Two Rooms",
        year = "2020",
        genre = "Jazz",
        cover = Cover(COVER_TWO_ROOMS),
        format = Format.M4A,
        titles = listOf("Back Room", "Stairwell", "Closing Time"),
        disc = 2 to 2,
    )

/** An album's songs, numbered from 1, at `Artist/Album/NN - Title.ext` (or `D-NN - Title.ext` on several discs). */
@Suppress("LongParameterList") // One parameter per tag of an album.
private fun album(
    artist: String,
    album: String,
    year: String,
    genre: String,
    cover: Cover,
    format: Format,
    titles: List<String>,
    disc: Pair<Int, Int>? = null,
    encoding: TextEncoding = TextEncoding.UTF_8,
    version: Id3Version = Id3Version.V2_4,
): List<AudioFixture> = titles.mapIndexed { index, title ->
    val number = index + 1
    val tags = Tags(
        title = title,
        artist = artist,
        album = album,
        albumArtist = artist,
        track = "$number/${titles.size}",
        disc = disc?.let { (current, total) -> "$current/$total" },
        year = year,
        genre = genre,
        cover = cover,
    )
    val prefix = disc?.let { "${it.first}-" }.orEmpty()
    AudioFixture(
        path = "$artist/$album/$prefix${number.toString().padStart(2, '0')} - $title.${format.extension}",
        content = when (format) {
            Format.MP3 -> Mp3(Mp3Tags.Id3v2(version, encoding, tags))
            Format.M4A -> M4a(tags)
        },
        case = "clean: ${format.extension}, full tags",
    )
}

internal fun id3v24(tags: Tags, stream: Mp3Stream = Mp3Stream()) =
    Mp3(Mp3Tags.Id3v2(Id3Version.V2_4, TextEncoding.UTF_8, tags), stream)

internal fun id3v23(tags: Tags, encoding: TextEncoding = TextEncoding.LATIN_1) =
    Mp3(Mp3Tags.Id3v2(Id3Version.V2_3, encoding, tags))

private const val FOUR_MINUTES_MS = 240_000

private fun formatFixtures(): List<AudioFixture> {
    val mp3s = listOf(
        "MPEG-1 128 kbps 44.1 kHz stereo" to Mp3Stream(),
        "MPEG-1 320 kbps 48 kHz stereo" to Mp3Stream(sampleRate = 48_000, bitratesKbps = listOf(320)),
        "MPEG-1 VBR 44.1 kHz stereo" to Mp3Stream(bitratesKbps = listOf(128, 192, 96, 160)),
        "MPEG-1 64 kbps 32 kHz mono" to Mp3Stream(sampleRate = 32_000, channels = 1, bitratesKbps = listOf(64)),
        "MPEG-2 64 kbps 22.05 kHz stereo" to
            Mp3Stream(MpegVersion.MPEG_2, sampleRate = 22_050, bitratesKbps = listOf(64)),
        "MPEG-1 128 kbps 44.1 kHz, 4 minutes" to Mp3Stream(durationMs = FOUR_MINUTES_MS),
    ).map { (name, stream) -> name to { tags: Tags -> id3v24(tags, stream) } }
    val m4as = listOf(
        "AAC 44.1 kHz stereo, index first" to AacStream(),
        "AAC 48 kHz mono, index last" to AacStream(sampleRate = 48_000, channels = 1, moovFirst = false),
    ).map { (name, stream) -> name to { tags: Tags -> M4a(tags, stream) } }

    val all = mp3s + m4as
    val album = Tags(artist = "Format Lab", album = "Formats", albumArtist = "Format Lab", year = "2024")
    return all.mapIndexed { index, (name, build) ->
        val number = index + 1
        val content = build(album.copy(title = name, track = "$number/${all.size}"))
        val extension = if (content is Mp3) "mp3" else "m4a"
        AudioFixture(
            path = "Format Lab/Formats/${number.toString().padStart(2, '0')} - $name.$extension",
            content = content,
            case = "format: $name",
        )
    }
}
