package com.eduardoflores.rolabox.device.music.ui.impl

import androidx.lifecycle.ViewModel
import com.eduardoflores.rolabox.device.library.api.Progress
import kotlinx.coroutines.flow.StateFlow

/** What Music shows: its menu, or why there is no menu to show (ADR-024). */
internal sealed interface MusicMenuUiState {
    data object Loading : MusicMenuUiState

    /** The permission is missing and has never been asked for, so the screen asks. */
    data object PermissionNotAsked : MusicMenuUiState

    /** The permission is missing, and Android can still ask for it. */
    data object PermissionDenied : MusicMenuUiState

    /** The permission is missing, and Android won't ask again: only system settings can grant it. */
    data object PermissionPermanentlyDenied : MusicMenuUiState

    data object Scanning : MusicMenuUiState

    data object Empty : MusicMenuUiState

    /** The menu. [readingTags] is how far reading the tags has come, or null when that isn't running. */
    data class Menu(val readingTags: Progress?) : MusicMenuUiState

    data object Failed : MusicMenuUiState
}

/**
 * The state of the Music menu (ADR-021). [MusicMenuViewModelImpl] implements it. Whether Android can still
 * ask needs an Activity, so the route finds out and reports it here as `canAskAgain` (ADR-009).
 */
internal abstract class MusicMenuViewModel : ViewModel() {
    abstract val uiState: StateFlow<MusicMenuUiState>

    /** The screen is in the foreground, on opening or on coming back. */
    abstract fun onResumed(canAskAgain: Boolean)

    /** Android's prompt is about to show. */
    abstract fun onPermissionRequested()

    /** Android's prompt returned. */
    abstract fun onPermissionResult(canAskAgain: Boolean)
}
