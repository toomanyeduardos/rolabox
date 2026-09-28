package com.eduardoflores.rolabox.core.userdata.api

data class UserData(val darkThemeConfig: DarkThemeConfig)

enum class DarkThemeConfig {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}
