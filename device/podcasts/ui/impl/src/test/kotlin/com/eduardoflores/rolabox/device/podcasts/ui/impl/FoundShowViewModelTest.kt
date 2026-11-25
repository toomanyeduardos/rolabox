package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDot
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.api.ShowKind
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FoundShowViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val directory = FakePodcastDirectory()
    private val podcasts = FakePodcastsRepository().apply { fillWithSamples() }

    private val stranger = DirectoryShow(DirectoryId.itunes(99), "The Daily Stranger", "Someone", "https://e.com/s")
    private val strangerFeed = ShowFeed(
        stranger,
        "Someone",
        listOf(episode("Second", "https://e.com/2.mp3"), episode("First", "https://e.com/1.mp3")),
    )

    // Hard Fork is in the samples with Follow Off. Its feed has one episode the library has, and one it hasn't.
    private val hardForkInLibrary = PodcastSamples.theDailyEpisodes.first().copy(showId = PodcastSamples.hardFork.id)
    private val hardFork =
        DirectoryShow(PodcastSamples.hardFork.directoryId, "Hard Fork", "NY Times", "https://e.com/hard-fork")
    private val hardForkFeed = ShowFeed(
        hardFork,
        "NY Times",
        listOf(episode("New", "https://e.com/new.mp3"), episode("Kept", hardForkInLibrary.audioUrl)),
    )

    private fun episode(title: String, url: String) = DirectoryEpisode(title, title, null, null, null, url)

    private fun TestScope.observedViewModel(show: DirectoryShow) =
        FoundShowViewModelImpl(show.toKey(), directory, podcasts).also {
            backgroundScope.launch(mainDispatcherRule.testDispatcher) { it.uiState.collect() }
        }

    private fun FoundShowViewModel.content() = (uiState.value as ListUiState.Loaded).value

    private fun FoundShowViewModel.rows() = foundShowRows(content(), WORDS)

    // A feed's episodes are newest first, as the parser gives them: each a day older than the one before.
    private fun ShowFeed.dated(kind: ShowKind) = copy(
        kind = kind,
        episodes = episodes.mapIndexed { index, episode ->
            episode.copy(publishedAt = Instant.ofEpochSecond((episodes.size - index) * DAY))
        },
    )

    // ADR-043: a serial feed from search is listed oldest first, and center goes to the episode its row shows.
    @Test
    fun serialFeed_isListedOldestFirst() = runTest {
        val feed = strangerFeed.dated(ShowKind.Serial)
        directory.feeds = mapOf(stranger to feed)
        val viewModel = observedViewModel(stranger)

        assertEquals(listOf("Follow", "First", "Second"), viewModel.rows().map { it.label })
        assertEquals(FoundEpisodeTarget.Search(stranger, feed.episodes.last()), viewModel.episodeTarget(0))
        assertEquals(0, viewModel.content().startingEpisode)
    }

    @Test
    fun episodicFeed_isListedNewestFirst() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed.dated(ShowKind.Episodic))

        assertEquals(listOf("Follow", "Second", "First"), observedViewModel(stranger).rows().map { it.label })
    }

    // The library's order, with the user's choice, wins over the feed's for a show the library has.
    @Test
    fun showInTheLibrary_isListedInTheLibrarysOrder() = runTest {
        podcasts.shows = podcasts.shows.map {
            if (it.id == PodcastSamples.hardFork.id) it.copy(episodeOrder = EpisodeOrder.Oldest) else it
        }
        podcasts.episodes = podcasts.episodes + hardForkInLibrary
        directory.feeds = mapOf(hardFork to hardForkFeed.dated(ShowKind.Episodic))

        assertEquals(
            listOf("Follow", hardForkInLibrary.title, "New"),
            observedViewModel(hardFork).rows().map {
                it.label
            },
        )
    }

    @Test
    fun whileTheFeedIsRead_itIsLoading() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed)
        val feed = CompletableDeferred<Unit>().also { directory.pendingShow = it }
        val viewModel = observedViewModel(stranger)
        assertEquals(ListUiState.Loading, viewModel.uiState.value)

        feed.complete(Unit)
        advanceUntilIdle()
        assertEquals(strangerFeed, viewModel.content().feed)
    }

    @Test
    fun aShowNotInTheLibrary_isFollowOff_thenEveryEpisodeWithTheArrowAndNoDot() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed)

        val viewModel = observedViewModel(stranger)

        assertEquals(
            listOf(
                DeviceListRow("Follow", value = "Off"),
                DeviceListRow("Second", download = DeviceListDownload.NotDownloaded),
                DeviceListRow("First", download = DeviceListDownload.NotDownloaded),
            ),
            viewModel.rows(),
        )
        assertEquals(listOf(stranger), directory.opened)
    }

    @Test
    fun noConnection_saysSo_andTheFeedIsReadWhenItComesBack() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed)
        directory.connected.value = false
        val viewModel = observedViewModel(stranger)

        assertEquals(ListUiState.NoConnection, viewModel.uiState.value)
        assertEquals(emptyList<DirectoryShow>(), directory.opened)

        directory.connected.value = true

        assertEquals(3, viewModel.rows().size)
        // Once read, a connection that goes changes nothing.
        directory.connected.value = false
        assertEquals(3, viewModel.rows().size)
    }

    @Test
    fun aFeedThatCantBeRead_saysSo() = runTest {
        directory.showError = PodcastError.FeedUnreadable

        assertEquals(ListUiState.FeedUnreadable, observedViewModel(stranger).uiState.value)
    }

    @Test
    fun followOn_addsTheShowWithItsFeed_andTheRowTurnsOn() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed)
        val viewModel = observedViewModel(stranger)

        viewModel.onFollowClick()

        assertEquals(listOf(strangerFeed), podcasts.followed)
        assertEquals(DeviceListRow("Follow", value = "On"), viewModel.rows().first())
    }

    @Test
    fun aShowInTheLibraryWithFollowOff_showsTheLibrarysEpisodes_andFollowAddsTheRestOfTheFeed() = runTest {
        podcasts.episodes = podcasts.episodes + hardForkInLibrary
        directory.feeds = mapOf(hardFork to hardForkFeed)
        val viewModel = observedViewModel(hardFork)

        assertEquals(
            listOf(
                DeviceListRow("Follow", value = "Off"),
                DeviceListRow("New", download = DeviceListDownload.NotDownloaded),
                DeviceListRow(
                    hardForkInLibrary.title!!,
                    dot = DeviceListDot.Unplayed,
                    download = DeviceListDownload.NotDownloaded,
                ),
            ),
            viewModel.rows(),
        )
        assertEquals(
            listOf(FoundEpisodeTarget.Search(hardFork, hardForkFeed.episodes[0]), FoundEpisodeTarget.Library(1)),
            listOf(viewModel.episodeTarget(0), viewModel.episodeTarget(1)),
        )

        viewModel.onFollowClick()

        // The episode it had is kept, and the one it hadn't is added: the whole feed is listed now.
        assertEquals(listOf(hardForkFeed), podcasts.followed)
        assertEquals(true, podcasts.shows.first { it.id == PodcastSamples.hardFork.id }.isFollowed)
        val listed = podcasts.episodes.filter { it.showId == PodcastSamples.hardFork.id }.map { it.audioUrl }
        assertEquals(setOf("https://e.com/new.mp3", hardForkInLibrary.audioUrl), listed.toSet())
        assertEquals(DeviceListRow("Follow", value = "On"), viewModel.rows().first())
    }

    @Test
    fun followOff_turnsOnlyTheSwitchOff() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed)
        val viewModel = observedViewModel(stranger)
        viewModel.onFollowClick()

        viewModel.onFollowClick()

        assertEquals(listOf(strangerFeed), podcasts.followed)
        assertEquals(DeviceListRow("Follow", value = "Off"), viewModel.rows().first())
    }

    @Test
    fun followThatCantBeChanged_saysSo_once() = runTest {
        directory.feeds = mapOf(stranger to strangerFeed)
        val viewModel = observedViewModel(stranger)
        podcasts.setWriteError(StorageError.Unavailable)

        viewModel.onFollowClick()

        assertTrue(viewModel.followFailed.value)
        viewModel.onFollowFailureShown()
        assertEquals(false, viewModel.followFailed.value)
    }

    private companion object {
        const val DAY = 86_400L
        val WORDS = ShowWords(follow = "Follow", on = "On", off = "Off", unknownEpisode = "Unknown episode")
    }
}
