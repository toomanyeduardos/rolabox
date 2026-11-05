package com.eduardoflores.rolabox.device.playback.api

import com.eduardoflores.rolabox.device.library.api.LibraryError

sealed interface PlaybackError {
    /** The list to play couldn't be read from the library. */
    data class Library(val cause: LibraryError) : PlaybackError

    /** The item to start from isn't in the list it was supposed to be selected from. */
    data class ItemNotInSource(val itemId: PlaybackItemId, val source: PlaybackSource) : PlaybackError

    /** The list to shuffle has no items, so there is nothing to start from. */
    data class NothingToPlay(val source: PlaybackSource) : PlaybackError

    /** The player couldn't be reached. Nothing is loaded after it. */
    data object PlayerUnavailable : PlaybackError

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

        /** The file may be fine, and the engine failed for a reason of its own. */
        data object EngineFailed : Unplayable
    }
}
