package com.eduardoflores.rolabox.device.library.local.impl

import com.eduardoflores.rolabox.device.library.local.impl.mediastore.MediaStoreRow
import com.eduardoflores.rolabox.device.library.local.impl.mediastore.toAudioFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaStoreRowTest {
    private val full = MediaStoreRow(
        id = 7,
        relativePath = "Music/Artist/Album/",
        displayName = "01 - Song.mp3",
        size = 1234,
        dateModified = 55,
        title = "Song",
        artist = "Artist",
        albumArtist = "Album Artist",
        album = "Album",
        track = 3,
        discNumber = "2/2",
        year = 2001,
        compilation = "1",
        durationMillis = 3000,
    )

    private fun MediaStoreRow.file() = toAudioFile("content://song/7")

    @Test
    fun everyValue_isMediaStores() {
        val file = full.file()

        assertEquals(7L, file.file.mediaStoreId)
        assertEquals("content://song/7", file.file.uri)
        assertEquals("Music/Artist/Album/", file.file.relativePath)
        assertEquals("01 - Song.mp3", file.file.fileName)
        assertEquals(1234L, file.file.size)
        assertEquals(55L, file.file.dateModified)
        assertEquals("Song", file.title)
        assertEquals("Artist", file.artist)
        assertEquals("Album Artist", file.albumArtist)
        assertEquals("Album", file.album)
        assertEquals(3, file.trackNumber)
        assertEquals(2, file.discNumber)
        assertEquals(2001, file.year)
        assertEquals(true, file.compilation)
        assertEquals(3000L, file.durationMillis)
    }

    @Test
    fun theMarkerOfNoValue_isNoValue() {
        val file = full.copy(artist = "<unknown>", album = "<unknown>", albumArtist = "<unknown>").file()

        assertNull(file.artist)
        assertNull(file.album)
        assertNull(file.albumArtist)
    }

    @Test
    fun blankText_isNoValue() {
        assertNull(full.copy(title = "   ").file().title)
    }

    @Test
    fun zero_isNoValue() {
        val file = full.copy(track = 0, year = 0, durationMillis = 0, discNumber = null).file()

        assertNull(file.trackNumber)
        assertNull(file.discNumber)
        assertNull(file.year)
        assertNull(file.durationMillis)
    }

    @Test
    fun aTrackNumberedByDisc_isSplit() {
        val file = full.copy(track = 2003, discNumber = null).file()

        assertEquals(3, file.trackNumber)
        assertEquals(2, file.discNumber)
    }

    @Test
    fun theDiscColumn_winsOverTheTrackNumbering() {
        assertEquals(4, full.copy(track = 2003, discNumber = "4").file().discNumber)
    }

    @Test
    fun aColumnThisApiDoesNotHave_isNoValue() {
        val file = full.copy(albumArtist = null, discNumber = null, compilation = null).file()

        assertNull(file.albumArtist)
        assertNull(file.discNumber)
        assertNull(file.compilation)
    }

    @Test
    fun notACompilation_isFalse() {
        assertEquals(false, full.copy(compilation = "0").file().compilation)
    }

    @Test
    fun textIsNotReinterpreted() {
        assertEquals("U2 & Coldplay", full.copy(artist = "U2 & Coldplay").file().artist)
    }
}
