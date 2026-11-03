package com.eduardoflores.rolabox.device.library.network.impl

import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoverMatchTest {
    private fun match(answer: String, title: String, artist: CoverArtist) =
        matchOf(checkNotNull(parseReleaseGroups(recorded(answer))), CoverQuery(title, artist))?.id

    @Test
    fun theAlbumOfTheTicket_matchesItsOneReleaseGroup() {
        assertEquals(
            "2f215175-266c-4945-b622-8c4f8cb0d4fb",
            match("live-2012.json", "Live 2012", CoverArtist.Named("Coldplay")),
        )
    }

    @Test
    fun caseWhitespaceAndPunctuation_dontMatter() {
        assertEquals(
            "2f215175-266c-4945-b622-8c4f8cb0d4fb",
            match("live-2012.json", "  LIVE   2012. ", CoverArtist.Named("coldplay!")),
        )
    }

    @Test
    fun aLeadingThe_onTheArtist_doesntMatter() {
        assertEquals(
            "2f215175-266c-4945-b622-8c4f8cb0d4fb",
            match("live-2012.json", "Live 2012", CoverArtist.Named("The Coldplay")),
        )
    }

    @Test
    fun anotherTitle_isNoMatch_soAnEditionIsntTakenForTheAlbum() {
        assertNull(match("live-2012.json", "Live 2012 (Deluxe Edition)", CoverArtist.Named("Coldplay")))
    }

    @Test
    fun anotherArtist_isNoMatch() {
        assertNull(match("live-2012.json", "Live 2012", CoverArtist.Named("Coldplay & Rihanna")))
    }

    @Test
    fun severalReleaseGroupsThatMatch_areAmbiguous_andNoCover() {
        // Queen has three release groups called Greatest Hits, all scored 100.
        assertNull(match("greatest-hits-queen.json", "Greatest Hits", CoverArtist.Named("Queen")))
    }

    @Test
    fun aLowScore_isNoMatch() {
        // Greatest Hits II is scored 93 for this search.
        val groups = checkNotNull(parseReleaseGroups(recorded("greatest-hits-queen.json")))
        val second = groups.filter { it.title == "Greatest Hits II" }
        assertNull(matchOf(second.take(1), CoverQuery("Greatest Hits II", CoverArtist.Named("Queen"))))
    }

    @Test
    fun aVariousArtistsAlbum_matchesMusicBrainzsVariousArtists() {
        assertEquals(
            "6ec24356-e2f9-402a-9e9c-2ee68ce5b509",
            match("pulp-fiction-various.json", "Pulp Fiction", CoverArtist.Various),
        )
    }

    @Test
    fun aVariousArtistsAlbum_isntMatchedByAName() {
        assertNull(match("pulp-fiction-various.json", "Pulp Fiction", CoverArtist.Named("Dick Dale")))
    }

    @Test
    fun aCreditOfSeveralArtists_matchesAsOneName() {
        val group = ReleaseGroup(
            id = "id",
            score = 100,
            title = "Collab",
            credits = listOf(
                Credit("Jay-Z", " & ", "a", "JAY-Z"),
                Credit("Kanye West", "", "b", "Kanye West"),
            ),
        )
        assertEquals("id", matchOf(listOf(group), CoverQuery("Collab", CoverArtist.Named("Jay-Z & Kanye West")))?.id)
        assertEquals("id", matchOf(listOf(group), CoverQuery("Collab", CoverArtist.Named("Jay-Z")))?.id)
    }

    @Test
    fun anAnswerThatIsntASearch_isNotRead() {
        assertNull(parseReleaseGroups("<html>busy</html>"))
        assertNull(parseReleaseGroups("{\"error\": \"busy\"}"))
        assertNull(parseReleaseGroups("{\"release-groups\": [{\"title\": \"no id\"}]}"))
    }

    @Test
    fun theQuery_isTwoPhrases_withQuotesEscaped() {
        assertEquals(
            "releasegroup:\"Say \\\"Hi\\\"\" AND artist:\"AC/DC\"",
            searchQueryOf(CoverQuery("Say \"Hi\"", CoverArtist.Named("AC/DC"))),
        )
        assertEquals(
            "releasegroup:\"Hits\" AND arid:$VARIOUS_ARTISTS_ID",
            searchQueryOf(CoverQuery("Hits", CoverArtist.Various)),
        )
    }
}
