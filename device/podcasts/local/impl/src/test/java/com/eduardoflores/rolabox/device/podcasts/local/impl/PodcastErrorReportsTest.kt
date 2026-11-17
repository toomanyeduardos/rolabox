package com.eduardoflores.rolabox.device.podcasts.local.impl

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** ADR-030 and ADR-031: which database failures the podcasts report, and how each can be told apart. */
@RunWith(RobolectricTestRunner::class)
class PodcastErrorReportsTest {
    private val report = FakeNonFatalReport()

    @Test
    fun aCorruptDatabase_isReported() {
        val error = toPodcastError(SQLiteDatabaseCorruptException(), report)

        assertEquals(PodcastError.Storage(StorageError.Corrupted), error)
        assertEquals(listOf("StorageError.Corrupted Room"), report.kinds)
    }

    @Test
    fun anyOtherDatabaseFailure_isReported_namingItsClass() {
        toPodcastError(SQLiteConstraintException(), report)
        toPodcastError(SQLiteDiskIOException(), report)
        val error = toPodcastError(SQLiteException(), report)

        assertEquals(PodcastError.Storage(StorageError.Unavailable), error)
        assertEquals(
            listOf(
                "StorageError.Unavailable Room SQLiteConstraintException",
                "StorageError.Unavailable Room SQLiteDiskIOException",
                "StorageError.Unavailable Room SQLiteException",
            ),
            report.kinds,
        )
    }

    @Test
    fun aFullDisk_isntReported() {
        val error = toPodcastError(SQLiteFullException(), report)

        assertEquals(PodcastError.Storage(StorageError.Unavailable), error)
        assertEquals(emptyList<String>(), report.kinds)
    }

    @Test
    fun whatIsntADatabaseFailure_isntConverted() {
        assertNull(toPodcastError(IOException(), report))
        assertEquals(emptyList<String>(), report.kinds)
    }
}
