package com.eduardoflores.rolabox.device.library.local.impl.tags

import android.content.Context
import android.net.Uri
import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.IO
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.library.api.LibraryError
import com.eduardoflores.rolabox.device.library.local.api.FileTags
import com.eduardoflores.rolabox.device.library.local.api.SongFile
import com.eduardoflores.rolabox.device.library.local.api.TagsLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private const val TAG = "LibraryTags"

/**
 * Opens a file from its `content://` URI, so no path is needed under scoped storage, and parses its tags. What goes
 * wrong is logged here, and the user doesn't see it: the song keeps the values of its other sources (ADR-024).
 */
internal class ContentResolverTagsLocalDataSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : TagsLocalDataSource {
    override suspend fun readTags(file: SongFile): Either<LibraryError, FileTags> = withContext(ioDispatcher) {
        catchNamed({ error -> toFileUnreadable(error, file.uri) }) { parse(file.uri) }
            .fold(ifLeft = { it.left() }, ifRight = { it?.right() ?: malformed(file.uri) })
    }

    private fun parse(uri: String): FileTags? {
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: throw FileNotFoundException(uri)
        return descriptor.use {
            FileInputStream(it.fileDescriptor).channel.use { channel -> parseTags(ChannelByteSource(channel)) }
        }
    }
}

private fun malformed(uri: String): Either<LibraryError, FileTags> {
    Log.w(TAG, "Malformed tags in $uri")
    return LibraryError.TagsMalformed(uri).left()
}

private fun toFileUnreadable(error: Throwable, uri: String): LibraryError? = when (error) {
    // The file went away, the permission did, or the system couldn't open it.
    is IOException, is SecurityException, is IllegalArgumentException -> {
        Log.w(TAG, "Couldn't read $uri", error)
        LibraryError.FileUnreadable(uri)
    }

    else -> null
}
