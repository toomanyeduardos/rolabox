package com.eduardoflores.rolabox.device.ui.impl

import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import org.junit.Assert.assertEquals
import org.junit.Test

class MainMenuRowsTest {
    @Test
    fun everyRowShown_andNothingLoaded_isTheWholeMenuButNowPlaying() {
        assertEquals(
            MenuRow.entries - MenuRow.NowPlaying,
            shownMenuRows(nowPlayingShown = false, visibility = AllShown),
        )
    }

    @Test
    fun aHiddenRow_isLeftOut_andTheOthersKeepTheirOrder() {
        val visibility = AllShown + (MainMenuRow.MUSIC to MainMenuRowVisibility.HIDDEN)

        assertEquals(
            listOf(MenuRow.NowPlaying, MenuRow.Podcasts, MenuRow.Audiobooks, MenuRow.ShuffleSongs, MenuRow.Settings),
            shownMenuRows(nowPlayingShown = true, visibility = visibility),
        )
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
