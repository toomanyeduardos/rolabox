package com.eduardoflores.rolabox.common.userdata.api

import com.eduardoflores.rolabox.common.sync.api.SyncedValue

/**
 * The preferences that sync across devices, each with when it was last changed (ADR-011). A field
 * is null when the user has never set it, so a device's defaults never overwrite a choice made on
 * another device.
 */
data class SyncedPreferences(
    val darkThemeConfig: SyncedValue<DarkThemeConfig>? = null,
    val accentColor: SyncedValue<AccentColor>? = null,
)
