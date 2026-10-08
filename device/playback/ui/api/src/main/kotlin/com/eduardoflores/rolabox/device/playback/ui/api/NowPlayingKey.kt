package com.eduardoflores.rolabox.device.playback.ui.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// The keys of the playback screens on the screen stack inside the display (ADR-018). They carry the ids
// of what they show, not models (ADR-012, rule 2).

/**
 * Now Playing: the loaded song, its place in the queue and how far it is. Playback is one state, so the key
 * carries no id.
 */
@Serializable
data object NowPlayingKey : NavKey
