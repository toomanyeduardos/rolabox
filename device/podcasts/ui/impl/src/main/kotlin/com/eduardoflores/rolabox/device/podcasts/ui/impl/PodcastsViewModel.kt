package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.podcasts.api.Owner
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * What Podcasts shows (frames 22 and 23): the owners of the library, A to Z, as the repository sorts them (ADR-021).
 * [PodcastsViewModelImpl] implements it. Search, the first row, is the screen's own.
 */
internal abstract class PodcastsViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<List<Owner>>>
}

@HiltViewModel
internal class PodcastsViewModelImpl @Inject constructor(podcastsRepository: PodcastsRepository) : PodcastsViewModel() {
    override val uiState: StateFlow<ListUiState<List<Owner>>> = podcastsRepository.observeOwners()
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)
}
