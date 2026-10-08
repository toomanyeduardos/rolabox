package com.eduardoflores.rolabox.device.playback.api

import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.api.SongId

sealed interface PlaybackError {
    /** The list to play couldn't be read from the library. */
    data class Library(val cause: LibraryError) : PlaybackError

    /** The song to start from isn't in the list it was supposed to be selected from. */
    data class SongNotInSource(val songId: SongId, val source: PlaybackSource) : PlaybackError
}
