package com.eduardoflores.rolabox.core.auth.api

sealed interface AuthState {
    data object SignedOut : AuthState

    data class SignedIn(val user: AuthUser) : AuthState
}
