package com.eduardoflores.rolabox.auth.data.api

/**
 * [id] is stable across sign-in providers. [photoUrl] is a URL string, since `:api` modules are pure JVM.
 * [signInMethods] are the ways the account signs in: a password, Google, or both when linked (ADR-013).
 */
data class AuthUser(
    val id: String,
    val displayName: String?,
    val photoUrl: String?,
    val signInMethods: Set<SignInMethod> = emptySet(),
)

/** A way an account signs in. Asking a user to re-authenticate offers the ones their account has (ADR-037). */
enum class SignInMethod {
    PASSWORD,
    GOOGLE,
}
