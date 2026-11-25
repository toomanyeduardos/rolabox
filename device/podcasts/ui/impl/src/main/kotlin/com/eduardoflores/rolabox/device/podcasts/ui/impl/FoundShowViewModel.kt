package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.api.ShowInLibrary
import com.eduardoflores.rolabox.device.podcasts.api.episodeOrder
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchShowKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A show opened from search (frame 32): its whole [feed], and the show as the library has it, [inLibrary], when it
 * does, with Follow Off, since one with Follow On opens the library's own screen (ADR-031, Search).
 *
 * Its episodes are listed in [order]: the library's, with the user's choice, when the library has the show, else the
 * one the feed asks for (ADR-043). [listed] is that order, as indices into the feed's episodes, and
 * [startingEpisode] the position in it of the episode the list opens on, or `null` to open on its first row.
 */
internal data class FoundShowContent(val feed: ShowFeed, val inLibrary: ShowInLibrary?) {
    val isFollowed: Boolean get() = inLibrary?.show?.isFollowed == true

    val order: EpisodeOrder = inLibrary?.show?.episodeOrder ?: feed.kind.episodeOrder

    val listed: List<Int> = listedIndices(feed.episodes.map { it.publishedAt }, order)

    // An episode the library hasn't was never played.
    val startingEpisode: Int? = startingEpisodeOf(
        listed.map { inLibrary?.episodes?.getOrNull(it)?.playedState ?: PlayedState.Unplayed },
        order,
    )
}

/** Where center on an episode of a show from search goes: the library's own Episode screen, or one from search. */
internal sealed interface FoundEpisodeTarget {
    data class Library(val episodeId: Long) : FoundEpisodeTarget

    data class Search(val show: DirectoryShow, val episode: DirectoryEpisode) : FoundEpisodeTarget
}

/**
 * What a show opened from search shows (frame 32): Follow, then every episode of its feed, in its order (ADR-021,
 * ADR-043).
 * [FoundShowViewModelImpl] implements it.
 */
internal abstract class FoundShowViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<FoundShowContent>>

    /** Follow couldn't be changed. The row keeps its value, and the screen says so once (ADR-007). */
    abstract val followFailed: StateFlow<Boolean>

    /**
     * Center on Follow: On adds the show to the library with every episode of its feed it hasn't, even when the library
     * has the show, and Off turns it off, in place.
     */
    abstract fun onFollowClick()

    abstract fun onFollowFailureShown()

    /** Where center on the episode at [index] of the list goes, in its order, or `null` when there is none. */
    abstract fun episodeTarget(index: Int): FoundEpisodeTarget?
}

/** The show is the one of the entry's key, given when the entry creates the ViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = FoundShowViewModelImpl.Factory::class)
internal class FoundShowViewModelImpl @AssistedInject constructor(
    @Assisted key: PodcastSearchShowKey,
    directory: PodcastDirectory,
    private val podcastsRepository: PodcastsRepository,
) : FoundShowViewModel() {
    override val uiState: StateFlow<ListUiState<FoundShowContent>> = directory.feedOf(key.toDirectoryShow())
        .flatMapLatest { read ->
            read.fold(
                ifLeft = { flowOf<Either<PodcastError, FoundShowContent>>(Either.Left(it)) },
                ifRight = { feed ->
                    podcastsRepository.observeInLibrary(feed).map { inLibrary ->
                        // A library that can't be read is as if it hadn't the show: the feed is still listed.
                        Either.Right(FoundShowContent(feed, inLibrary.getOrNull()))
                    }
                },
            )
        }
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    private val followFailedState = MutableStateFlow(false)
    override val followFailed: StateFlow<Boolean> = followFailedState.asStateFlow()

    override fun onFollowClick() {
        val content = loaded() ?: return
        viewModelScope.launch {
            val followed = content.inLibrary?.show?.takeIf { it.isFollowed }
            // On always adds the feed, whether or not the library has the show: one there with Follow Off has only
            // what was played of it, and following lists the rest (ADR-031, Search). Off is the switch alone.
            val changed = if (followed == null) {
                podcastsRepository.follow(content.feed)
            } else {
                podcastsRepository.setFollowed(followed.id, followed = false)
            }
            changed.onLeft { followFailedState.value = true }
        }
    }

    override fun onFollowFailureShown() {
        followFailedState.value = false
    }

    override fun episodeTarget(index: Int): FoundEpisodeTarget? {
        val content = loaded()
        val inFeed = content?.listed?.getOrNull(index) ?: return null
        val episode = content.feed.episodes[inFeed]
        val inLibrary = content.inLibrary?.episodes?.getOrNull(inFeed)
        return inLibrary?.let { FoundEpisodeTarget.Library(it.id.value) }
            ?: FoundEpisodeTarget.Search(content.feed.show, episode)
    }

    private fun loaded(): FoundShowContent? = (uiState.value as? ListUiState.Loaded)?.value

    @AssistedFactory
    interface Factory {
        fun create(key: PodcastSearchShowKey): FoundShowViewModelImpl
    }
}
