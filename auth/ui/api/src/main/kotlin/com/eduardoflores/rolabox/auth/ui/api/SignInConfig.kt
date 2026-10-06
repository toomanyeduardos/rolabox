package com.eduardoflores.rolabox.auth.ui.api

/**
 * The configuration the sign-in provider steps need, which only `:app` knows (ADR-009, rule 4).
 * `:app` provides it through Hilt, and the ViewModels of the auth screens inject it and pass it on
 * to the step.
 */
data class SignInConfig(
    /** Credential Manager asks Google for an ID token meant for this Web OAuth client (Firebase's own). */
    val googleWebClientId: String,
)
