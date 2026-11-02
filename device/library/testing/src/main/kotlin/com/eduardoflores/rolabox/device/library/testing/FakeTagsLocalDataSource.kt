package com.eduardoflores.rolabox.device.library.testing

import arrow.core.Either
import arrow.core.right
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.TagsLocalDataSource
import kotlinx.coroutines.CompletableDeferred

/** Tags that tests set by a file's URI. A file with none set has no tags. With [hold] set, a read waits for it. */
class FakeTagsLocalDataSource : TagsLocalDataSource {
    val results = mutableMapOf<String, Either<LibraryError, FileTags>>()
    val reads = mutableListOf<String>()
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun readTags(file: SongFile): Either<LibraryError, FileTags> {
        reads += file.uri
        hold?.await()
        return results[file.uri] ?: FileTags().right()
    }
}
