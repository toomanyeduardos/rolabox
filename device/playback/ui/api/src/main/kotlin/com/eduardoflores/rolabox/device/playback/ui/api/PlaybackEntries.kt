package com.eduardoflores.rolabox.device.playback.ui.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of the playback screens (ADR-020): it adds their entries, and takes every way out
 * of them as a lambda. The device injects it and maps each exit to a key and a push, as the part's
 * parent, and it is the only module that names these screens' keys outside the part.
 *
 * These are device screens: they only receive the wheel's events (ADR-018, rule 3). The part has no
 * full screens, so it has no entries for the app stack.
 */
interface PlaybackEntries {
    /**
     * Adds the entry of [NowPlayingKey], for the screen stack inside the display. Now Playing has no way
     * out of its own yet: MENU and the playback buttons are handled by the host and the device.
     */
    fun screenStackEntries(scope: EntryProviderScope<NavKey>)
}
