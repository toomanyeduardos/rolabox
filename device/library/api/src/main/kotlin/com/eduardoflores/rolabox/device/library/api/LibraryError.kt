package com.eduardoflores.rolabox.device.library.api

import com.eduardoflores.rolabox.common.storage.api.StorageError

sealed interface LibraryError {
    data class ArtistNotFound(val id: ArtistId) : LibraryError

    data class AlbumNotFound(val id: AlbumId) : LibraryError

    /** Wherever the library is kept, it couldn't be read. */
    data class Storage(val cause: StorageError) : LibraryError
}
