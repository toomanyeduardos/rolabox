package com.eduardoflores.rolabox.device.playback.api

import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError

sealed interface PlaybackError {
    /** The list to play couldn't be read from the library. */
    data class Library(val cause: LibraryError) : PlaybackError

    /** The episode to play, its show or its position couldn't be read from the podcasts (ADR-031). */
    data class Podcasts(val cause: PodcastError) : PlaybackError

    /** The item to start from isn't in the list it was supposed to be selected from. */
    data class ItemNotInSource(val itemId: PlaybackItemId, val source: PlaybackSource) : PlaybackError

    /** The list to shuffle has no items, so there is nothing to start from. */
    data class NothingToPlay(val source: PlaybackSource) : PlaybackError

    /** The player couldn't be reached. Nothing is loaded after it. */
    data object PlayerUnavailable : PlaybackError

    /**
     * The episode isn't downloaded, and the phone hasn't room for it: less free space than its size, as its feed gives
     * it, plus a margin (ADR-031, Downloads). Nothing is downloaded or loaded.
     */
    data object StorageFull : PlaybackError

    /**
     * What an episode that ran out of bytes waits for, reported by [PlaybackState.waitingFor] (ADR-031, Stream while
     * downloading). [MobileDataOff] is also what [PlaybackController.play] returns for an episode with no download
     * while episodes need Wi-Fi ([EpisodeNetwork]).
     */
    sealed interface Waiting : PlaybackError

    /** No network: the episode goes on once there is one. */
    data object NoConnection : Waiting

    /** Use Mobile Data is Off and the network is metered: the episode goes on once Wi-Fi or the setting allows it. */
    data object MobileDataOff : Waiting

    /**
     * Why the engine couldn't play an item. No call returns these: [PlaybackState] reports them as they happen, since
     * they come while the queue plays (ADR-023).
     */
    sealed interface Unplayable : PlaybackError {
        /** The file is no longer where the library said it was. */
        data object Missing : Unplayable

        /** The file is corrupt or truncated. */
        data object Damaged : Unplayable

        /** The file's format or codec can't be played on this phone. */
        data object Unsupported : Unplayable

        /** The file is protected by DRM, as an `.m4p` is. */
        data object Protected : Unplayable

        /** The file is there, but reading it failed. */
        data object Unreadable : Unplayable

        /**
         * The server refused the audio, with a 401, 403 or 410, as a publisher does a paid episode kept in its public
         * feed (ADR-031, Errors). It is the publisher's choice, not a bug.
         */
        data object Refused : Unplayable

        /** The file may be fine, and the engine failed for a reason of its own. */
        data object EngineFailed : Unplayable
    }
}
