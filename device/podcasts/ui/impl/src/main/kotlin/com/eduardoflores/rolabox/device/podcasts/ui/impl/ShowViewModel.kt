package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.device.podcasts.api.Episode
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A show of the library and the episodes it lists, newest first: every one with Follow On, and only those with a
 * download, done or in progress, with Follow Off (ADR-031). The repository decides which.
 */
internal data class ShowContent(val show: Show, val episodes: List<Episode>)

/**
 * What a show's screen shows (frame 25): Follow, then its episodes (ADR-021). [ShowViewModelImpl] implements it.
 */
internal abstract class ShowViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<ShowContent>>

    /** Follow couldn't be changed. The row keeps its value, and the screen says so once (ADR-007). */
    abstract val followFailed: StateFlow<Boolean>

    /** Center on Follow: it turns On or Off in place, like a setting. */
    abstract fun onFollowClick()

    abstract fun onFollowFailureShown()
}

/**
 * The show is the one of the entry's key, so it is given when the entry creates the ViewModel, as the key holds it:
 * KSP can't name a factory's parameter of a value class.
 */
@HiltViewModel(assistedFactory = ShowViewModelImpl.Factory::class)
internal class ShowViewModelImpl @AssistedInject constructor(
    @Assisted showKey: Long,
    private val podcastsRepository: PodcastsRepository,
) : ShowViewModel() {
    private val showId = ShowId(showKey)

    override val uiState: StateFlow<ListUiState<ShowContent>> = combine(
        podcastsRepository.observeShow(showId),
        podcastsRepository.observeEpisodesByShow(showId),
    ) { show, episodes -> contentOf(show, episodes) }
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    private val followFailedState = MutableStateFlow(false)
    override val followFailed: StateFlow<Boolean> = followFailedState.asStateFlow()

    override fun onFollowClick() {
        val show = (uiState.value as? ListUiState.Loaded)?.value?.show ?: return
        viewModelScope.launch {
            podcastsRepository.setFollowed(showId, !show.isFollowed).onLeft { followFailedState.value = true }
        }
    }

    override fun onFollowFailureShown() {
        followFailedState.value = false
    }

    @AssistedFactory
    interface Factory {
        fun create(showKey: Long): ShowViewModelImpl
    }
}

private fun <E> contentOf(show: Either<E, Show>, episodes: Either<E, List<Episode>>): Either<E, ShowContent> =
    either { ShowContent(show.bind(), episodes.bind()) }
