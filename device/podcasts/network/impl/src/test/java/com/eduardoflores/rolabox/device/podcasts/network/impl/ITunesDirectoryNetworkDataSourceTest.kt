package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkError
import dagger.Lazy
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private val EmperorsOfRome = DirectoryShow(
    DirectoryId.itunes(850148806),
    "Emperors of Rome",
    "Nightlight Productions",
    "https://mattsmithpodcast.com/emperors/eor.xml",
)

/** The directory against its recorded answers, served from a local server: no request leaves the test. */
class ITunesDirectoryNetworkDataSourceTest {
    private val server = MockWebServer()

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.close()

    private fun TestScope.source(country: String = "AU") = ITunesDirectoryNetworkDataSource(
        Lazy { testClient() },
        server.url("/search"),
        DeviceCountry { country },
        StandardTestDispatcher(testScheduler),
    )

    @Test
    fun shows_areTheDirectorysInItsOrder() = runTest {
        server.enqueue(answer(200, recorded("itunes/emperors-of-rome-shows.json")))

        val shows = source().searchShows("emperors of rome").getOrNull()!!

        assertEquals(EmperorsOfRome, shows.first())
        assertEquals(
            listOf(
                "Emperors of Rome",
                "The Fall of Rome Podcast",
                "Augustus Caesar: The First Emperor of Rome — Fexingo History",
            ),
            shows.map { it.title },
        )
        assertEquals(DirectoryId("itunes-1141563910"), shows[1].directoryId)
    }

    @Test
    fun aSearch_sendsTheTextWhatItSearchesForTheLimitAndTheCountry_andNothingElse() = runTest {
        server.enqueue(answer(200, recorded("itunes/emperors-of-rome-shows.json")))
        server.enqueue(answer(200, recorded("itunes/tiberius-gracchus-episodes.json")))

        source(country = "AU").searchShows("emperors of rome")
        source(country = "AU").searchEpisodes("tiberius gracchus")

        val shows = server.takeRequest().url
        assertEquals(setOf("term", "media", "entity", "limit", "country"), shows.queryParameterNames)
        assertEquals("emperors of rome", shows.queryParameter("term"))
        assertEquals("podcast", shows.queryParameter("media"))
        assertEquals("podcast", shows.queryParameter("entity"))
        assertEquals("25", shows.queryParameter("limit"))
        assertEquals("AU", shows.queryParameter("country"))
        assertEquals("podcastEpisode", server.takeRequest().url.queryParameter("entity"))
    }

    @Test
    fun episodes_comeWithTheirShow_whoseCreatorTheFeedGives() = runTest {
        server.enqueue(answer(200, recorded("itunes/tiberius-gracchus-episodes.json")))

        val episodes = source().searchEpisodes("tiberius gracchus").getOrNull()!!

        assertEquals(
            FoundEpisode(
                show = EmperorsOfRome.copy(creator = null),
                episode = DirectoryEpisode(
                    guid = "F3540ADA-3743-4A09-90B5-3598EBA2DFD0",
                    title = "Episode CXXX - Unpopular Reforms (Gracchi III)",
                    publishedAt = Instant.parse("2019-11-06T07:19:36Z"),
                    length = 2_201_000.milliseconds,
                    description = episodes.first().episode.description,
                    audioUrl = "https://www.mattsmithpodcast.com/emperors/191106-gracchi03.mp3",
                ),
            ),
            episodes.first(),
        )
        // Found by its description: its title holds neither word (ADR-031, The directory).
        assertEquals(true, episodes.first().episode.description!!.startsWith("Tiberius Gracchus had introduced"))
        assertEquals(3, episodes.size)
    }

    @Test
    fun noResults_isAnEmptyAnswer() = runTest {
        server.enqueue(answer(200, """{"resultCount":0,"results":[]}"""))

        assertEquals(emptyList<DirectoryShow>().right(), source().searchShows("zzzxqv"))
    }

    @Test
    fun aShowWithNoFeed_andAnEpisodeThatIsntAudio_areDropped() = runTest {
        server.enqueue(
            answer(
                200,
                """{"results":[
                    {"collectionId":1,"collectionName":"No feed","artistName":"A"},
                    {"collectionId":2,"collectionName":"Feed","artistName":" ","feedUrl":"https://e.com/2"}
                ]}""",
            ),
        )
        server.enqueue(
            answer(
                200,
                """{"results":[
                    {"collectionId":2,"collectionName":"Feed","feedUrl":"https://e.com/2","trackName":"Video",
                     "episodeUrl":"https://e.com/v.mp4","episodeContentType":"video"},
                    {"collectionId":2,"collectionName":"Feed","feedUrl":"https://e.com/2","trackName":"No audio"},
                    {"collectionId":3,"collectionName":"No feed","trackName":"Orphan","episodeUrl":"https://e.com/o.mp3"},
                    {"collectionId":2,"collectionName":"Feed","feedUrl":"https://e.com/2","trackName":"Audio",
                     "episodeUrl":"https://e.com/a.mp3","releaseDate":"not a date","trackTimeMillis":0}
                ]}""",
            ),
        )

        val shows = source().searchShows("feed").getOrNull()!!
        val episodes = source().searchEpisodes("feed").getOrNull()!!

        val feed = DirectoryShow(DirectoryId.itunes(2), "Feed", creator = null, feedUrl = "https://e.com/2")
        assertEquals(listOf(feed), shows)
        assertEquals(
            listOf(FoundEpisode(feed, DirectoryEpisode(null, "Audio", null, null, null, "https://e.com/a.mp3"))),
            episodes,
        )
    }

    @Test
    fun aRefusalForTheRate_isBusy() = runTest {
        server.enqueue(answer(429))
        server.enqueue(answer(403))

        assertEquals(DirectoryNetworkError.Busy.left(), source().searchShows("rome"))
        assertEquals(DirectoryNetworkError.Busy.left(), source().searchEpisodes("rome"))
    }

    @Test
    fun anyOtherErrorOrABodyThatIsntAnAnswer_failed() = runTest {
        server.enqueue(answer(500))
        server.enqueue(answer(200, "<html>Maintenance</html>"))
        server.enqueue(answer(200, """{"errorMessage":"Invalid value(s) for key(s): [country]"}"""))

        repeat(3) { assertEquals(DirectoryNetworkError.Failed.left(), source().searchShows("rome")) }
    }

    @Test
    fun aTimeout_isUnreachable() = runTest {
        server.enqueue(tooSlow())

        assertEquals(DirectoryNetworkError.Unreachable.left(), source().searchShows("rome"))
    }

    @Test
    fun noServer_isUnreachable() = runTest {
        val source = source()
        server.close()

        assertEquals(DirectoryNetworkError.Unreachable.left(), source.searchShows("rome"))
    }
}
