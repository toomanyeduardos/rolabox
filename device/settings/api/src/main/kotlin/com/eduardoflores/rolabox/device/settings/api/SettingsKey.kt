package com.eduardoflores.rolabox.device.settings.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The keys of the settings screens on the screen stack inside the display (ADR-018, rule 11). They
// are device screens: only a contributed section's row leaves the display.

/** The settings list. */
@Serializable
data object SettingsKey : NavKey

/** The rows of the main menu that can be hidden, each shown or hidden. */
@Serializable
data object MainMenuSettingsKey : NavKey

/** The app's name and version, and the number of songs in the library. */
@Serializable
data object AboutKey : NavKey
