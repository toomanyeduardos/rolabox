package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.playback.api.EpisodeNetwork
import com.eduardoflores.rolabox.device.playback.api.PlaybackController
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.api.PlaybackSource
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchEpisodeKey
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

/** An episode from search, found in its show's feed: what Play adds to the library. */
private data class FoundInFeed(val show: DirectoryShow, val episode: DirectoryEpisode)

/**
 * The Episode screen (frame 26) of an episode from search that isn't in the library (ADR-031, Search). It reads the
 * show's feed, finds the episode there, and offers Play: center adds the episode and its show to the library, with
 * Follow Off, and plays it, which downloads it (ADR-031, rule 8). The episode is the one of the entry's key.
 */
@HiltViewModel(assistedFactory = FoundEpisodeViewModelImpl.Factory::class)
internal class FoundEpisodeViewModelImpl @AssistedInject constructor(
    @Assisted key: PodcastSearchEpisodeKey,
    savedState: SavedStateHandle,
    directory: PodcastDirectory,
    private val podcastsRepository: PodcastsRepository,
    private val playbackController: PlaybackController,
    episodeNetwork: EpisodeNetwork,
) : EpisodeViewModel() {
    private var found: FoundInFeed? = null

    override val uiState: StateFlow<ListUiState<EpisodeContent>> = combine(
        directory.feedOf(key.show.toDirectoryShow()),
        episodeNetwork.observeNeedsWifi(),
    ) { read, needWifi ->
        read.fold(
            ifLeft = { error ->
                when (error) {
                    PodcastError.NoConnection -> ListUiState.NoConnection
                    else -> ListUiState.FeedUnreadable
                }
            },
            ifRight = { feed ->
                val episode = feed.episodeOf(key.guid, key.audioUrl)
                if (episode == null) {
                    ListUiState.NotInFeed
                } else {
                    found = FoundInFeed(feed.show, episode)
                    ListUiState.Loaded(foundEpisodeContent(feed.show, episode, needWifi))
                }
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    private val savedWheel = SavedEpisodeWheel(savedState, viewModelScope)
    override val wheel: StateFlow<EpisodeWheel> = savedWheel.wheel

    private val playFailureState = MutableStateFlow<EpisodePlayFailure?>(null)
    override val playFailure: StateFlow<EpisodePlayFailure?> = playFailureState.asStateFlow()

    override fun onTurn(steps: Int) {
        val rowCount = (uiState.value as? ListUiState.Loaded)?.value?.rows?.size ?: return
        savedWheel.turn(steps, rowCount)
    }

    override fun onScrollableLinesChange(lines: Int) = savedWheel.setScrollableLines(lines)

    override fun onCenter(onPlaying: () -> Unit, onAllowMobileData: () -> Unit) {
        val rows = (uiState.value as? ListUiState.Loaded)?.value?.rows ?: return
        val found = found ?: return
        when (rows.getOrNull(savedWheel.current().row.coerceIn(0, rows.lastIndex))) {
            EpisodeRow.NeedsWifi -> onAllowMobileData()
            null -> Unit
            else -> play(found, onPlaying)
        }
    }

    // Playing from search is two calls: the repository adds the episode and its show, then playback plays it
    // (ADR-031, The layout).
    private fun play(found: FoundInFeed, onPlaying: () -> Unit) {
        viewModelScope.launch {
            val episodeId = podcastsRepository.addEpisode(found.show, found.episode).getOrNull()
            if (episodeId == null) {
                playFailureState.value = EpisodePlayFailure.EpisodeUnavailable
                return@launch
            }
            playbackController.play(
                source = PlaybackSource.Episode(episodeId),
                from = PlaybackItemId.Episode(episodeId),
                resume = false,
            ).fold(
                ifLeft = { error -> playFailureState.value = error.toEpisodePlayFailure() },
                ifRight = { onPlaying() },
            )
        }
    }

    override fun onPlayFailureShown() {
        playFailureState.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(key: PodcastSearchEpisodeKey): FoundEpisodeViewModelImpl
    }
}
