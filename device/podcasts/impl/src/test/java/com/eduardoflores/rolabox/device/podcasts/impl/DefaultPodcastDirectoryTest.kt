package com.eduardoflores.rolabox.device.podcasts.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryResults
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.Feed
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators
import com.eduardoflores.rolabox.device.podcasts.testing.FakeConnectionNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakeDirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakeDirectoryNetworkDataSource.Kind
import com.eduardoflores.rolabox.device.podcasts.testing.FakeFeedNetworkDataSource
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultPodcastDirectoryTest {
    private val network = FakeDirectoryNetworkDataSource()
    private val feeds = FakeFeedNetworkDataSource()
    private val connection = FakeConnectionNetworkDataSource()
    private val directory = DefaultPodcastDirectory(network, feeds, connection)

    private val rome = DirectoryShow(DirectoryId.itunes(1), "Emperors of Rome", "Nightlight", "https://e.com/rome.xml")
    private val gracchi = FoundEpisode(
        rome.copy(creator = null),
        DirectoryEpisode("g1", "Unpopular Reforms", null, null, "Tiberius Gracchus", "https://e.com/1.mp3"),
    )

    @Test
    fun aSearch_isShowsThenEpisodes_fromTwoRequests() = runTest {
        network.shows = mapOf("rome" to listOf(rome))
        network.episodes = mapOf("rome" to listOf(gracchi))

        assertEquals(DirectoryResults(listOf(rome), listOf(gracchi)).right(), directory.search("rome"))
        assertEquals(setOf(Kind.Shows to "rome", Kind.Episodes to "rome"), network.requests.toSet())
    }

    @Test
    fun noResults_isAnEmptyAnswer() = runTest {
        val results = directory.search("zzzxqv").getOrNull()!!

        assertEquals(true, results.isEmpty)
    }

    @Test
    fun aBlankText_isntSent() = runTest {
        assertEquals(DirectoryResults.None.right(), directory.search("   "))
        assertEquals(emptyList<Pair<Kind, String>>(), network.requests)
    }

    @Test
    fun theText_isSentTrimmed_withSpacesRunTogether() = runTest {
        directory.search("  emperors   of rome ")

        assertEquals(setOf("emperors of rome"), network.requests.map { it.second }.toSet())
    }

    @Test
    fun aTextAskedAgain_isAnsweredFromMemory_whateverItsCase() = runTest {
        network.shows = mapOf("Rome" to listOf(rome))
        directory.search("Rome")
        network.requests.clear()

        assertEquals(listOf(rome), directory.search("rome ").getOrNull()!!.shows)
        assertEquals(emptyList<Pair<Kind, String>>(), network.requests)
    }

    @Test
    fun onlyTheLast20Texts_areRemembered() = runTest {
        (1..REMEMBERED_SEARCHES + 1).forEach { directory.search("text $it") }
        network.requests.clear()

        directory.search("text 2")
        assertEquals(emptyList<Pair<Kind, String>>(), network.requests)
        directory.search("text 1")
        assertEquals(2, network.requests.size)
    }

    @Test
    fun aFailedSearch_isntRemembered() = runTest {
        network.showsError = DirectoryNetworkError.Unreachable
        directory.search("rome")
        network.showsError = null
        network.shows = mapOf("rome" to listOf(rome))

        assertEquals(listOf(rome), directory.search("rome").getOrNull()!!.shows)
    }

    @Test
    fun eitherRequestFailing_failsTheSearch_withThePartsError() = runTest {
        network.episodesError = DirectoryNetworkError.Busy
        assertEquals(PodcastError.DirectoryBusy.left(), directory.search("a"))

        network.episodesError = null
        network.showsError = DirectoryNetworkError.Failed
        assertEquals(PodcastError.DirectoryFailed.left(), directory.search("b"))

        network.showsError = DirectoryNetworkError.Unreachable
        assertEquals(PodcastError.NoConnection.left(), directory.search("c"))
    }

    private val feedEpisode = FeedEpisode(
        guid = "g1",
        title = "Unpopular Reforms",
        publishedAt = Instant.parse("2019-11-06T07:19:36Z"),
        length = 36.minutes,
        description = "Tiberius Gracchus had introduced property laws.",
        audioUrl = "https://e.com/1.mp3",
        audioSizeBytes = 35_000_000,
        audioType = "audio/mpeg",
        imageUrl = "https://e.com/1.jpg",
    )

    private fun read(ownerName: String?, movedTo: String? = null) = FeedAnswer.Read(
        Feed("Emperors of Rome", ownerName, "https://e.com/rome.jpg", listOf(feedEpisode)),
        FeedValidators("\"etag\"", null),
        movedTo,
    )

    @Test
    fun aShow_isItsFeed_withTheFeedsOwnerAndEpisodes() = runTest {
        feeds.serve(rome.feedUrl, read(ownerName = "Nightlight Productions"))

        val expected = ShowFeed(
            show = rome,
            ownerName = "Nightlight Productions",
            episodes = listOf(
                DirectoryEpisode(
                    guid = "g1",
                    title = "Unpopular Reforms",
                    publishedAt = Instant.parse("2019-11-06T07:19:36Z"),
                    length = 36.minutes,
                    description = "Tiberius Gracchus had introduced property laws.",
                    audioUrl = "https://e.com/1.mp3",
                    audioSizeBytes = 35_000_000,
                    audioType = "audio/mpeg",
                ),
            ),
        )
        assertEquals(expected.right(), directory.show(rome))
        // A show from search has no validators: the whole feed is asked for.
        assertEquals(listOf(rome.feedUrl to FeedValidators.None), feeds.fetched)
    }

    @Test
    fun aFeedWithNoOwner_hasTheDirectorysCreator_orNone() = runTest {
        feeds.serve(rome.feedUrl, read(ownerName = null))

        assertEquals("Nightlight", directory.show(rome).getOrNull()!!.ownerName)
        assertEquals(null, directory.show(rome.copy(creator = null)).getOrNull()!!.ownerName)
    }

    @Test
    fun aFeedThatMoved_givesTheShowItsNewUrl() = runTest {
        feeds.serve(rome.feedUrl, read(ownerName = null, movedTo = "https://e.com/new.xml"))

        assertEquals("https://e.com/new.xml", directory.show(rome).getOrNull()!!.show.feedUrl)
    }

    @Test
    fun aFeedThatCantBeRead_orReached_isThePartsError() = runTest {
        feeds.answers = mapOf(rome.feedUrl to FeedNetworkError.Unreadable.left())
        assertEquals(PodcastError.FeedUnreadable.left(), directory.show(rome))

        feeds.answers = mapOf(rome.feedUrl to FeedNetworkError.Unreachable.left())
        assertEquals(PodcastError.NoConnection.left(), directory.show(rome))

        feeds.serve(rome.feedUrl, FeedAnswer.NotModified(movedTo = null))
        assertEquals(PodcastError.FeedUnreadable.left(), directory.show(rome))
    }
}
