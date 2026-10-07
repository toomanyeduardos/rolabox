package com.eduardoflores.rolabox.auth.ui.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Where the auth flow starts. */
@Serializable
data object SignInKey : NavKey

@Serializable
data object CreateAccountKey : NavKey

/** Reset password. [email] is what the user had typed on Sign in, and may be empty. */
@Serializable
data class ResetPasswordKey(val email: String = "") : NavKey

/** Asks the signed-in user to confirm signing out. It's opened from Settings, over the device. */
@Serializable
data object SignOutKey : NavKey
