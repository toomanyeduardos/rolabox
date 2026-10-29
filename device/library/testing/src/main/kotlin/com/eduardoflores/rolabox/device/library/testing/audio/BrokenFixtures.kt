package com.eduardoflores.rolabox.device.library.testing.audio

import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent.M4a
import com.eduardoflores.rolabox.device.library.testing.audio.FixtureContent.Mp3

private val GLASS_HARBOR = Tags(
    title = "Glass Harbor",
    artist = AURORA_LANE,
    album = "Paper Lanterns",
    albumArtist = AURORA_LANE,
    track = "2/4",
    year = "2019",
    genre = "Pop",
    cover = Cover(COVER_PAPER_LANTERNS),
)

/** The broken set: one case per file, grouped by what they break. */
internal fun brokenFixtures(): List<AudioFixture> =
    untagged() + tagVersions() + encodings() + albumAndTrackNumbers() + genres() + compilation() +
        duplicates() + dumpedInOneFolder() + notWholeAudio()

// No tags at all, so everything comes from the folder and the name.
private fun untagged() = listOf(
    AudioFixture("Unknown/Track 07.mp3", Mp3(Mp3Tags.None), "no tags: mp3"),
    AudioFixture("Unknown/Recording 3.m4a", M4a(tags = null), "no tags: m4a"),
    AudioFixture("Loose/Velvet Static - Neon Rain.mp3", Mp3(Mp3Tags.None), "named Artist - Title, with no tags"),
    AudioFixture("Loose/03 Harbor Lights.mp3", Mp3(Mp3Tags.None), "named NN Title, with no tags"),
)

private fun tagVersions(): List<AudioFixture> {
    val oldRip =
        Tags(title = "Static Bloom", artist = "Velvet Static", album = "Old Rips", year = "2003", genre = "Rock")
    val versions = Tags(artist = "Tag Lab", album = "Versions", albumArtist = "Tag Lab", year = "2024", genre = "Pop")
    return listOf(
        AudioFixture(
            "Velvet Static/Old Rips/05 Static Bloom.mp3",
            Mp3(Mp3Tags.Id3v1(oldRip.copy(track = "5"))),
            "ID3v1.1 only",
        ),
        AudioFixture(
            "Velvet Static/Old Rips/06 Night Bus.mp3",
            Mp3(Mp3Tags.Id3v1(oldRip.copy(title = "Night Bus"))),
            "ID3v1.0 only, so no track number",
        ),
        AudioFixture(
            "Velvet Static/Old Rips/07 Long Title.mp3",
            Mp3(Mp3Tags.Id3v1(oldRip.copy(title = "A Title Much Longer Than Thirty Characters", track = "7"))),
            "ID3v1 only, with a title cut at its 30 characters",
        ),
        AudioFixture(
            "Tag Lab/Versions/01 - ID3v2.3.mp3",
            id3v23(versions.copy(title = "ID3v2.3", track = "1/3")),
            "ID3v2.3",
        ),
        AudioFixture(
            "Tag Lab/Versions/02 - ID3v2.4.mp3",
            id3v24(versions.copy(title = "ID3v2.4", track = "2/3")),
            "ID3v2.4",
        ),
        AudioFixture(
            "Tag Lab/Versions/03 - ID3v2.4 and ID3v1.mp3",
            Mp3(
                Mp3Tags.Id3v2(
                    Id3Version.V2_4,
                    TextEncoding.UTF_8,
                    versions.copy(title = "ID3v2.4 and ID3v1", track = "3/3"),
                    id3v1 = versions.copy(title = "The ID3v1 Title", track = "9"),
                ),
            ),
            "ID3v2.4 and ID3v1 in one file, with different values",
        ),
    )
}

private fun encodings(): List<AudioFixture> {
    val album = Tags(album = "Encodings", albumArtist = "Tag Lab", year = "2024")
    return listOf(
        AudioFixture(
            "Tag Lab/Encodings/01 - Café Brûlée.mp3",
            id3v23(album.copy(title = "Café Brûlée", artist = "Søren Ångström", track = "1/4")),
            "Latin-1 text",
        ),
        AudioFixture(
            "Tag Lab/Encodings/02 - Don't Look Back.mp3",
            id3v23(
                album.copy(title = "Don’t Look Back…", artist = "Zoë € Co", track = "2/4"),
                TextEncoding.CP1252_AS_LATIN_1,
            ),
            "Windows-1252 text in a frame that declares Latin-1: ’, … and € aren't Latin-1",
        ),
        AudioFixture(
            "Tag Lab/Encodings/03 - Tokyo Nights.mp3",
            id3v23(album.copy(title = "東京の夜", artist = "Ниночка", track = "3/4"), TextEncoding.UTF_16),
            "UTF-16 text with a byte order mark",
        ),
        AudioFixture(
            "Tag Lab/Encodings/04 - Greek.mp3",
            id3v24(album.copy(title = "Καλημέρα", artist = "Ελένη", track = "4/4")),
            "UTF-8 text",
        ),
    )
}

private fun albumAndTrackNumbers(): List<AudioFixture> {
    val album = Tags(artist = "Hollow Pines", album = "Wrong Numbers", albumArtist = "Hollow Pines", year = "2021")
    val folder = "Hollow Pines/Wrong Numbers"
    return listOf(
        AudioFixture(
            "Hollow Pines/Single/Cinder.mp3",
            id3v24(Tags(title = "Cinder", artist = "Hollow Pines", year = "2021", genre = "Rock")),
            "no album tag",
        ),
        AudioFixture("$folder/Ember.mp3", id3v24(album.copy(title = "Ember")), "no track number"),
        AudioFixture("$folder/00 - Ash.mp3", id3v24(album.copy(title = "Ash", track = "0")), "track 0"),
        AudioFixture(
            "$folder/Smoke.mp3",
            id3v24(album.copy(title = "Smoke", track = "x")),
            "a track number that isn't a number",
        ),
        AudioFixture(
            "$folder/15 - Spark.mp3",
            id3v24(album.copy(title = "Spark", track = "15/4")),
            "a track number past the album's total",
        ),
        AudioFixture("$folder/02 - Flint.mp3", id3v24(album.copy(title = "Flint", track = "2/4")), "track 2"),
        AudioFixture(
            "$folder/02 - Tinder.mp3",
            id3v24(album.copy(title = "Tinder", track = "2/4")),
            "a second track 2 in the same album",
        ),
        AudioFixture(
            "$folder/03 - Kindling.m4a",
            M4a(album.copy(title = "Kindling", track = "x")),
            "m4a: a track number that isn't a number, which trkn can only write as 0",
        ),
    )
}

private fun genres(): List<AudioFixture> {
    val album = Tags(artist = "Genre Lab", album = "Genres", albumArtist = "Genre Lab", year = "2024")
    val folder = "Genre Lab/Genres"
    return listOf(
        AudioFixture(
            "$folder/01 - Parenthesised.mp3",
            id3v23(album.copy(title = "Parenthesised", track = "1/5", genre = "(17)")),
            "genre (17), ID3v1's Rock",
        ),
        AudioFixture(
            "$folder/02 - Number.mp3",
            id3v24(album.copy(title = "Number", track = "2/5", genre = "17")),
            "genre 17, as a bare number",
        ),
        AudioFixture(
            "$folder/03 - Several.mp3",
            id3v24(album.copy(title = "Several", track = "3/5", genre = "Rock\u0000Pop\u0000Jazz")),
            "several genres, separated as ID3v2.4 separates them",
        ),
        AudioFixture(
            "$folder/04 - Text.mp3",
            id3v23(album.copy(title = "Text", track = "4/5", genre = "Dream Pop")),
            "a genre as free text",
        ),
        AudioFixture(
            "$folder/05 - Numbered M4A.m4a",
            M4a(album.copy(title = "Numbered M4A", track = "5/5", genre = "13")),
            "m4a: genre 13 (Pop), written as gnre",
        ),
    )
}

// A compilation with its own artist on each track, in both formats.
private fun compilation(): List<AudioFixture> {
    val album = Tags(
        album = "Summer Mixtape",
        albumArtist = "Various Artists",
        year = "2023",
        compilation = true,
        cover = Cover(COVER_MIXTAPE),
    )
    val tracks = listOf(
        AURORA_LANE to "Sunburn",
        LOW_TIDES to "High Water",
        "Velvet Static" to "Heat Haze",
        MIREILLE_OKAFOR to "Late Swim",
    )
    return tracks.mapIndexed { index, (artist, title) ->
        val number = index + 1
        val tags = album.copy(title = title, artist = artist, track = "$number/${tracks.size}")
        val m4a = number % 2 == 0
        AudioFixture(
            "Various Artists/Summer Mixtape/0$number - $artist - $title.${if (m4a) "m4a" else "mp3"}",
            if (m4a) M4a(tags) else id3v24(tags),
            "a compilation, with its own artist on each track",
        )
    }
}

// Two albums called Greatest Hits, by different artists, and the same song in two places.
private fun duplicates() = listOf(LOW_TIDES to "Undertow", MIREILLE_OKAFOR to "Front Room").map { (artist, title) ->
    val tags = Tags(
        title = title,
        artist = artist,
        album = "Greatest Hits",
        albumArtist = artist,
        track = "1/1",
        year = "2025",
        cover = Cover(COVER_GREATEST_HITS),
    )
    AudioFixture(
        "$artist/Greatest Hits/01 - $title.mp3",
        id3v24(tags),
        "an album called Greatest Hits, as another artist's is",
    )
} + listOf(
    // The same bytes as the clean set's Glass Harbor, twice.
    AudioFixture("$AURORA_LANE/Paper Lanterns/02 - Glass Harbor.mp3", id3v24(GLASS_HARBOR), "a song also in Downloads"),
    AudioFixture("Downloads/Glass Harbor.mp3", id3v24(GLASS_HARBOR), "the same song in a second place"),
)

private fun dumpedInOneFolder() = listOf(
    Triple("track1.mp3", "Lighthouse", Tags(artist = AURORA_LANE, album = "Northbound")),
    Triple("AUD-20190311-WA0004.mp3", "Tidepool", Tags(artist = LOW_TIDES, album = "Saltwater Radio")),
    Triple("new song (1).mp3", "Hallway", Tags(artist = MIREILLE_OKAFOR, album = "Two Rooms")),
    Triple("final_FINAL_v2.m4a", "Ember Glow", Tags(artist = "Hollow Pines", album = "Wrong Numbers")),
).map { (name, title, tags) ->
    val content = if (name.endsWith(".m4a")) M4a(tags.copy(title = title)) else id3v24(tags.copy(title = title))
    AudioFixture("Dump/$name", content, "one of several songs dumped in one folder")
}

private fun notWholeAudio(): List<AudioFixture> {
    val interrupted = GLASS_HARBOR.copy(title = "Interrupted Download")
    return listOf(
        AudioFixture(
            "Unknown/not really audio.mp3",
            FixtureContent.Text("This is a text file with an .mp3 extension.\n"),
            "a text file named .mp3",
        ),
        AudioFixture(
            "Unknown/Interrupted Download.mp3",
            FixtureContent.Truncated(id3v24(interrupted), fraction = TRUNCATED_MP3_KEEPS),
            "a truncated mp3: its tag is whole, its audio is cut",
        ),
        AudioFixture(
            "Unknown/Interrupted Download.m4a",
            FixtureContent.Truncated(M4a(interrupted, AacStream(moovFirst = false)), fraction = TRUNCATED_M4A_KEEPS),
            "a truncated m4a: its index, at the end, is gone",
        ),
    )
}

private const val TRUNCATED_MP3_KEEPS = 0.4
private const val TRUNCATED_M4A_KEEPS = 0.5
