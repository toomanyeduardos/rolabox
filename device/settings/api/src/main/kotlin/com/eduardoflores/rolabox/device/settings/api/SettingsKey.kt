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

/**
 * The podcasts' settings: Use Mobile Data (ADR-031, frame 35). The Episode screen opens it too, from an episode that
 * needs Wi-Fi (frame 36).
 */
@Serializable
data object PodcastSettingsKey : NavKey

/**
 * The app's name and version, the number of songs in the library, and the rows that open the privacy policy, the
 * open-source licences and an email for feedback.
 */
@Serializable
data object AboutKey : NavKey

/** The open-source libraries the app ships, each opening its licence. */
@Serializable
data object LicencesKey : NavKey

/** The licence of one open-source library, by the library's id in the list of [LicencesKey]. */
@Serializable
data class LicenceKey(val libraryId: String) : NavKey
