package com.eduardoflores.rolabox.auth.data.api

sealed interface AuthState {
    data object SignedOut : AuthState

    data class SignedIn(val user: AuthUser) : AuthState
}
