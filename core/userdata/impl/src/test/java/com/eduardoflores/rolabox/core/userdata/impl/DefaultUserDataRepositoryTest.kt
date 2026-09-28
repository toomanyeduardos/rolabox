package com.eduardoflores.rolabox.core.userdata.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.storage.testing.FakePreferencesStore
import com.eduardoflores.rolabox.core.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.core.sync.api.SyncedValue
import com.eduardoflores.rolabox.core.userdata.api.AccentColor
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.core.userdata.api.UserData
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUserDataRepositoryTest {
    private val preferencesStore = FakePreferencesStore()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val repository = DefaultUserDataRepository(preferencesStore, clock)

    @Test
    fun observeUserData_nothingStored_usesDefaults() = runTest {
        assertEquals(DEFAULTS.right(), repository.observeUserData().first())
    }

    @Test
    fun setDarkThemeConfig_isObserved() = runTest {
        assertEquals(Unit.right(), repository.setDarkThemeConfig(DarkThemeConfig.DARK))
        assertEquals(
            DEFAULTS.copy(darkThemeConfig = DarkThemeConfig.DARK).right(),
            repository.observeUserData().first(),
        )
    }

    @Test
    fun setAccentColor_isObserved() = runTest {
        assertEquals(Unit.right(), repository.setAccentColor(AccentColor.PURPLE))
        assertEquals(DEFAULTS.copy(accentColor = AccentColor.PURPLE).right(), repository.observeUserData().first())
    }

    @Test
    fun observeUserData_unknownStoredValue_usesDefault() = runTest {
        preferencesStore.setString("dark_theme_config", "SEPIA")

        assertEquals(DEFAULTS.right(), repository.observeUserData().first())
    }

    @Test
    fun observeUserData_readError_emitsErrorAndEnds() = runTest {
        preferencesStore.setReadError(StorageError.Corrupted)

        assertEquals(listOf(StorageError.Corrupted.left()), repository.observeUserData().toList())
    }

    @Test
    fun setDarkThemeConfig_writeError_isReturned() = runTest {
        preferencesStore.writeError = StorageError.Unavailable

        assertEquals(StorageError.Unavailable.left(), repository.setDarkThemeConfig(DarkThemeConfig.DARK))
    }

    @Test
    fun getSyncedPreferences_nothingStored_hasNoFields() = runTest {
        assertEquals(SyncedPreferences().right(), repository.getSyncedPreferences())
    }

    @Test
    fun setDarkThemeConfig_isStampedWithTheClock() = runTest {
        repository.setDarkThemeConfig(DarkThemeConfig.LIGHT)

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, NOW)).right(),
            repository.getSyncedPreferences(),
        )
    }

    @Test
    fun getSyncedPreferences_valueStoredBeforeTimestamps_isOldestPossible() = runTest {
        preferencesStore.setString("dark_theme_config", "DARK")

        assertEquals(
            SyncedPreferences(darkThemeConfig = SyncedValue(DarkThemeConfig.DARK, SyncTimestamp.Epoch)).right(),
            repository.getSyncedPreferences(),
        )
    }

    @Test
    fun applySyncedPreferences_newerRemote_isStoredWithItsTimestamp() = runTest {
        repository.setDarkThemeConfig(DarkThemeConfig.LIGHT)
        val remote = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, NOW + 1))

        assertEquals(Unit.right(), repository.applySyncedPreferences(remote))
        assertEquals(remote.right(), repository.getSyncedPreferences())
    }

    @Test
    fun applySyncedPreferences_olderRemote_keepsTheLocalChange() = runTest {
        repository.setDarkThemeConfig(DarkThemeConfig.LIGHT)

        repository.applySyncedPreferences(SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, NOW - 1)))

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, NOW)).right(),
            repository.getSyncedPreferences(),
        )
    }

    @Test
    fun applySyncedPreferences_resolvesEachFieldOnItsOwn() = runTest {
        repository.setDarkThemeConfig(DarkThemeConfig.LIGHT)
        repository.setAccentColor(AccentColor.GREEN)

        repository.applySyncedPreferences(
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
            repository.getSyncedPreferences(),
        )
    }

    @Test
    fun applySyncedPreferences_isObservedByScreens() = runTest {
        repository.applySyncedPreferences(SyncedPreferences(accentColor = synced(AccentColor.PINK, NOW)))

        assertEquals(DEFAULTS.copy(accentColor = AccentColor.PINK).right(), repository.observeUserData().first())
    }

    @Test
    fun applySyncedPreferences_writeError_isReturned() = runTest {
        preferencesStore.writeError = StorageError.Unavailable

        assertEquals(
            StorageError.Unavailable.left(),
            repository.applySyncedPreferences(SyncedPreferences(accentColor = synced(AccentColor.PINK, NOW))),
        )
    }

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))

    private companion object {
        const val NOW = 1_000_000L
        val DEFAULTS = UserData(darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM, accentColor = AccentColor.BLUE)
    }
}
