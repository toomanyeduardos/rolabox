package com.eduardoflores.rolabox.common.userdata.api

/**
 * The user's preferences, with a value for each: a preference that was never set reads as its
 * default (ADR-011). [mainMenuRows] has an entry for every [MainMenuRow].
 */
data class UserData(
    val darkThemeConfig: DarkThemeConfig,
    val accentColor: AccentColor,
    val clicker: Clicker,
    val shuffle: Shuffle,
    val repeat: Repeat,
    val mainMenuRows: Map<MainMenuRow, MainMenuRowVisibility>,
)

enum class DarkThemeConfig {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}

/** The app's primary color. The design system decides the exact tones for light and dark themes. */
enum class AccentColor {
    BLUE,
    GREEN,
    PURPLE,
    PINK,
}

/** Whether turning the device's wheel clicks. The names are stored and synced, so they never change. */
enum class Clicker {
    ON,
    OFF,
}

/**
 * The order the queue plays a list in (ADR-028): the list's own, a random one by song, or a random one by album. The
 * names are stored and synced, so they never change.
 */
enum class Shuffle {
    OFF,
    SONGS,
    ALBUMS,
}

/**
 * What happens at the ends of the queue (ADR-028): it ends, the song that ends starts again, or the queue starts again.
 * The names are stored and synced, so they never change.
 */
enum class Repeat {
    OFF,
    ONE,
    ALL,
}

/**
 * The rows of the device's main menu that the user can hide. Settings and Now Playing are always
 * shown, so they aren't here. Which rows the main menu has is decided by the device
 * (ADR-020, rule 15); this only names the ones whose visibility is a preference.
 */
enum class MainMenuRow {
    MUSIC,
    PODCASTS,
    AUDIOBOOKS,
    SHUFFLE_SONGS,
}

/** Whether a [MainMenuRow] is shown. The names are stored and synced, so they never change. */
enum class MainMenuRowVisibility {
    SHOWN,
    HIDDEN,
}
