package com.eduardoflores.rolabox.device.playback.api

import com.eduardoflores.rolabox.device.library.api.LibraryError

sealed interface PlaybackError {
    /** The list to play couldn't be read from the library. */
    data class Library(val cause: LibraryError) : PlaybackError

    /** The item to start from isn't in the list it was supposed to be selected from. */
    data class ItemNotInSource(val itemId: PlaybackItemId, val source: PlaybackSource) : PlaybackError

    /** The player couldn't be reached. Nothing is loaded after it. */
    data object PlayerUnavailable : PlaybackError
}
