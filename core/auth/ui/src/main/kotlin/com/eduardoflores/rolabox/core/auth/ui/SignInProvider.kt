package com.eduardoflores.rolabox.core.auth.ui

/**
 * The sign-in providers whose UI step this module can run. Adding one is the checklist of ADR-014:
 * a case of `SignInCredential`, its branch in the `:impl`, a value here with its step and button,
 * and its configuration in [SignInConfig].
 */
enum class SignInProvider { Google, }

/**
 * The configuration the provider steps need, which only `:app` knows (ADR-009, rule 4). `:app`
 * provides it through Hilt, and a feature's ViewModel injects it and passes it on. This module
 * injects nothing (ADR-014).
 */
data class SignInConfig(
    /** Credential Manager asks Google for an ID token meant for this Web OAuth client (Firebase's own). */
    val googleWebClientId: String,
)
