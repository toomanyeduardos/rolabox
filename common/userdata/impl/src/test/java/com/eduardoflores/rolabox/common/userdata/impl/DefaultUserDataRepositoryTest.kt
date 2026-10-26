package com.eduardoflores.rolabox.common.userdata.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.api.UserData
import com.eduardoflores.rolabox.common.userdata.testing.FakePreferencesLocalDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUserDataRepositoryTest {
    private val local = FakePreferencesLocalDataSource()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val repository = DefaultUserDataRepository(local, clock)

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
    fun setDarkThemeConfig_isStampedWithTheClock() = runTest {
        repository.setDarkThemeConfig(DarkThemeConfig.LIGHT)

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, NOW)).right(),
            local.getSyncedPreferences(),
        )
    }

    @Test
    fun setAccentColor_isStampedWithTheClock() = runTest {
        repository.setAccentColor(AccentColor.GREEN)

        assertEquals(
            SyncedPreferences(accentColor = synced(AccentColor.GREEN, NOW)).right(),
            local.getSyncedPreferences(),
        )
    }

    @Test
    fun setClicker_isObservedAndStampedWithTheClock() = runTest {
        assertEquals(Unit.right(), repository.setClicker(Clicker.OFF))

        assertEquals(DEFAULTS.copy(clicker = Clicker.OFF).right(), repository.observeUserData().first())
        assertEquals(
            SyncedPreferences(clicker = synced(Clicker.OFF, NOW)).right(),
            local.getSyncedPreferences(),
        )
    }

    @Test
    fun setMainMenuRowVisibility_changesThatRowOnly() = runTest {
        assertEquals(
            Unit.right(),
            repository.setMainMenuRowVisibility(MainMenuRow.PODCASTS, MainMenuRowVisibility.HIDDEN),
        )

        assertEquals(
            DEFAULTS.copy(mainMenuRows = DEFAULTS.mainMenuRows + (MainMenuRow.PODCASTS to MainMenuRowVisibility.HIDDEN))
                .right(),
            repository.observeUserData().first(),
        )
        assertEquals(
            SyncedPreferences(
                mainMenuRows = mapOf(MainMenuRow.PODCASTS to synced(MainMenuRowVisibility.HIDDEN, NOW)),
            ).right(),
            local.getSyncedPreferences(),
        )
    }

    @Test
    fun appliedBySync_isObservedByScreens() = runTest {
        local.applySyncedPreferences(SyncedPreferences(accentColor = synced(AccentColor.PINK, NOW)))

        assertEquals(DEFAULTS.copy(accentColor = AccentColor.PINK).right(), repository.observeUserData().first())
    }

    @Test
    fun observeUserData_aChangeOfTimestampOnly_isNotEmittedAgain() = runTest {
        local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, NOW))
        val emitted = mutableListOf<Any>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.observeUserData().toList(emitted)
        }

        local.applySyncedPreferences(SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, NOW + 1)))

        assertEquals(1, emitted.size)
    }

    @Test
    fun observeUserData_readError_emitsErrorAndEnds() = runTest {
        local.setReadError(StorageError.Corrupted)

        assertEquals(listOf(StorageError.Corrupted.left()), repository.observeUserData().toList())
    }

    @Test
    fun setDarkThemeConfig_writeError_isReturned() = runTest {
        local.writeError = StorageError.Unavailable

        assertEquals(StorageError.Unavailable.left(), repository.setDarkThemeConfig(DarkThemeConfig.DARK))
    }

    @Test
    fun setOfflineModeChosen_isObserved() = runTest {
        assertEquals(false.right(), repository.observeOfflineModeChosen().first())

        assertEquals(Unit.right(), repository.setOfflineModeChosen(true))
        assertEquals(true.right(), repository.observeOfflineModeChosen().first())
    }

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))

    private companion object {
        const val NOW = 1_000_000L
        val DEFAULTS = UserData(
            darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
            accentColor = AccentColor.BLUE,
            clicker = Clicker.ON,
            mainMenuRows = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN },
        )
    }
}
