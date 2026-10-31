package com.eduardoflores.rolabox.device.library.local.impl

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.audioPermission
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.queryMusic
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A real query against a provider that runs the selection in SQLite, with MediaStore's columns. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.S])
class MediaStoreQueryTest {
    private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

    private fun titles(sdk: Int): List<String?> {
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, MediaStore.AUTHORITY)
        return queryMusic(resolver, sdk).map { it.title }
    }

    @Test
    fun onlyMusic_isListed() {
        assertEquals(listOf("Music"), titles(Build.VERSION_CODES.S))
    }

    @Test
    fun theUri_namesTheVolumeOfTheFile() {
        Robolectric.setupContentProvider(FakeMediaProvider::class.java, MediaStore.AUTHORITY)

        val file = queryMusic(resolver, Build.VERSION_CODES.S).single()

        assertEquals("content://media/sdcard-1/audio/media/1", file.file.uri)
    }

    @Test
    fun thePermission_isTheOneOfTheVersion() {
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, audioPermission(Build.VERSION_CODES.S_V2))
        assertEquals(Manifest.permission.READ_MEDIA_AUDIO, audioPermission(Build.VERSION_CODES.TIRAMISU))
    }
}

/** Marks one row as each kind of audio that isn't music. */
internal class FakeMediaProvider : ContentProvider() {
    private val database by lazy {
        SQLiteDatabase.create(null).apply {
            execSQL(
                "CREATE TABLE audio (_id INTEGER PRIMARY KEY, volume_name TEXT, relative_path TEXT, " +
                    "_display_name TEXT, _size INTEGER, date_modified INTEGER, title TEXT, artist TEXT, album TEXT, " +
                    "track INTEGER, year INTEGER, duration INTEGER, album_artist TEXT, disc_number TEXT, " +
                    "compilation TEXT, is_music INTEGER DEFAULT 1, is_ringtone INTEGER DEFAULT 0, " +
                    "is_notification INTEGER DEFAULT 0, is_alarm INTEGER DEFAULT 0, is_recording INTEGER DEFAULT 0)",
            )
            insertRow("Music", volume = "sdcard-1")
            insertRow("Not music", "is_music" to 0)
            insertRow("Ringtone", "is_ringtone" to 1)
            insertRow("Notification", "is_notification" to 1)
            insertRow("Alarm", "is_alarm" to 1)
            insertRow("Recording", "is_recording" to 1)
        }
    }

    private fun SQLiteDatabase.insertRow(
        title: String,
        vararg flags: Pair<String, Int>,
        volume: String = "external_primary",
    ) {
        val values = ContentValues().apply {
            put("title", title)
            put("volume_name", volume)
            flags.forEach { (column, value) -> put(column, value) }
        }
        insert("audio", null, values)
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = database.query("audio", projection, selection, selectionArgs, null, null, sortOrder)

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
