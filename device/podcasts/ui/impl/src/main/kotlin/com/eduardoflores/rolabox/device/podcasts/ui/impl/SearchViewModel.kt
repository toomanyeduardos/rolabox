package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryResults
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What Search shows under its field (frames 29 to 34). */
internal sealed interface SearchUiState {
    /** Nothing searched: the text is shorter than a search (frame 29). */
    data object Empty : SearchUiState

    /** Shows, then Episodes (frames 30 and 31). */
    data class Found(val results: DirectoryResults) : SearchUiState

    /** The directory found nothing (frame 34). */
    data object NoResults : SearchUiState

    /** No connection: the text stays, and the search is sent again when one comes back (frame 33). */
    data object NoConnection : SearchUiState

    /** The directory refused for the rate of requests. It isn't sent again by itself (ADR-031, Search). */
    data object Busy : SearchUiState

    /** Any other answer of the directory that can't be used. */
    data object Failed : SearchUiState
}

/**
 * Search of the directory (ADR-031, Search): the screen gives it the text as it is typed, and it answers with what to
 * show (ADR-021). [SearchViewModelImpl] implements it.
 */
internal abstract class SearchViewModel : ViewModel() {
    /** Stays as it was while a newer text waits to be sent, so results update in place as the user types. */
    abstract val uiState: StateFlow<SearchUiState>

    /** The field's text changed. It is sent once typing has paused, and the search of an older text is cancelled. */
    abstract fun onTextChange(text: String)

    /** The keyboard's search key: the text is sent now, and again if it was, such as after the directory was busy. */
    abstract fun onSearchAction()

    /**
     * Center on a show: one in the library with Follow On opens its own screen, [onLibraryShow], and any other the
     * screen of a show from search, [onFoundShow], which lists its whole feed (ADR-031, Search).
     */
    abstract fun onShowClick(show: DirectoryShow, onLibraryShow: (showId: Long) -> Unit, onFoundShow: () -> Unit)

    /**
     * Center on an episode: one in the library opens its own screen, [onLibraryEpisode], and any other the Episode
     * screen of an episode from search, [onFoundEpisode].
     */
    abstract fun onEpisodeClick(
        found: FoundEpisode,
        onLibraryEpisode: (episodeId: Long) -> Unit,
        onFoundEpisode: () -> Unit,
    )
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class SearchViewModelImpl @Inject constructor(
    private val directory: PodcastDirectory,
    private val podcastsRepository: PodcastsRepository,
) : SearchViewModel() {
    /** The text to send, and whether to send it [now], with the search key. [sends] tells one send from the next. */
    private data class Query(val text: String, val now: Boolean = false, val sends: Int = 0)

    private val query = MutableStateFlow(Query(""))

    override val uiState: StateFlow<SearchUiState> = query
        .debounce { if (it.now) 0L else TYPING_PAUSE_MILLIS }
        .flatMapLatest { searchOf(it.text.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchUiState.Empty)

    /**
     * The search of [text], or nothing for a text too short to send (ADR-031, rule 1). Without a connection it isn't
     * sent: it says so, and is sent when the connection comes back.
     */
    private fun searchOf(text: String): Flow<SearchUiState> {
        if (text.length < MIN_SEARCH_LENGTH) return flowOf(SearchUiState.Empty)
        return directory.observeConnected().distinctUntilChanged().transformLatest { connected ->
            if (!connected) {
                emit(SearchUiState.NoConnection)
            } else {
                emit(directory.search(text).fold(ifLeft = ::stateOf, ifRight = ::stateOf))
            }
        }
    }

    override fun onTextChange(text: String) {
        // The same text again, such as the field's after a configuration change, is no new search.
        query.update { if (it.text == text) it else Query(text, sends = it.sends) }
    }

    override fun onSearchAction() {
        query.update { Query(it.text, now = true, sends = it.sends + 1) }
    }

    override fun onShowClick(show: DirectoryShow, onLibraryShow: (showId: Long) -> Unit, onFoundShow: () -> Unit) {
        viewModelScope.launch {
            // A library that can't be read is as if it hadn't the show: the feed still opens.
            val inLibrary = podcastsRepository.findShow(show).getOrNull()
            if (inLibrary?.isFollowed == true) onLibraryShow(inLibrary.id.value) else onFoundShow()
        }
    }

    override fun onEpisodeClick(
        found: FoundEpisode,
        onLibraryEpisode: (episodeId: Long) -> Unit,
        onFoundEpisode: () -> Unit,
    ) {
        viewModelScope.launch {
            val inLibrary = podcastsRepository.findEpisode(found.show, found.episode).getOrNull()
            if (inLibrary != null) onLibraryEpisode(inLibrary.value) else onFoundEpisode()
        }
    }

    private companion object {
        const val TYPING_PAUSE_MILLIS = 400L
        const val MIN_SEARCH_LENGTH = 2
    }
}

private fun stateOf(results: DirectoryResults): SearchUiState =
    if (results.isEmpty) SearchUiState.NoResults else SearchUiState.Found(results)

private fun stateOf(error: PodcastError): SearchUiState = when (error) {
    PodcastError.NoConnection -> SearchUiState.NoConnection
    PodcastError.DirectoryBusy -> SearchUiState.Busy
    else -> SearchUiState.Failed
}
