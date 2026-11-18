package com.eduardoflores.rolabox.device.podcasts.ui.api

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

/**
 * The entry contract of the podcasts screens (ADR-020, rule 11; ADR-031): it adds their entries, and takes every way
 * out of them as a lambda with ids. The device injects it and maps each exit to a key and a push, as the part's
 * parent, and it is the only module that names these screens' keys outside the part.
 *
 * These are device screens: they only receive the wheel's turn and center (ADR-018, rule 3). The part has no full
 * screens, so it has no entries for the app stack.
 */
interface PodcastsEntries {
    /**
     * Adds the entries of [PodcastsKey], [PodcastOwnerKey], [PodcastShowKey] and [PodcastEpisodeKey], for the screen
     * stack inside the display.
     *
     * The exits: center on Search in Podcasts is [onSearchClick]; on an owner, [onOwnerClick] with its grouping key;
     * on a show of an owner, [onShowClick]; on an episode of a show, [onEpisodeClick], which opens its
     * [PodcastEpisodeKey]; and on Play, Resume or Play from Start of an episode, [onPlaying], once it plays, which
     * opens Now Playing; and on Needs Wi-Fi of an episode, [onAllowMobileData], which opens the podcasts' settings,
     * where Use Mobile Data is (ADR-031, frame 36). Follow, the first row of a show, is toggled in place and isn't an
     * exit.
     */
    @Suppress("LongParameterList") // One lambda per exit, so the contract lists how its screens are left.
    fun screenStackEntries(
        scope: EntryProviderScope<NavKey>,
        onSearchClick: () -> Unit,
        onOwnerClick: (ownerId: String) -> Unit,
        onShowClick: (showId: Long) -> Unit,
        onEpisodeClick: (episodeId: Long) -> Unit,
        onPlaying: () -> Unit,
        onAllowMobileData: () -> Unit,
    )
}
