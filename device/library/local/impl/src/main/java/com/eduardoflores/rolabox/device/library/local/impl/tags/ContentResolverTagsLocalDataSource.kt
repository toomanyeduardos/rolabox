package com.eduardoflores.rolabox.device.library.local.impl.tags

import android.content.Context
import android.net.Uri
import android.util.Log
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.NonFatalReport
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
    private val report: NonFatalReport,
) : TagsLocalDataSource {
    override suspend fun readTags(file: SongFile): Either<LibraryError, FileTags> = withContext(ioDispatcher) {
        catchNamed({ error -> toFileUnreadable(error, file.uri, report) }) { parse(file.uri) }
            .fold(ifLeft = { it.left() }, ifRight = { it?.right() ?: malformed(file.uri, report) })
    }

    private fun parse(uri: String): FileTags? {
        val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
            ?: throw FileNotFoundException(uri)
        return descriptor.use {
            FileInputStream(it.fileDescriptor).channel.use { channel -> parseTags(ChannelByteSource(channel)) }
        }
    }
}

// Our own parser said no, so a parser bug shows up here too (ADR-030). The URI isn't reported.
internal fun malformed(uri: String, report: NonFatalReport): Either<LibraryError, FileTags> {
    Log.w(TAG, "Malformed tags in $uri")
    report.report("LibraryError.TagsMalformed", null)
    return LibraryError.TagsMalformed(uri).left()
}

internal fun toFileUnreadable(error: Throwable, uri: String, report: NonFatalReport): LibraryError? = when (error) {
    // The file went away, the permission did, or the system couldn't open it.
    is IOException, is SecurityException, is IllegalArgumentException -> {
        Log.w(TAG, "Couldn't read $uri", error)
        // A file that went away, or a permission that did, is the phone's state, not a bug (ADR-030).
        if (error !is FileNotFoundException && error !is SecurityException) {
            report.report("LibraryError.FileUnreadable", error)
        }
        LibraryError.FileUnreadable(uri)
    }

    else -> null
}
