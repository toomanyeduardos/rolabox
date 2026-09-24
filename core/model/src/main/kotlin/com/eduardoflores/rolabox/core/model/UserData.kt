package com.eduardoflores.rolabox.core.model

data class UserData(
    val darkThemeConfig: DarkThemeConfig,
)

enum class DarkThemeConfig {
    FOLLOW_SYSTEM,
    LIGHT,
    DARK,
}
