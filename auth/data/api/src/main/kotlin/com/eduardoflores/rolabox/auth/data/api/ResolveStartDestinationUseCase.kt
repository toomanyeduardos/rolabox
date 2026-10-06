package com.eduardoflores.rolabox.auth.data.api

/**
 * Where the app opens once it has started, as auth sees it: whether the user may go past the auth
 * flow. What is shown for each answer is `:app`'s decision, so auth doesn't name the device (ADR-020).
 */
enum class StartDestination {
    /** The user is signed in, or chose to use the app without an account. */
    AccessGranted,

    /** Neither: the user is asked to sign in. */
    SignIn,
}

/**
 * Decides where the app opens: access is granted to a signed-in user, and to one who chose to use
 * the app without an account, and everyone else signs in. Every build has accounts, so this is
 * decided only at runtime (ADR-008).
 *
 * This combines two areas (auth and user data), so it's a use case (ADR-001).
 */
interface ResolveStartDestinationUseCase {
    suspend operator fun invoke(): StartDestination
}
