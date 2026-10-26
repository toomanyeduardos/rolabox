package com.eduardoflores.rolabox.common.userdata.local.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.storage.testing.FakePreferencesStore
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.sync.testing.FakeLastWriteWins
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility.HIDDEN
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility.SHOWN
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultPreferencesLocalDataSourceTest {
    private val preferencesStore = FakePreferencesStore()
    private val dataSource = DefaultPreferencesLocalDataSource(preferencesStore, FakeLastWriteWins())

    @Test
    fun getSyncedPreferences_nothingStored_hasNoFields() = runTest {
        assertEquals(SyncedPreferences().right(), dataSource.getSyncedPreferences())
    }

    @Test
    fun setDarkThemeConfig_isStoredWithItsTimestamp() = runTest {
        assertEquals(Unit.right(), dataSource.setDarkThemeConfig(synced(DarkThemeConfig.LIGHT, NOW)))

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, NOW)).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun setAccentColor_isStoredWithItsTimestamp() = runTest {
        assertEquals(Unit.right(), dataSource.setAccentColor(synced(AccentColor.PURPLE, NOW)))

        assertEquals(
            SyncedPreferences(accentColor = synced(AccentColor.PURPLE, NOW)).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun setDarkThemeConfig_isStoredUnderItsTwoKeys() = runTest {
        dataSource.setDarkThemeConfig(synced(DarkThemeConfig.DARK, NOW))

        assertEquals("DARK".right(), preferencesStore.observeString("dark_theme_config").first())
        assertEquals("$NOW".right(), preferencesStore.observeString("dark_theme_config_updated_at").first())
    }

    @Test
    fun setClicker_isStoredUnderItsTwoKeys() = runTest {
        assertEquals(Unit.right(), dataSource.setClicker(synced(Clicker.OFF, NOW)))

        assertEquals("OFF".right(), preferencesStore.observeString("clicker").first())
        assertEquals("$NOW".right(), preferencesStore.observeString("clicker_updated_at").first())
        assertEquals(
            SyncedPreferences(clicker = synced(Clicker.OFF, NOW)).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun setMainMenuRowVisibility_eachRowIsStoredUnderItsOwnKeys() = runTest {
        val keys = mapOf(
            MainMenuRow.MUSIC to "main_menu_music",
            MainMenuRow.PODCASTS to "main_menu_podcasts",
            MainMenuRow.AUDIOBOOKS to "main_menu_audiobooks",
            MainMenuRow.SHUFFLE_SONGS to "main_menu_shuffle_songs",
        )

        keys.forEach { (row, key) ->
            assertEquals(Unit.right(), dataSource.setMainMenuRowVisibility(row, synced(HIDDEN, NOW)))
            assertEquals("HIDDEN".right(), preferencesStore.observeString(key).first())
            assertEquals("$NOW".right(), preferencesStore.observeString("${key}_updated_at").first())
        }
        assertEquals(
            SyncedPreferences(mainMenuRows = MainMenuRow.entries.associateWith { synced(HIDDEN, NOW) }).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun setMainMenuRowVisibility_otherRowsAreNeverSet() = runTest {
        dataSource.setMainMenuRowVisibility(MainMenuRow.PODCASTS, synced(HIDDEN, NOW))

        assertEquals(
            SyncedPreferences(mainMenuRows = mapOf(MainMenuRow.PODCASTS to synced(HIDDEN, NOW))).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun getSyncedPreferences_unknownStoredValue_readsAsTheDefault() = runTest {
        preferencesStore.setString("dark_theme_config", "SEPIA")

        val readAsDefault = SyncedValue(DarkThemeConfig.FOLLOW_SYSTEM, SyncTimestamp.Epoch)

        assertEquals(SyncedPreferences(darkThemeConfig = readAsDefault).right(), dataSource.getSyncedPreferences())
    }

    @Test
    fun getSyncedPreferences_valueStoredBeforeTimestamps_isOldestPossible() = runTest {
        preferencesStore.setString("dark_theme_config", "DARK")

        assertEquals(
            SyncedPreferences(darkThemeConfig = SyncedValue(DarkThemeConfig.DARK, SyncTimestamp.Epoch)).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun observeSyncedPreferences_readError_emitsErrorAndEnds() = runTest {
        preferencesStore.setReadError(StorageError.Corrupted)

        assertEquals(listOf(StorageError.Corrupted.left()), dataSource.observeSyncedPreferences().toList())
    }

    @Test
    fun setDarkThemeConfig_writeError_isReturned() = runTest {
        preferencesStore.writeError = StorageError.Unavailable

        assertEquals(
            StorageError.Unavailable.left(),
            dataSource.setDarkThemeConfig(synced(DarkThemeConfig.DARK, NOW)),
        )
    }

    @Test
    fun applySyncedPreferences_newerRemote_isStoredWithItsTimestamp() = runTest {
        dataSource.setDarkThemeConfig(synced(DarkThemeConfig.LIGHT, NOW))
        val remote = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, NOW + 1))

        assertEquals(Unit.right(), dataSource.applySyncedPreferences(remote))
        assertEquals(remote.right(), dataSource.getSyncedPreferences())
    }

    @Test
    fun applySyncedPreferences_olderRemote_keepsTheLocalChange() = runTest {
        dataSource.setDarkThemeConfig(synced(DarkThemeConfig.LIGHT, NOW))

        dataSource.applySyncedPreferences(SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, NOW - 1)))

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, NOW)).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun applySyncedPreferences_resolvesEachFieldOnItsOwn() = runTest {
        dataSource.setDarkThemeConfig(synced(DarkThemeConfig.LIGHT, NOW))
        dataSource.setAccentColor(synced(AccentColor.GREEN, NOW))

        dataSource.applySyncedPreferences(
            SyncedPreferences(
                darkThemeConfig = synced(DarkThemeConfig.DARK, NOW - 1),
                accentColor = synced(AccentColor.PINK, NOW + 1),
            ),
        )

        assertEquals(
            SyncedPreferences(
                darkThemeConfig = synced(DarkThemeConfig.LIGHT, NOW),
                accentColor = synced(AccentColor.PINK, NOW + 1),
            ).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun applySyncedPreferences_resolvesEachMainMenuRowOnItsOwn() = runTest {
        dataSource.setMainMenuRowVisibility(MainMenuRow.PODCASTS, synced(HIDDEN, NOW))
        dataSource.setMainMenuRowVisibility(MainMenuRow.MUSIC, synced(HIDDEN, NOW))

        dataSource.applySyncedPreferences(
            SyncedPreferences(
                clicker = synced(Clicker.OFF, NOW),
                mainMenuRows = mapOf(
                    MainMenuRow.PODCASTS to synced(SHOWN, NOW - 1),
                    MainMenuRow.MUSIC to synced(SHOWN, NOW + 1),
                    MainMenuRow.AUDIOBOOKS to synced(HIDDEN, NOW),
                ),
            ),
        )

        assertEquals(
            SyncedPreferences(
                clicker = synced(Clicker.OFF, NOW),
                mainMenuRows = mapOf(
                    MainMenuRow.PODCASTS to synced(HIDDEN, NOW),
                    MainMenuRow.MUSIC to synced(SHOWN, NOW + 1),
                    MainMenuRow.AUDIOBOOKS to synced(HIDDEN, NOW),
                ),
            ).right(),
            dataSource.getSyncedPreferences(),
        )
    }

    @Test
    fun applySyncedPreferences_writeError_isReturned() = runTest {
        preferencesStore.writeError = StorageError.Unavailable

        assertEquals(
            StorageError.Unavailable.left(),
            dataSource.applySyncedPreferences(SyncedPreferences(accentColor = synced(AccentColor.PINK, NOW))),
        )
    }

    @Test
    fun observeOfflineModeChosen_nothingStored_isFalse() = runTest {
        assertEquals(false.right(), dataSource.observeOfflineModeChosen().first())
    }

    @Test
    fun setOfflineModeChosen_isObserved() = runTest {
        assertEquals(Unit.right(), dataSource.setOfflineModeChosen(true))
        assertEquals(true.right(), dataSource.observeOfflineModeChosen().first())

        assertEquals(Unit.right(), dataSource.setOfflineModeChosen(false))
        assertEquals(false.right(), dataSource.observeOfflineModeChosen().first())
    }

    @Test
    fun setOfflineModeChosen_isNotPartOfTheSyncedPreferences() = runTest {
        val before = dataSource.getSyncedPreferences()

        dataSource.setOfflineModeChosen(true)

        assertEquals(before, dataSource.getSyncedPreferences())
    }

    @Test
    fun observeOfflineModeChosen_readError_emitsErrorAndEnds() = runTest {
        preferencesStore.setReadError(StorageError.Corrupted)

        assertEquals(listOf(StorageError.Corrupted.left()), dataSource.observeOfflineModeChosen().toList())
    }

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))

    private companion object {
        const val NOW = 1_000_000L
    }
}
