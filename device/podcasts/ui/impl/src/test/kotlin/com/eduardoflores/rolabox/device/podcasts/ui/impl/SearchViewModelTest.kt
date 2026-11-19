package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListSection
import com.eduardoflores.rolabox.common.testing.MainDispatcherRule
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryResults
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    // A standard dispatcher, so the pause before a search is sent is the test's to advance.
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val directory = FakePodcastDirectory()
    private val podcasts = FakePodcastsRepository().apply { fillWithSamples() }

    private val theDaily =
        DirectoryShow(PodcastSamples.theDaily.directoryId, "The Daily", "NY Times", "https://e.com/d")
    private val stranger = DirectoryShow(DirectoryId.itunes(99), "The Daily Stranger", null, "https://e.com/s")
    private val commute = FoundEpisode(
        stranger,
        DirectoryEpisode("g1", "Why the Daily Commute Changed", null, null, null, "https://e.com/1.mp3"),
    )

    private fun test(body: suspend TestScope.(SearchViewModel) -> Unit) = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = SearchViewModelImpl(directory, podcasts)
        backgroundScope.launch { viewModel.uiState.collect() }
        runCurrent()
        body(viewModel)
    }

    private fun TestScope.typeAndPause(viewModel: SearchViewModel, text: String) {
        viewModel.onTextChange(text)
        advanceUntilIdle()
    }

    @Test
    fun typingFast_sendsOnlyTheText_typingPausedOn() = test { viewModel ->
        viewModel.onTextChange("th")
        advanceTimeBy(PAUSE / 2)
        viewModel.onTextChange("the")
        advanceTimeBy(PAUSE / 2)
        viewModel.onTextChange("the d")
        advanceUntilIdle()

        assertEquals(listOf("the d"), directory.searched)
    }

    @Test
    fun aNewerText_cancelsTheSearchOfAnOlderOne() = test { viewModel ->
        directory.pending = CompletableDeferred()
        typeAndPause(viewModel, "the")
        typeAndPause(viewModel, "the daily")

        directory.pending!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("the", "the daily"), directory.searched)
        assertEquals(listOf("the daily"), directory.answered)
    }

    @Test
    fun anEmptyText_orOneCharacter_isNotSent_andShowsNothing() = test { viewModel ->
        directory.results = mapOf("the daily" to DirectoryResults(listOf(theDaily), emptyList()))
        typeAndPause(viewModel, "the daily")
        typeAndPause(viewModel, "t")

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
        typeAndPause(viewModel, "   ")
        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
        assertEquals(listOf("the daily"), directory.searched)
    }

    @Test
    fun showsOnly_areListedUnderShows_withTheirCreator() = test { viewModel ->
        directory.results = mapOf("daily" to DirectoryResults(listOf(theDaily, stranger), emptyList()))
        typeAndPause(viewModel, "daily")

        val rows = searchRows(viewModel.uiState.value, WORDS, typing = false).map { it.entry }
        assertEquals(
            listOf(
                DeviceListSection("Shows"),
                DeviceListRow("The Daily", secondaryValue = "NY Times"),
                DeviceListRow("The Daily Stranger"),
            ),
            rows,
        )
    }

    @Test
    fun episodesOnly_areListedUnderEpisodes() = test { viewModel ->
        directory.results = mapOf("commute" to DirectoryResults(emptyList(), listOf(commute)))
        typeAndPause(viewModel, "commute")

        val rows = searchRows(viewModel.uiState.value, WORDS, typing = false).map { it.entry }
        assertEquals(
            listOf(DeviceListSection("Episodes"), DeviceListRow("Why the Daily Commute Changed", opensSubmenu = true)),
            rows,
        )
    }

    @Test
    fun showsAndEpisodes_areListedShowsFirst_andTheEpisodesArrowOnlyOnceTypingEnds() = test { viewModel ->
        directory.results = mapOf("daily" to DirectoryResults(listOf(theDaily), listOf(commute)))
        typeAndPause(viewModel, "daily")

        val typing = searchRows(viewModel.uiState.value, WORDS, typing = true)
        assertEquals(
            listOf(
                DeviceListSection("Shows"),
                DeviceListRow("The Daily", secondaryValue = "NY Times"),
                DeviceListSection("Episodes"),
                DeviceListRow("Why the Daily Commute Changed"),
            ),
            typing.map { it.entry },
        )
        assertEquals(
            listOf(null, SearchTarget.Show(theDaily), null, SearchTarget.Episode(commute)),
            typing.map { it.target },
        )
    }

    @Test
    fun noResults_saySo() = test { viewModel ->
        typeAndPause(viewModel, "zzqx radio")

        assertEquals(SearchUiState.NoResults, viewModel.uiState.value)
    }

    @Test
    fun noConnection_keepsTheText_andTheSearchComesBackWithTheConnection() = test { viewModel ->
        directory.results = mapOf("the daily" to DirectoryResults(listOf(theDaily), emptyList()))
        directory.connected.value = false
        typeAndPause(viewModel, "the daily")

        assertEquals(SearchUiState.NoConnection, viewModel.uiState.value)
        assertEquals(emptyList<String>(), directory.searched)

        directory.connected.value = true
        advanceUntilIdle()

        assertEquals(SearchUiState.Found(DirectoryResults(listOf(theDaily), emptyList())), viewModel.uiState.value)
    }

    @Test
    fun aSearchThatFindsNoConnection_saysSo() = test { viewModel ->
        directory.searchError = PodcastError.NoConnection
        typeAndPause(viewModel, "the daily")

        assertEquals(SearchUiState.NoConnection, viewModel.uiState.value)
    }

    @Test
    fun theDirectoryBusy_saysSo_isNotSentAgainByItself_andTheSearchKeySendsIt() = test { viewModel ->
        directory.searchError = PodcastError.DirectoryBusy
        typeAndPause(viewModel, "the daily")

        assertEquals(SearchUiState.Busy, viewModel.uiState.value)
        advanceTimeBy(PAUSE * 100)
        assertEquals(listOf("the daily"), directory.searched)

        directory.searchError = null
        directory.results = mapOf("the daily" to DirectoryResults(listOf(theDaily), emptyList()))
        viewModel.onSearchAction()
        advanceUntilIdle()

        assertEquals(listOf("the daily", "the daily"), directory.searched)
        assertEquals(SearchUiState.Found(DirectoryResults(listOf(theDaily), emptyList())), viewModel.uiState.value)
    }

    @Test
    fun anyOtherFailure_saysSearchFailed() = test { viewModel ->
        directory.searchError = PodcastError.DirectoryFailed
        typeAndPause(viewModel, "the daily")

        assertEquals(SearchUiState.Failed, viewModel.uiState.value)
    }

    @Test
    fun theSameTextAgain_isNoNewSearch() = test { viewModel ->
        typeAndPause(viewModel, "the daily")
        typeAndPause(viewModel, "the daily")

        assertEquals(listOf("the daily"), directory.searched)
    }

    @Test
    fun aShowTheLibraryFollows_opensTheLibrarysScreen() = test { viewModel ->
        var opened: Any? = null
        viewModel.onShowClick(theDaily, onLibraryShow = { opened = it }, onFoundShow = { opened = "found" })
        advanceUntilIdle()

        assertEquals(PodcastSamples.theDaily.id.value, opened)
    }

    @Test
    fun aShowTheLibraryHasWithFollowOff_orHasnt_opensTheFeed() = test { viewModel ->
        val hardFork = DirectoryShow(PodcastSamples.hardFork.directoryId, "Hard Fork", "NY Times", "https://e.com/h")
        val opened = mutableListOf<Any>()

        viewModel.onShowClick(hardFork, onLibraryShow = { opened += it }, onFoundShow = { opened += "found" })
        viewModel.onShowClick(stranger, onLibraryShow = { opened += it }, onFoundShow = { opened += "found" })
        advanceUntilIdle()

        assertEquals(listOf("found", "found"), opened)
    }

    @Test
    fun anEpisodeTheLibraryHas_opensItsOwnScreen_andAnyOtherTheOneFromSearch() = test { viewModel ->
        val inLibrary = PodcastSamples.theDailyEpisodes.first()
        val found = FoundEpisode(theDaily, commute.episode.copy(audioUrl = inLibrary.audioUrl))
        val opened = mutableListOf<Any>()

        viewModel.onEpisodeClick(found, onLibraryEpisode = { opened += it }, onFoundEpisode = { opened += "found" })
        viewModel.onEpisodeClick(commute, onLibraryEpisode = { opened += it }, onFoundEpisode = { opened += "found" })
        advanceUntilIdle()

        assertEquals(listOf<Any>(inLibrary.id.value, "found"), opened)
    }

    private companion object {
        const val PAUSE = 400L
        val WORDS = SearchWords(shows = "Shows", episodes = "Episodes", unknownEpisode = "Unknown episode")
    }
}
