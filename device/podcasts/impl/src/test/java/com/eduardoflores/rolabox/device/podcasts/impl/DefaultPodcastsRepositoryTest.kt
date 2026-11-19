package com.eduardoflores.rolabox.device.podcasts.impl

import arrow.core.left
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPodcastsRepositoryTest {
    private val local = FakePodcastsLocalDataSource()
    private val downloads = FakeEpisodeDownloads()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val repository = DefaultPodcastsRepository(local, downloads, clock)

    private val daily = DirectoryShow(DirectoryId.itunes(1), "The Daily", "NY Times", "https://example.com/feed")

    private fun episode(guid: String?, url: String = "https://example.com/$guid.mp3", day: Long = 1) =
        DirectoryEpisode(guid, "Episode $guid", Instant.ofEpochSecond(day * DAY), 30.minutes, null, url)

    private suspend fun add(episode: DirectoryEpisode, show: DirectoryShow = daily): EpisodeId =
        repository.addEpisode(show, episode).getOrNull()!!

    private suspend fun showOf(episodeId: EpisodeId): ShowId = repository.getEpisode(episodeId).getOrNull()!!.showId

    @Test
    fun addingTheSameEpisodeTwice_givesTheSameEpisode() = runTest {
        val first = add(episode("g1"))
        val second = add(episode("g1"))

        assertEquals(first, second)
        repository.setFollowed(showOf(first), followed = true)
        assertEquals(listOf(first), repository.observeEpisodesByShow(showOf(first)).first().getOrNull()?.map { it.id })
    }

    @Test
    fun anEpisodeFromAFeed_keepsItsAudiosSize() = runTest {
        val id = add(episode("g1").copy(audioSizeBytes = 35_000_000, audioType = "audio/mpeg"))

        assertEquals(35_000_000L, repository.getEpisode(id).getOrNull()!!.audioSizeBytes)
    }

    @Test
    fun anEpisodeWithNoGuid_isIdentifiedByItsAudio() = runTest {
        val first = add(episode(guid = null, url = "https://example.com/a.mp3"))
        val again = add(episode(guid = " ", url = "https://example.com/a.mp3"))
        val other = add(episode(guid = null, url = "https://example.com/b.mp3"))

        assertEquals(first, again)
        assertTrue(other != first)
    }

    @Test
    fun aShowsOwner_isItsCreator_groupedIgnoringCaseAndWhitespace() = runTest {
        add(episode("g1"))
        add(episode("g2"), DirectoryShow(DirectoryId.itunes(2), "Hard Fork", "  ny   TIMES ", "https://e.com/2"))
        add(episode("g3"), DirectoryShow(DirectoryId.itunes(3), "Unsigned", "   ", "https://e.com/3"))

        val owners = repository.observeOwners().first().getOrNull()!!

        assertEquals(setOf(OwnerId("ny times"), OwnerId("")), owners.map(Owner::id).toSet())
        assertEquals(null, owners.single { it.id == OwnerId("") }.name)
        val nyTimes = repository.observeShowsByOwner(OwnerId("ny times")).first().getOrNull()!!
        assertEquals(setOf("The Daily", "Hard Fork"), nyTimes.map { it.title }.toSet())
        assertEquals("ny TIMES", nyTimes.single { it.title == "Hard Fork" }.ownerName)
    }

    @Test
    fun aShowAddedByPlaying_isFollowOff_andListsOnlyWhatHasADownload() = runTest {
        val notDownloaded = add(episode("g1", day = 3))
        val downloading = add(episode("g2", day = 2))
        val downloaded = add(episode("g3", day = 1))
        val showId = showOf(notDownloaded)

        assertEquals(false, repository.observeShow(showId).first().getOrNull()?.isFollowed)
        // Until podcasts/07, nothing is downloaded, so a show with Follow Off lists nothing.
        assertEquals(emptyList<Any>(), repository.observeEpisodesByShow(showId).first().getOrNull())

        downloads.states.value = mapOf(
            downloading to DownloadState.Downloading(0.38f),
            downloaded to DownloadState.Downloaded,
        )
        val listed = repository.observeEpisodesByShow(showId).first().getOrNull()!!

        assertEquals(listOf(downloading, downloaded), listed.map { it.id })
        assertEquals(listOf(DownloadState.Downloading(0.38f), DownloadState.Downloaded), listed.map { it.download })
    }

    @Test
    fun followOn_listsEveryEpisode_newestFirst_andOffAgain_keepsTheirRows() = runTest {
        val older = add(episode("g1", day = 1))
        val newer = add(episode("g2", day = 2))
        val showId = showOf(older)

        assertEquals(Unit, repository.setFollowed(showId, followed = true).getOrNull())
        assertEquals(true, repository.observeShow(showId).first().getOrNull()?.isFollowed)
        assertEquals(
            listOf(newer, older),
            repository.observeEpisodesByShow(showId).first().getOrNull()?.map { it.id },
        )
        assertEquals(true to Instant.ofEpochMilli(NOW), local.follows[DirectoryId.itunes(1)])

        repository.setFollowed(showId, followed = false)
        assertEquals(emptyList<Any>(), repository.observeEpisodesByShow(showId).first().getOrNull())
        assertEquals(false to Instant.ofEpochMilli(NOW), local.follows[DirectoryId.itunes(1)])
        repository.setFollowed(showId, followed = true)
        assertEquals(2, repository.observeEpisodesByShow(showId).first().getOrNull()?.size)
    }

    @Test
    fun anEpisode_readsAsNotDownloaded_untilPlaybackSaysOtherwise() = runTest {
        val id = add(episode("g1"))

        assertEquals(DownloadState.NotDownloaded, repository.getEpisode(id).getOrNull()?.download)
        downloads.states.value = mapOf(id to DownloadState.Downloaded)
        assertEquals(DownloadState.Downloaded, repository.observeEpisode(id).first().getOrNull()?.download)
    }

    @Test
    fun whatIsntThere_isNotFound_andEndsTheFlow() = runTest {
        val owner = OwnerId("nobody")
        val show = ShowId(9)
        val episode = EpisodeId(9)

        assertEquals(listOf(PodcastError.OwnerNotFound(owner).left()), repository.observeShowsByOwner(owner).toList())
        assertEquals(listOf(PodcastError.ShowNotFound(show).left()), repository.observeShow(show).toList())
        assertEquals(listOf(PodcastError.ShowNotFound(show).left()), repository.observeEpisodesByShow(show).toList())
        assertEquals(listOf(PodcastError.EpisodeNotFound(episode).left()), repository.observeEpisode(episode).toList())
        assertEquals(PodcastError.EpisodeNotFound(episode).left(), repository.getEpisode(episode))
        assertEquals(PodcastError.ShowNotFound(show).left(), repository.setFollowed(show, followed = true))
    }

    @Test
    fun followingFromSearch_addsTheShowWithTheFeedsOwner_everyEpisode_andFollowOn() = runTest {
        val feed = ShowFeed(daily, "The New York Times", listOf(episode("g2", day = 2), episode("g1")))

        val showId = repository.follow(feed).getOrNull()!!

        val show = repository.observeShow(showId).first().getOrNull()!!
        assertEquals(true, show.isFollowed)
        assertEquals("The New York Times", show.ownerName)
        assertEquals(
            listOf("Episode g2", "Episode g1"),
            repository.observeEpisodesByShow(showId).first().getOrNull()?.map { it.title },
        )
    }

    @Test
    fun aShowAndAnEpisodeFromSearch_areFoundInTheLibrary_onlyOnceAdded() = runTest {
        assertEquals(null, repository.findShow(daily).getOrNull())
        assertEquals(null, repository.findEpisode(daily, episode("g1")).getOrNull())

        val id = add(episode("g1"))

        assertEquals(showOf(id), repository.findShow(daily).getOrNull()?.id)
        assertEquals(id, repository.findEpisode(daily, episode("g1")).getOrNull())
        assertEquals(null, repository.findEpisode(daily, episode("g2")).getOrNull())
    }

    @Test
    fun aFeedInTheLibrary_hasTheLibrarysEpisodes_inTheFeedsOrder_whateverItsFollowSays() = runTest {
        val feed = ShowFeed(daily, "NY Times", listOf(episode("g3", day = 3), episode("g2", day = 2), episode("g1")))
        assertEquals(null, repository.observeInLibrary(feed).first().getOrNull())

        val played = add(episode("g2", day = 2))
        downloads.states.value = mapOf(played to DownloadState.Downloading(0.5f))

        val inLibrary = repository.observeInLibrary(feed).first().getOrNull()!!
        assertEquals(false, inLibrary.show.isFollowed)
        assertEquals(listOf(null, played, null), inLibrary.episodes.map { it?.id })
        assertEquals(DownloadState.Downloading(0.5f), inLibrary.episodes[1]?.download)
    }

    @Test
    fun aStorageError_isPassedOn() = runTest {
        local.readError = StorageError.Unavailable

        assertEquals(PodcastError.Storage(StorageError.Unavailable).left(), repository.addEpisode(daily, episode("g1")))
        assertEquals(
            PodcastError.Storage(StorageError.Unavailable).left(),
            repository.observeOwners().first(),
        )
    }

    private companion object {
        const val NOW = 5_000L
        const val DAY = 86_400L
    }
}
