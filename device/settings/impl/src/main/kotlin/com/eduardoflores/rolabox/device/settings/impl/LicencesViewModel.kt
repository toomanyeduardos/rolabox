package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** What a licences screen shows: the list, or one library's licence. */
internal sealed interface LicencesUiState<out T> {
    data object Loading : LicencesUiState<Nothing>

    data class Loaded<T>(val value: T) : LicencesUiState<T>

    /** The list can't be read, or has no such library, and the screen says so (ADR-018). */
    data object Unavailable : LicencesUiState<Nothing>
}

/**
 * What Open-Source Licences shows: the libraries the app ships, by name (ADR-021). [LicencesViewModelImpl] implements
 * it.
 */
internal abstract class LicencesViewModel : ViewModel() {
    abstract val uiState: StateFlow<LicencesUiState<List<Licence>>>
}

@HiltViewModel
internal class LicencesViewModelImpl @Inject constructor(licences: Licences) : LicencesViewModel() {
    override val uiState: StateFlow<LicencesUiState<List<Licence>>> = flow {
        emit(licences.load().fold(ifLeft = { LicencesUiState.Unavailable }, ifRight = { LicencesUiState.Loaded(it) }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LicencesUiState.Loading)
}

/** What a library's licence shows (ADR-021). [LicenceViewModelImpl] implements it. */
internal abstract class LicenceViewModel : ViewModel() {
    abstract val uiState: StateFlow<LicencesUiState<Licence>>
}

@HiltViewModel(assistedFactory = LicenceViewModelImpl.Factory::class)
internal class LicenceViewModelImpl @AssistedInject constructor(@Assisted libraryId: String, licences: Licences) :
    LicenceViewModel() {
    override val uiState: StateFlow<LicencesUiState<Licence>> = flow {
        val licence = licences.load().getOrNull()?.firstOrNull { it.id == libraryId }
        emit(licence?.let { LicencesUiState.Loaded(it) } ?: LicencesUiState.Unavailable)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LicencesUiState.Loading)

    @AssistedFactory
    interface Factory {
        fun create(libraryId: String): LicenceViewModelImpl
    }
}

private const val STOP_TIMEOUT_MILLIS = 5_000L
