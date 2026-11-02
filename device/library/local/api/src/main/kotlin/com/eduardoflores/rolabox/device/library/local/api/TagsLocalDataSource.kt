package com.eduardoflores.rolabox.device.library.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.LibraryError

/** The tags inside the files. Only the tags' bytes are read, and the files are never written (ADR-024). */
interface TagsLocalDataSource {
    /**
     * What [file]'s tags say. A file with no tags, or in a format the parser doesn't read, has empty [FileTags].
     * A `Left` is [LibraryError.TagsMalformed] or [LibraryError.FileUnreadable].
     */
    suspend fun readTags(file: SongFile): Either<LibraryError, FileTags>
}
