package com.eduardoflores.rolabox.device.podcasts.ui.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.PodcastsRepository
import com.eduardoflores.rolabox.device.podcasts.api.Show
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The owner's name for the title: [name] is `null` for the shows with no owner, and the title says so. */
internal data class OwnerTitle(val name: String?)

/**
 * What an owner's screen shows (frame 24): its shows A to Z, as the repository sorts them, even when it has one
 * (ADR-021). [OwnerViewModelImpl] implements it.
 */
internal abstract class OwnerViewModel : ViewModel() {
    abstract val uiState: StateFlow<ListUiState<List<Show>>>

    /** The screen's title, the owner's name as Podcasts lists it. `null` until it is read, and when it can't be. */
    abstract val title: StateFlow<OwnerTitle?>
}

/**
 * The owner is the one of the entry's key, so it is given when the entry creates the ViewModel, as the key holds it:
 * KSP can't name a factory's parameter of a value class.
 */
@HiltViewModel(assistedFactory = OwnerViewModelImpl.Factory::class)
internal class OwnerViewModelImpl @AssistedInject constructor(
    @Assisted ownerKey: String,
    podcastsRepository: PodcastsRepository,
) : OwnerViewModel() {
    private val ownerId = OwnerId(ownerKey)

    override val uiState: StateFlow<ListUiState<List<Show>>> = podcastsRepository.observeShowsByOwner(ownerId)
        .asListState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ListUiState.Loading)

    // An owner that can't be read leaves the header empty: the list already says why.
    override val title: StateFlow<OwnerTitle?> = podcastsRepository.observeOwners()
        .map { result ->
            result.fold(
                ifLeft = { null },
                ifRight = { owners -> owners.firstOrNull { it.id == ownerId }?.let { OwnerTitle(it.name) } },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    @AssistedFactory
    interface Factory {
        fun create(ownerKey: String): OwnerViewModelImpl
    }
}
