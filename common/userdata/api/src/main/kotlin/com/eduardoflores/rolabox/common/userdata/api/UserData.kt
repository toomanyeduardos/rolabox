package com.eduardoflores.rolabox.common.userdata.api

data class UserData(val darkThemeConfig: DarkThemeConfig, val accentColor: AccentColor)

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
