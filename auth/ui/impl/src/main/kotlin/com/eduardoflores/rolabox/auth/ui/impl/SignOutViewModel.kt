package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

internal data class SignOutUiState(
    val isLoading: Boolean = false,
    /** Signing out failed. Nothing changed, so the user is still signed in and can try again. */
    val hasError: Boolean = false,
    /** The user is signed out, and the screen should be left. */
    val isFinished: Boolean = false,
)

/**
 * What the Sign out screen shows, and what the user does on it (ADR-021).
 * [SignOutViewModelImpl] implements it.
 */
internal abstract class SignOutViewModel : ViewModel() {
    abstract val uiState: StateFlow<SignOutUiState>

    abstract fun onSignOutClick()
}
