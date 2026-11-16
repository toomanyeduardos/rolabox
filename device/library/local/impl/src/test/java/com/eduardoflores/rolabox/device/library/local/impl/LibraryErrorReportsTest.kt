package com.eduardoflores.rolabox.device.library.local.impl

import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.eduardoflores.rolabox.device.library.local.impl.index.toLibraryError as toIndexError
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.toLibraryError as toMediaStoreError
import com.eduardoflores.rolabox.device.library.local.impl.tags.malformed
import com.eduardoflores.rolabox.device.library.local.impl.tags.toFileUnreadable
import java.io.FileNotFoundException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ADR-030: what the library reports when it fails, and what it doesn't, since it's the phone's state. */
@RunWith(RobolectricTestRunner::class)
class LibraryErrorReportsTest {
    private val report = FakeNonFatalReport()

    @Test
    fun `a corrupt library database is reported`() {
        toIndexError(SQLiteDatabaseCorruptException(), report)

        assertEquals(listOf("StorageError.Corrupted Room"), report.kinds)
    }

    @Test
    fun `any other database error isn't`() {
        toIndexError(SQLiteException(), report)

        assertEquals(emptyList<String>(), report.kinds)
    }

    @Test
    fun `MediaStore failing is reported`() {
        toMediaStoreError(SQLiteException(), report)
        toMediaStoreError(IOException(), report)

        assertEquals(List(2) { "LibraryError.Storage MediaStore" }, report.kinds)
    }

    @Test
    fun `the permission going away isn't`() {
        toMediaStoreError(SecurityException(), report)

        assertEquals(emptyList<String>(), report.kinds)
    }

    @Test
    fun `tags that make no sense are reported, without the file`() {
        malformed(URI, report)

        assertEquals(listOf("LibraryError.TagsMalformed"), report.kinds)
    }

    @Test
    fun `a file that can't be read is reported`() {
        toFileUnreadable(IOException(), URI, report)

        assertEquals(listOf("LibraryError.FileUnreadable"), report.kinds)
    }

    @Test
    fun `a file that went away, or a permission that did, isn't`() {
        toFileUnreadable(FileNotFoundException(), URI, report)
        toFileUnreadable(SecurityException(), URI, report)

        assertEquals(emptyList<String>(), report.kinds)
    }

    private companion object {
        const val URI = "content://media/external/audio/media/1"
    }
}
