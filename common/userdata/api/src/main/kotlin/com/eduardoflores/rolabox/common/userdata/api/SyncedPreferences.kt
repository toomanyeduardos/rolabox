package com.eduardoflores.rolabox.common.userdata.api

import com.eduardoflores.rolabox.common.sync.api.SyncedValue

/**
 * The preferences that sync across devices, each with when it was last changed (ADR-011). A field
 * is null when the user has never set it, so a device's defaults never overwrite a choice made on
 * another device. Each main menu row is a field of its own, and a row that was never set has no
 * entry in [mainMenuRows].
 */
data class SyncedPreferences(
    val darkThemeConfig: SyncedValue<DarkThemeConfig>? = null,
    val accentColor: SyncedValue<AccentColor>? = null,
    val clicker: SyncedValue<Clicker>? = null,
    val mainMenuRows: Map<MainMenuRow, SyncedValue<MainMenuRowVisibility>> = emptyMap(),
)
