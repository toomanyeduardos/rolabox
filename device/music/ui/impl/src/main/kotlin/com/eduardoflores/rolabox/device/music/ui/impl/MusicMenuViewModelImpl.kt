package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.viewModelScope
import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.LibraryRepository
import com.eduardoflores.rolabox.device.library.api.LibraryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
internal class MusicMenuViewModelImpl @Inject constructor(private val libraryRepository: LibraryRepository) :
    MusicMenuViewModel() {
    // True until the route reports, and while the prompt shows, so that the row under it never says "Settings".
    private val canAskAgain = MutableStateFlow(true)

    override val uiState: StateFlow<MusicMenuUiState> = combine(
        libraryRepository.observeStatus(),
        libraryRepository.observePermissionAsked(),
        canAskAgain,
    ) { status, asked, canAsk -> uiStateOf(status, asked, canAsk) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MusicMenuUiState.Loading)

    override fun onResumed(canAskAgain: Boolean) {
        this.canAskAgain.value = canAskAgain
        if (uiState.value.isPermissionMissing) libraryRepository.checkPermission()
    }

    override fun onPermissionRequested() {
        canAskAgain.value = true
        // If this fails the user is asked again the next time Music opens, which does no harm.
        viewModelScope.launch { libraryRepository.setPermissionAsked() }
    }

    override fun onPermissionResult(canAskAgain: Boolean) {
        this.canAskAgain.value = canAskAgain
        libraryRepository.checkPermission()
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private val MusicMenuUiState.isPermissionMissing: Boolean
    get() = this == MusicMenuUiState.PermissionNotAsked ||
        this == MusicMenuUiState.PermissionDenied ||
        this == MusicMenuUiState.PermissionPermanentlyDenied

private fun uiStateOf(
    status: Either<LibraryError, LibraryStatus>,
    asked: Either<LibraryError, Boolean>,
    canAskAgain: Boolean,
): MusicMenuUiState = status.fold(
    ifLeft = { MusicMenuUiState.Failed },
    ifRight = { current ->
        when (current) {
            LibraryStatus.PermissionMissing -> asked.fold(
                ifLeft = { MusicMenuUiState.Failed },
                ifRight = { wasAsked -> permissionState(wasAsked, canAskAgain) },
            )

            LibraryStatus.Scanning -> MusicMenuUiState.Scanning

            LibraryStatus.Empty -> MusicMenuUiState.Empty

            is LibraryStatus.Ready -> MusicMenuUiState.Menu(current.readingTags)
        }
    },
)

private fun permissionState(asked: Boolean, canAskAgain: Boolean): MusicMenuUiState = when {
    !asked -> MusicMenuUiState.PermissionNotAsked
    canAskAgain -> MusicMenuUiState.PermissionDenied
    else -> MusicMenuUiState.PermissionPermanentlyDenied
}
