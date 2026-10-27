package com.eduardoflores.rolabox.device.settings.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
internal class AboutViewModelImpl @Inject constructor(library: LibraryRepository, appVersion: AppVersion) :
    AboutViewModel() {
    private val version = appVersion.name()

    override val uiState: StateFlow<AboutUiState> = library.observeSongs()
        .map { result ->
            AboutUiState(
                version = version,
                songs = result.fold(ifLeft = { SongCount.Unavailable }, ifRight = { SongCount.Loaded(it.size) }),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = AboutUiState(version = version),
        )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
