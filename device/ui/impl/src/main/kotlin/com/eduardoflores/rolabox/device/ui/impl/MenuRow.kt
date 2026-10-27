package com.eduardoflores.rolabox.device.ui.impl

import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility

/**
 * The main menu's rows, in the order they are shown. This fixed list is what the product has
 * (ADR-020, rule 15): the menu takes no contributions. Now Playing is shown only while a song is loaded
 * (ADR-018), Settings always, and each of the others unless the user hid it in Settings, a preference.
 */
internal enum class MenuRow(val preference: MainMenuRow?) {
    NowPlaying(null),
    Music(MainMenuRow.MUSIC),
    Podcasts(MainMenuRow.PODCASTS),
    Audiobooks(MainMenuRow.AUDIOBOOKS),
    ShuffleSongs(MainMenuRow.SHUFFLE_SONGS),
    Settings(null),
}

/**
 * The rows the main menu shows, in order: Now Playing while [nowPlayingShown], Settings always, and every
 * other row that [visibility] doesn't hide.
 */
internal fun shownMenuRows(
    nowPlayingShown: Boolean,
    visibility: Map<MainMenuRow, MainMenuRowVisibility>,
): List<MenuRow> = MenuRow.entries.filter { row ->
    when (row) {
        MenuRow.NowPlaying -> nowPlayingShown
        MenuRow.Settings -> true
        else -> visibility[row.preference] != MainMenuRowVisibility.HIDDEN
    }
}
