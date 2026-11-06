package com.eduardoflores.rolabox.device.library.network.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.library.network.api.Cover
import com.eduardoflores.rolabox.device.library.network.api.CoverArtist
import com.eduardoflores.rolabox.device.library.network.api.CoverQuery
import com.eduardoflores.rolabox.device.library.network.api.CoverSourceError
import dagger.Lazy
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

private const val LIVE_2012 = "2f215175-266c-4945-b622-8c4f8cb0d4fb"
private val Jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
private val Live2012 = CoverQuery("Live 2012", CoverArtist.Named("Coldplay"))
private const val SKIN_AND_BONES_ALBUM = "578a8246-a9e7-35cb-b7e9-bb799aba44cc"
private const val SKIN_AND_BONES_EP = "1b6c2ac6-e178-3044-b787-97be2d933ca1"
private const val HENDRIX_20_TRACKS = "c398cd29-bd3a-3dca-bea2-e5d93d6e2778"
private const val RADIO_CITY = "071e274e-ac5c-301b-8476-affc8e6f55fa"
private val SkinAndBones = CoverQuery(
    "Skin and Bones (Live)",
    CoverArtist.Named("Foo Fighters"),
    songArtists = listOf("Foo Fighters"),
    highestTrackNumber = 15,
    year = 2006,
)

/** The source against recorded answers, served by an interceptor: no request leaves the test. */
class MusicBrainzCoverNetworkDataSourceTest {
    private val requests = mutableListOf<HttpUrl>()
    private var answer: (HttpUrl) -> Answer = { url ->
        when {
            url.host == "musicbrainz.org" -> Answer(200, recorded("live-2012.json").toByteArray(), "application/json")
            url.encodedPath == "/release-group/$LIVE_2012/front-500" -> Answer(200, Jpeg, "image/jpeg")
            else -> Answer(404, ByteArray(0), "text/plain")
        }
    }

    private class Answer(val code: Int, val body: ByteArray, val type: String)

    private val client = OkHttpClient.Builder()
        .addInterceptor(
            Interceptor { chain ->
                val url = chain.request().url
                requests += url
                val served = answer(url)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(served.code)
                    .message("recorded")
                    .header("Content-Type", served.type)
                    .body(served.body.toResponseBody(served.type.toMediaType()))
                    .build()
            },
        )
        .build()

    private fun TestScope.source() = MusicBrainzCoverNetworkDataSource(
        Lazy { client },
        RequestSpacing(Duration.ZERO, TestTimeSource()),
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun aMatch_downloadsTheFrontImageOfItsReleaseGroup() = runTest {
        assertEquals(Cover(LIVE_2012, Jpeg).right(), source().findCover(Live2012))
        assertEquals("musicbrainz.org", requests[0].host)
        assertEquals("releasegroup:\"Live 2012\" AND artist:\"Coldplay\"", requests[0].queryParameter("query"))
        assertEquals("json", requests[0].queryParameter("fmt"))
        assertEquals("https://coverartarchive.org/release-group/$LIVE_2012/front-500", requests[1].toString())
    }

    @Test
    fun noMatch_isNoCover_withoutAskingTheArchive() = runTest {
        assertEquals(null.right(), source().findCover(CoverQuery("Parachutes", CoverArtist.Named("Coldplay"))))
        assertEquals(1, requests.size)
    }

    @Test
    fun aMatchWithNoFrontImage_isNoCover() = runTest {
        val search = answer
        answer =
            { url -> if (url.host == "coverartarchive.org") Answer(404, ByteArray(0), "text/plain") else search(url) }
        assertEquals(null.right(), source().findCover(Live2012))
    }

    @Test
    fun noNetwork_isUnreachable() = runTest {
        answer = { throw IOException("offline") }
        assertEquals(CoverSourceError.Unreachable.left(), source().findCover(Live2012))
    }

    @Test
    fun aTimeout_isUnreachable() = runTest {
        answer = { throw SocketTimeoutException("slow") }
        assertEquals(CoverSourceError.Unreachable.left(), source().findCover(Live2012))
    }

    @Test
    fun aRequestToSlowDown_isAServerError() = runTest {
        answer = { Answer(503, ByteArray(0), "text/plain") }
        assertEquals(CoverSourceError.Server(503).left(), source().findCover(Live2012))
    }

    @Test
    fun anAnswerThatIsntASearch_isMalformed() = runTest {
        answer = { Answer(200, "<html>maintenance</html>".toByteArray(), "text/html") }
        assertEquals(CoverSourceError.Malformed.left(), source().findCover(Live2012))
    }

    @Test
    fun anImageThatIsntOne_isMalformed() = runTest {
        val search = answer
        answer = { url -> if (url.host == "coverartarchive.org") Answer(200, Jpeg, "text/html") else search(url) }
        assertEquals(CoverSourceError.Malformed.left(), source().findCover(Live2012))
    }

    // Pass 1 finds nothing for the title with "(Live)", and pass 2's search of "Skin and Bones" has an album and an EP
    // of that name, both scored 100. The album's 15 tracks are enough for the 15th track, and the EP's 3 aren't.
    @Test
    fun skinAndBones_pass2_takesTheReleaseGroupWithEnoughTracks() = runTest {
        answer = recordedAnswers()
        assertEquals(Cover(SKIN_AND_BONES_ALBUM, Jpeg).right(), source().findCover(SkinAndBones))
        assertEquals(
            listOf(
                "releasegroup:\"Skin and Bones (Live)\" AND artist:\"Foo Fighters\"",
                "releasegroup:\"Skin and Bones\" AND artist:\"Foo Fighters\"",
                "rgid:$SKIN_AND_BONES_ALBUM",
                "rgid:$SKIN_AND_BONES_EP",
            ),
            requests.mapNotNull { it.queryParameter("query") },
        )
        assertEquals("/release-group/$SKIN_AND_BONES_ALBUM/front-500", requests.last().encodedPath)
    }

    @Test
    fun aCandidateWithNoFrontImage_isPassedForTheNext() = runTest {
        val recorded = recordedAnswers()
        answer = { url ->
            if (url.encodedPath.contains(
                    SKIN_AND_BONES_ALBUM,
                )
            ) {
                Answer(404, ByteArray(0), "text/plain")
            } else {
                recorded(url)
            }
        }
        assertEquals(Cover(SKIN_AND_BONES_EP, Jpeg).right(), source().findCover(SkinAndBones))
    }

    // MusicBrainz has the compilation twice under one title: the one with a 20th track is this album.
    @Test
    fun experienceHendrix_pass2_takesTheEditionWithItsTracks() = runTest {
        answer = recordedAnswers()
        val album = CoverQuery(
            "Experience Hendrix - The Best of Jimi Hendrix",
            CoverArtist.Named("Jimi Hendrix"),
            highestTrackNumber = 20,
        )
        assertEquals(Cover(HENDRIX_20_TRACKS, Jpeg).right(), source().findCover(album))
    }

    // The album artist is "Dave Matthews", and its songs' artist is MusicBrainz's credit.
    @Test
    fun liveAtRadioCity_pass1_matchesAnArtistOfItsSongs() = runTest {
        answer = recordedAnswers()
        val album = CoverQuery(
            "Live At Radio City (Bonus Track Version)",
            CoverArtist.Named("Dave Matthews"),
            songArtists = listOf("Dave Matthews & Tim Reynolds"),
        )
        assertEquals(Cover(RADIO_CITY, Jpeg).right(), source().findCover(album))
        assertEquals(2, requests.size)
    }

    @Test
    fun liveAtRadioCity_byItsAlbumArtistOnly_isNoCover() = runTest {
        answer = recordedAnswers()
        val album = CoverQuery("Live At Radio City (Bonus Track Version)", CoverArtist.Named("Dave Matthews"))
        assertEquals(null.right(), source().findCover(album))
    }

    // Pass 1's answer is the album: another release group's cover would be another album's.
    @Test
    fun pass1sMatchWithNoFrontImage_isNoCover_withoutPass2() = runTest {
        val search = answer
        answer =
            { url -> if (url.host == "coverartarchive.org") Answer(404, ByteArray(0), "text/plain") else search(url) }
        assertEquals(null.right(), source().findCover(Live2012.copy(title = "Live 2012 (Deluxe Edition)")))
        assertEquals(2, requests.size)
    }

    /** MusicBrainz's recorded answers for the albums above, by the query, and a front image for every release group. */
    private fun recordedAnswers(): (HttpUrl) -> Answer {
        // A search not here finds nothing, as pass 1's of "Skin and Bones (Live)" and of Hendrix's full title do.
        val searches = mapOf(
            "releasegroup:\"Skin and Bones\" AND artist:\"Foo Fighters\"" to "skin-and-bones.json",
            "releasegroup:\"Experience Hendrix\" AND artist:\"Jimi Hendrix\"" to "experience-hendrix.json",
            "releasegroup:\"Live At Radio City\" AND artist:\"Dave Matthews\"" to "live-at-radio-city.json",
        )
        return { url ->
            val query = url.queryParameter("query")
            when {
                url.host == "coverartarchive.org" -> Answer(200, Jpeg, "image/jpeg")

                query != null && query.startsWith("rgid:") ->
                    Answer(
                        200,
                        recorded("releases-${query.removePrefix("rgid:")}.json").toByteArray(),
                        "application/json",
                    )

                query != null && query in searches ->
                    Answer(200, recorded(searches.getValue(query)).toByteArray(), "application/json")

                else -> Answer(200, "{\"release-groups\": []}".toByteArray(), "application/json")
            }
        }
    }

    @Test
    fun theRequestsWaitTheirTurn() = runTest {
        val spacing = RequestSpacing(1.seconds, testScheduler.timeSource)
        val started = mutableListOf<Long>()
        repeat(3) { spacing.next { started += testScheduler.currentTime } }
        advanceTimeBy(5.seconds)
        spacing.next { started += testScheduler.currentTime }
        // A second apart, and none waits when the last was long enough ago.
        assertEquals(listOf(0L, 1000L, 2000L, 7000L), started)
    }
}
