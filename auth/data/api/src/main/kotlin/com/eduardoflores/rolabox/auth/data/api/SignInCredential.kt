package com.eduardoflores.rolabox.auth.data.api

/**
 * What the UI's sign-in step produced (ADR-009). The UI gets it from the provider's SDK, and the
 * repository exchanges it for a session. Each provider is a case.
 */
sealed interface SignInCredential {
    /** An ID token from Google sign-in (Credential Manager's `GoogleIdTokenCredential.idToken`). */
    data class GoogleIdToken(val token: String) : SignInCredential
}
