package com.eduardoflores.rolabox.auth.ui.impl

import androidx.lifecycle.viewModelScope
import com.eduardoflores.rolabox.auth.data.api.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Offline mode is already off for a signed-in user (ADR-008, rule 7), so signing out leaves the user
 * signed out and not offline: the next start is Sign in, not Home.
 */
@HiltViewModel
internal class SignOutViewModelImpl @Inject constructor(private val authRepository: AuthRepository) :
    SignOutViewModel() {
    private val _uiState = MutableStateFlow(SignOutUiState())
    override val uiState: StateFlow<SignOutUiState> = _uiState.asStateFlow()

    override fun onSignOutClick() {
        val current = _uiState.value
        if (current.isLoading || current.isFinished) return

        _uiState.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            authRepository.signOut().fold(
                ifLeft = { _ -> _uiState.update { it.copy(isLoading = false, hasError = true) } },
                ifRight = { _ -> _uiState.update { it.copy(isLoading = false, isFinished = true) } },
            )
        }
    }
}
