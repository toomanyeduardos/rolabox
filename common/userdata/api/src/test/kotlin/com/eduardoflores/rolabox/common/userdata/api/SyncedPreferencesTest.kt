package com.eduardoflores.rolabox.common.userdata.api

import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncedPreferencesTest {
    @Test
    fun merge_resolvesEachFieldOnItsOwn() {
        val local = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.DARK, 2),
            accentColor = synced(AccentColor.GREEN, 1),
        )
        val remote = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.LIGHT, 1),
            accentColor = synced(AccentColor.PINK, 2),
        )

        assertEquals(
            SyncedPreferences(
                darkThemeConfig = synced(DarkThemeConfig.DARK, 2),
                accentColor = synced(AccentColor.PINK, 2),
            ),
            local.merge(remote),
        )
    }

    @Test
    fun merge_fieldNeverSetLocally_takesRemote() {
        val remote = SyncedPreferences(accentColor = synced(AccentColor.PURPLE, 1))

        assertEquals(remote, SyncedPreferences().merge(remote))
    }

    @Test
    fun merge_fieldNeverSetRemotely_keepsLocal() {
        val local = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 1))

        assertEquals(local, local.merge(SyncedPreferences()))
    }

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))
}
