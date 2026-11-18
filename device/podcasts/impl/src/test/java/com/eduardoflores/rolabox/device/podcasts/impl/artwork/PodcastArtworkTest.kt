package com.eduardoflores.rolabox.device.podcasts.impl.artwork

import arrow.core.Either
import arrow.core.left
import com.eduardoflores.rolabox.device.library.api.ArtworkImage
import com.eduardoflores.rolabox.device.library.testing.FakeArtworkRepository
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkName
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import com.eduardoflores.rolabox.device.podcasts.network.api.Feed
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedAnswer
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedNetworkError
import com.eduardoflores.rolabox.device.podcasts.network.api.FeedValidators
import com.eduardoflores.rolabox.device.podcasts.network.api.ImageNetworkError
import com.eduardoflores.rolabox.device.podcasts.testing.FakeFeedNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakeImageNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastArtworkLocalDataSource
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastArtworkLocalDataSource.Companion.imageOf
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** An episode's artwork (ADR-031, Artwork): downloaded by the podcasts part, its own image, then its show's. */
class PodcastArtworkTest {
    private val local = FakePodcastsLocalDataSource()
    private val files = FakePodcastArtworkLocalDataSource()
    private val feeds = FakeFeedNetworkDataSource()
    private val images = FakeImageNetworkDataSource()
    private val libraryArtwork = FakeArtworkRepository()
    private val scheduler = object : PodcastArtworkScheduler {
        val enqueued = mutableListOf<EpisodeId>()

        override fun enqueue(episodeId: EpisodeId) {
            enqueued += episodeId
        }
    }
    private val downloader = PodcastArtworkDownloader(local, files, feeds, images, libraryArtwork)
    private val repository = DefaultPodcastArtworkRepository(local, files, scheduler, libraryArtwork)

    private suspend fun addEpisode(): EpisodeId = local.addEpisode(SHOW, EPISODE, Instant.EPOCH).getOrNull()!!

    private fun serveFeed(episodeImage: String? = EPISODE_IMAGE, showImage: String? = SHOW_IMAGE) {
        val item = FeedEpisode(GUID, "Episode", null, null, null, AUDIO, null, null, episodeImage)
        feeds.serve(FEED, FeedAnswer.Read(Feed("Show", "Owner", showImage, listOf(item)), FeedValidators.None, null))
    }

    @Test
    fun theFeedIsRead_forTheImages_andTheEpisodesOwnIsKept() = runTest {
        val id = addEpisode()
        serveFeed()
        images.serve(EPISODE_IMAGE, EPISODE_BYTES)

        assertEquals(ArtworkOutcome.Done, downloader.download(id))

        assertEquals(listOf(FEED), feeds.fetched.map { it.first })
        assertEquals(listOf(EPISODE_IMAGE), images.fetched)
        assertEquals(imageOf(EPISODE_BYTES), repository.loadArtwork(id).getOrNull())
    }

    @Test
    fun anEpisodeWithNoImageOfItsOwn_hasItsShows() = runTest {
        val id = addEpisode()
        serveFeed(episodeImage = null)
        images.serve(SHOW_IMAGE, SHOW_BYTES)

        downloader.download(id)

        assertEquals(imageOf(SHOW_BYTES), repository.loadArtwork(id).getOrNull())
    }

    @Test
    fun anEpisodeImageThatCantBeUsed_fallsBackToTheShows() = runTest {
        val id = addEpisode()
        serveFeed()
        images.answers = mapOf(EPISODE_IMAGE to ImageNetworkError.Unusable.left())
        images.serve(SHOW_IMAGE, SHOW_BYTES)

        assertEquals(ArtworkOutcome.Done, downloader.download(id))

        assertEquals(imageOf(SHOW_BYTES), repository.loadArtwork(id).getOrNull())
    }

    @Test
    fun noNetwork_isRetried_andKeepsNothing() = runTest {
        val id = addEpisode()
        serveFeed()
        images.answers = mapOf(EPISODE_IMAGE to ImageNetworkError.Unreachable.left())

        assertEquals(ArtworkOutcome.Retry, downloader.download(id))
        assertNull(repository.loadArtwork(id).getOrNull())
    }

    @Test
    fun aFeedThatCantBeReached_isRetried() = runTest {
        val id = addEpisode()
        feeds.answers = mapOf(FEED to FeedNetworkError.Unreachable.left())

        assertEquals(ArtworkOutcome.Retry, downloader.download(id))
    }

    @Test
    fun imagesAlreadyKnown_needNoFeed() = runTest {
        val id = addEpisode()
        local.setImages(id, EPISODE_IMAGE, SHOW_IMAGE)
        images.serve(EPISODE_IMAGE, EPISODE_BYTES)

        downloader.download(id)

        assertTrue(feeds.fetched.isEmpty())
        assertEquals(imageOf(EPISODE_BYTES), repository.loadArtwork(id).getOrNull())
    }

    @Test
    fun withOnlineArtworkOff_nothingIsRequested() = runTest {
        val id = addEpisode()
        serveFeed()
        libraryArtwork.downloadEnabled.value = false

        assertEquals(ArtworkOutcome.Done, downloader.download(id))

        assertTrue(feeds.fetched.isEmpty())
        assertTrue(images.fetched.isEmpty())
    }

    @Test
    fun anEpisodeObservedWithNoArtwork_isEnqueuedOnce_andGetsItWhenItIsKept() = runTest {
        val id = addEpisode()
        serveFeed()
        images.serve(EPISODE_IMAGE, EPISODE_BYTES)

        val seen = observeTwoValues(id)
        repository.observeArtwork(id).first()
        assertEquals(listOf(id), scheduler.enqueued)

        downloader.download(id)
        runCurrent()

        assertEquals(listOf(null, imageOf(EPISODE_BYTES)), seen.map { it.getOrNull() })
        assertEquals(listOf(id), scheduler.enqueued)
    }

    @Test
    fun withOnlineArtworkOff_anEpisodeObservedIsntEnqueued() = runTest {
        val id = addEpisode()
        libraryArtwork.downloadEnabled.value = false

        assertNull(repository.observeArtwork(id).first().getOrNull())

        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test
    fun anEpisodeThatIsntThere_isEpisodeNotFound() = runTest {
        assertEquals(PodcastError.EpisodeNotFound(EpisodeId(99)), repository.loadArtwork(EpisodeId(99)).leftOrNull())
        assertEquals(ArtworkOutcome.Done, downloader.download(EpisodeId(99)))
    }

    @Test
    fun anImageKeptBefore_isntDownloadedAgain() = runTest {
        val id = addEpisode()
        local.setImages(id, EPISODE_IMAGE, SHOW_IMAGE)
        files.save(ArtworkName.OfEpisode(id, EPISODE_IMAGE), EPISODE_BYTES)

        downloader.download(id)

        assertTrue(images.fetched.isEmpty())
    }

    private fun TestScope.observeTwoValues(id: EpisodeId) = mutableListOf<Either<PodcastError, ArtworkImage?>>().also {
        backgroundScope.launch { repository.observeArtwork(id).take(2).toList(it) }
        runCurrent()
    }

    private companion object {
        const val FEED = "https://example.com/feed"
        const val GUID = "guid-1"
        const val AUDIO = "https://example.com/1.mp3"
        const val EPISODE_IMAGE = "https://example.com/episode.jpg"
        const val SHOW_IMAGE = "https://example.com/show.jpg"
        val EPISODE_BYTES = ByteArray(3)
        val SHOW_BYTES = ByteArray(5)

        val SHOW = ShowWrite(
            directoryId = DirectoryId.itunes(1),
            feedUrl = FEED,
            title = "Show",
            ownerName = "Owner",
            ownerKey = OwnerId("owner"),
            titleSortKey = "show",
            ownerSortKey = "owner",
        )
        val EPISODE = EpisodeWrite(
            itemKey = GUID,
            guid = GUID,
            title = "Episode",
            publishedAt = null,
            length = null,
            description = null,
            audioUrl = AUDIO,
        )
    }
}
