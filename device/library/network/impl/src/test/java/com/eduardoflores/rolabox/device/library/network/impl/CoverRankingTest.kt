package com.eduardoflores.rolabox.device.library.network.impl

import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pass 2 of the match (ADR-027, How a match is judged): its candidates, and each hint of their ranking. */
class CoverRankingTest {
    private val artist = CoverArtist.Named("Band")
    private val credit = listOf(Credit("Band", "", "band", "Band"))

    private fun group(
        id: String,
        title: String,
        type: String? = "Album",
        secondary: List<String> = emptyList(),
        date: String? = null,
        score: Int = 100,
        credits: List<Credit> = credit,
    ) = ReleaseGroup(id, score, title, credits, type, secondary, date)

    private fun rankedIds(album: CoverQuery, vararg groups: ReleaseGroup, counts: Map<String, List<Int>> = emptyMap()) =
        ranked(groups.toList(), album, counts).map { it.id }

    @Test
    fun theFirstPart_isWhatComesBeforeTheFirstBracketColonOrDash() {
        val parts = mapOf(
            "Skin and Bones (Live)" to "Skin and Bones",
            "Experience Hendrix - The Best of Jimi Hendrix" to "Experience Hendrix",
            "Experience Hendrix: The Best of Jimi Hendrix" to "Experience Hendrix",
            "Rumours [Live in Concert]" to "Rumours",
            "Live 2012" to "Live 2012",
            "(What's the Story) Morning Glory?" to "(What's the Story) Morning Glory?",
            "Collision Course (Bonus Video Version) - EP" to "Collision Course",
        )

        parts.forEach { (title, part) -> assertEquals(title, part, firstPartOf(title)) }
    }

    @Test
    fun theCandidates_shareTheFirstPartAndTheArtist_withAGoodScore() {
        val album = CoverQuery("Hits (Live)", artist)
        val candidates = candidatesOf(
            listOf(
                group("a", "Hits"),
                group("b", "Hits: More"),
                group("c", "Other Hits"),
                group("d", "Hits", score = 90),
                group("e", "Hits", credits = listOf(Credit("Someone", "", "x", "Someone"))),
                group("a", "Hits"),
            ),
            album,
        )

        assertEquals(listOf("a", "b"), candidates.map { it.id })
    }

    @Test
    fun hint1_theClosestFullTitle_comesFirst() {
        val album = CoverQuery("Greatest Hits", artist)

        assertEquals(
            listOf("plain", "volume"),
            rankedIds(album, group("volume", "Greatest Hits: Volume 2"), group("plain", "Greatest Hits")),
        )
    }

    @Test
    fun hint2_aLiveAlbum_prefersALiveReleaseGroup() {
        val album = CoverQuery("Skin and Bones (Live)", artist)
        val studio = group("studio", "Skin and Bones", date = "2001")
        val live = group("live", "Skin and Bones", secondary = listOf("Live"), date = "2006")

        assertEquals(listOf("live", "studio"), rankedIds(album, studio, live))
        // An album that doesn't say live has no preference: the earliest is first.
        assertEquals(listOf("studio", "live"), rankedIds(CoverQuery("Skin and Bones: Deluxe", artist), studio, live))
    }

    @Test
    fun hint3_enoughTracks_beatsTheYearTheTypeAndTheDate() {
        val album = CoverQuery("Hits (Live)", artist, highestTrackNumber = 15, year = 2005)
        val short = group("short", "Hits", date = "2005")
        val long = group("long", "Hits", type = "EP", date = "2009")

        assertEquals(
            listOf("long", "short"),
            rankedIds(album, short, long, counts = mapOf("short" to listOf(3), "long" to listOf(15, 21))),
        )
    }

    @Test
    fun hint4_theAlbumsYear_comesFirst_andNoYearSkipsIt() {
        val early = group("early", "Hits", date = "2001-05-01")
        val ofTheYear = group("year", "Hits", date = "2006-11-01")

        assertEquals(
            listOf("year", "early"),
            rankedIds(CoverQuery("Hits (Live)", artist, year = 2006), early, ofTheYear),
        )
        assertEquals(listOf("early", "year"), rankedIds(CoverQuery("Hits (Live)", artist), early, ofTheYear))
    }

    @Test
    fun hint5_anAlbum_comesBeforeAnEp() {
        val ep = group("ep", "Hits", type = "EP", date = "2006-10-16")
        val album = group("album", "Hits", date = "2006-11-01")

        assertEquals(listOf("album", "ep"), rankedIds(CoverQuery("Hits (Live)", artist), ep, album))
    }

    @Test
    fun hint6_theEarliest_thenTheAnswersOrder() {
        val later = group("later", "Hits", date = "1997-09-16")
        val earlier = group("earlier", "Hits", date = "1997")
        val undated = group("undated", "Hits")
        val undated2 = group("undated2", "Hits")

        assertEquals(
            listOf("earlier", "later", "undated", "undated2"),
            rankedIds(CoverQuery("Hits (Live)", artist), undated, later, undated2, earlier),
        )
    }

    @Test
    fun trackCounts_areAskedOnlyForTheFirstEqualCandidates_atMostThree() {
        val album = CoverQuery("Hits (Live)", artist, highestTrackNumber = 12)
        val groups = listOf(
            group("a", "Hits"),
            group("b", "Hits"),
            group("c", "Hits: Volume 2"),
            group("d", "Hits"),
            group("e", "Hits"),
        )

        assertEquals(listOf("a", "b", "d"), needingTrackCounts(groups, album).map { it.id })
        assertTrue(needingTrackCounts(groups, album.copy(highestTrackNumber = null)).isEmpty())
        assertTrue(needingTrackCounts(groups.take(1), album).isEmpty())
    }

    @Test
    fun theTrackCounts_ofAnAnswer_areRead() {
        assertEquals(
            listOf(15, 21),
            parseTrackCounts("{\"releases\": [{\"track-count\": 15}, {}, {\"track-count\": 21}]}"),
        )
        assertEquals(null, parseTrackCounts("<html>busy</html>"))
    }
}
