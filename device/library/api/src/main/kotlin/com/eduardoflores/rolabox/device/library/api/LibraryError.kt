package com.eduardoflores.rolabox.device.library.api

import com.eduardoflores.rolabox.common.storage.api.StorageError

sealed interface LibraryError {
    data class ArtistNotFound(val id: ArtistId) : LibraryError

    data class AlbumNotFound(val id: AlbumId) : LibraryError

    data class GenreNotFound(val id: GenreId) : LibraryError

    /** Wherever the library is kept, it couldn't be read. */
    data class Storage(val cause: StorageError) : LibraryError

    /** The file at [uri] says it has tags, but they make no sense. Its song keeps the values of the fallbacks. */
    data class TagsMalformed(val uri: String) : LibraryError

    /** The file at [uri] couldn't be opened or read right now. Its tags are tried again by the next scan. */
    data class FileUnreadable(val uri: String) : LibraryError
}
