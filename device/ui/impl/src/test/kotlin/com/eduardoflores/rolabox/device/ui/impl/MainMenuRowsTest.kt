package com.eduardoflores.rolabox.device.ui.impl

import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainMenuRowsTest {
    @Test
    fun everyRowShown_andNothingLoaded_isEveryBuiltRowButNowPlaying() {
        assertEquals(
            listOf(MenuRow.Music, MenuRow.Podcasts, MenuRow.ShuffleSongs, MenuRow.Settings),
            shownMenuRows(nowPlayingShown = false, visibility = AllShown),
        )
    }

    @Test
    fun aHiddenRow_isLeftOut_andTheOthersKeepTheirOrder() {
        val visibility = AllShown + (MainMenuRow.MUSIC to MainMenuRowVisibility.HIDDEN)

        assertEquals(
            listOf(MenuRow.NowPlaying, MenuRow.Podcasts, MenuRow.ShuffleSongs, MenuRow.Settings),
            shownMenuRows(nowPlayingShown = true, visibility = visibility),
        )
    }

    // Audiobooks has no screens in v1 (launch/03): its stored preference, shown or hidden, is kept but not shown.
    @Test
    fun audiobooks_isNeverShown_whateverItsPreference() {
        MainMenuRowVisibility.entries.forEach { visibility ->
            val rows = shownMenuRows(
                nowPlayingShown = true,
                visibility = AllShown + (MainMenuRow.AUDIOBOOKS to visibility),
            )

            assertFalse(MenuRow.Audiobooks in rows)
        }
    }

    // Settings and Now Playing can't be hidden: hiding every row that can be leaves them.
    @Test
    fun everyRowHidden_leavesSettings_andNowPlayingWhileLoaded() {
        val allHidden = MainMenuRow.entries.associateWith { MainMenuRowVisibility.HIDDEN }

        assertEquals(listOf(MenuRow.Settings), shownMenuRows(nowPlayingShown = false, visibility = allHidden))
        assertEquals(
            listOf(MenuRow.NowPlaying, MenuRow.Settings),
            shownMenuRows(nowPlayingShown = true, visibility = allHidden),
        )
    }
}

private val AllShown = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN }
