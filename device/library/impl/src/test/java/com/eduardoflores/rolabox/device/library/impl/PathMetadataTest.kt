package com.eduardoflores.rolabox.device.library.impl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PathMetadataTest {
    @Test
    fun twoFoldersBelowTheTopOne_areTheArtistAndAlbum() {
        assertEquals(FolderMetadata("Artist", "Album"), folderOf("Music/Artist/Album/"))
    }

    @Test
    fun deeperPaths_useTheLastTwoFolders() {
        assertEquals(FolderMetadata("Artist", "Album"), folderOf("Music/Rips/2003/Artist/Album/"))
    }

    @Test
    fun oneFolderBelowTheTopOne_saysNothing() {
        assertNull(folderOf("Music/Dump/"))
    }

    @Test
    fun theTopFolderAlone_saysNothing() {
        assertNull(folderOf("Download/"))
        assertNull(folderOf(""))
    }

    @Test
    fun aGenericFolder_saysNothing_inAnyCase() {
        assertNull(folderOf("Music/Rips/Unknown/"))
        assertNull(folderOf("Music/DOWNLOADS/Album/"))
    }

    @Test
    fun artistDashTitle() {
        assertEquals(FileNameMetadata("Title", "Artist", null), fileNameOf("Artist - Title.mp3"))
    }

    @Test
    fun aNumberAndATitle() {
        assertEquals(FileNameMetadata("Title", null, 1), fileNameOf("01 Title.mp3"))
        assertEquals(FileNameMetadata("Title", null, 1), fileNameOf("01 - Title.mp3"))
        assertEquals(FileNameMetadata("Title", null, 3), fileNameOf("3. Title.mp3"))
    }

    @Test
    fun aNumberAnArtistAndATitle() {
        assertEquals(FileNameMetadata("Title", "Artist", 12), fileNameOf("12 - Artist - Title.m4a"))
    }

    @Test
    fun trackZero_isNoTrack_andStillLeavesTheTitle() {
        assertEquals(FileNameMetadata("Ash", null, null), fileNameOf("00 - Ash.mp3"))
    }

    @Test
    fun aSingleDigitWithNoSeparator_isPartOfTheTitle() {
        assertEquals(FileNameMetadata("7 Rings", null, null), fileNameOf("7 Rings.mp3"))
    }

    @Test
    fun aYearOrADate_isPartOfTheTitle() {
        assertEquals(FileNameMetadata("1979", null, null), fileNameOf("1979.mp3"))
        assertEquals(FileNameMetadata("2024-03-01 demo", null, null), fileNameOf("2024-03-01 demo.mp3"))
    }

    @Test
    fun aNameWithoutAPattern_isTheTitle() {
        assertEquals(FileNameMetadata("AUD-20190311-WA0004", null, null), fileNameOf("AUD-20190311-WA0004.mp3"))
        assertEquals(FileNameMetadata("final_FINAL_v2", null, null), fileNameOf("final_FINAL_v2.m4a"))
    }

    @Test
    fun onlyTheLastDot_startsTheExtension() {
        assertEquals("Dr. Who", fileNameOf("Dr. Who.mp3").title)
    }

    @Test
    fun noName_isNoTitle() {
        assertNull(fileNameOf("").title)
    }
}
