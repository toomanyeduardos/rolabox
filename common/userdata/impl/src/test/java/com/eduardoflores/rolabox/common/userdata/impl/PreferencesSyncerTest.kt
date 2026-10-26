package com.eduardoflores.rolabox.common.userdata.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.sync.testing.FakeLastWriteWins
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.testing.FakePreferencesLocalDataSource
import com.eduardoflores.rolabox.common.userdata.testing.FakePreferencesNetworkDataSource
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesSyncerTest {
    private val network = FakePreferencesNetworkDataSource()

    @Test
    fun localNewer_isPushed() = runTest {
        val device = device()
        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 2))
        network.byUser[USER] = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, 1))

        assertEquals(Unit.right(), device.syncer.sync(USER))

        val expected = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 2))
        assertEquals(expected, network.byUser[USER])
        assertEquals(expected.right(), device.local.getSyncedPreferences())
    }

    @Test
    fun remoteNewer_isPulled() = runTest {
        val device = device()
        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 1))
        network.byUser[USER] = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, 2))

        device.syncer.sync(USER)

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, 2)).right(),
            device.local.getSyncedPreferences(),
        )
        assertEquals(0, network.writes)
    }

    @Test
    fun sameTimestamp_remoteWins() = runTest {
        val device = device()
        device.local.setAccentColor(synced(AccentColor.GREEN, 1))
        network.byUser[USER] = SyncedPreferences(accentColor = synced(AccentColor.PINK, 1))

        device.syncer.sync(USER)

        assertEquals(
            SyncedPreferences(accentColor = synced(AccentColor.PINK, 1)).right(),
            device.local.getSyncedPreferences(),
        )
        assertEquals(0, network.writes)
    }

    @Test
    fun eachFieldIsResolvedOnItsOwn() = runTest {
        val device = device()
        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 2))
        network.byUser[USER] = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.LIGHT, 1),
            accentColor = synced(AccentColor.PURPLE, 3),
        )

        device.syncer.sync(USER)

        val expected = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.DARK, 2),
            accentColor = synced(AccentColor.PURPLE, 3),
        )
        assertEquals(expected, network.byUser[USER])
        assertEquals(expected.right(), device.local.getSyncedPreferences())
    }

    @Test
    fun newDevice_neverOverwritesRemoteWithDefaults() = runTest {
        val remotePreferences = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 1))
        network.byUser[USER] = remotePreferences

        device().syncer.sync(USER)

        assertEquals(remotePreferences, network.byUser[USER])
        assertEquals(0, network.writes)
    }

    @Test
    fun twoDevicesOffline_convergeOnTheLaterChanges() = runTest {
        val phone = device()
        val tablet = device()
        phone.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 1))
        tablet.local.setDarkThemeConfig(synced(DarkThemeConfig.LIGHT, 2))
        phone.local.setAccentColor(synced(AccentColor.GREEN, 3))

        phone.syncer.sync(USER)
        tablet.syncer.sync(USER)
        phone.syncer.sync(USER)

        val expected = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.LIGHT, 2),
            accentColor = synced(AccentColor.GREEN, 3),
        )
        assertEquals(expected, network.byUser[USER])
        assertEquals(expected.right(), phone.local.getSyncedPreferences())
        assertEquals(expected.right(), tablet.local.getSyncedPreferences())
    }

    @Test
    fun clicker_newerIsPushedAndOlderIsPulledOver() = runTest {
        val phone = device()
        val tablet = device()
        phone.local.setClicker(synced(Clicker.OFF, 1))
        tablet.local.setClicker(synced(Clicker.ON, 2))

        phone.syncer.sync(USER)
        tablet.syncer.sync(USER)
        phone.syncer.sync(USER)

        val expected = SyncedPreferences(clicker = synced(Clicker.ON, 2))
        assertEquals(expected, network.byUser[USER])
        assertEquals(expected.right(), phone.local.getSyncedPreferences())
    }

    // ADR-011 rule 9: each row is a field, so two rows hidden on two devices offline both stay hidden.
    @Test
    fun mainMenuRowsHiddenOnTwoDevices_bothSurvive() = runTest {
        val phone = device()
        val tablet = device()
        phone.local.setMainMenuRowVisibility(MainMenuRow.PODCASTS, synced(MainMenuRowVisibility.HIDDEN, 1))
        tablet.local.setMainMenuRowVisibility(MainMenuRow.AUDIOBOOKS, synced(MainMenuRowVisibility.HIDDEN, 2))

        phone.syncer.sync(USER)
        tablet.syncer.sync(USER)
        phone.syncer.sync(USER)

        val expected = SyncedPreferences(
            mainMenuRows = mapOf(
                MainMenuRow.PODCASTS to synced(MainMenuRowVisibility.HIDDEN, 1),
                MainMenuRow.AUDIOBOOKS to synced(MainMenuRowVisibility.HIDDEN, 2),
            ),
        )
        assertEquals(expected, network.byUser[USER])
        assertEquals(expected.right(), phone.local.getSyncedPreferences())
        assertEquals(expected.right(), tablet.local.getSyncedPreferences())
    }

    @Test
    fun newDevice_neverOverwritesTheNewFieldsWithDefaults() = runTest {
        val remotePreferences = SyncedPreferences(
            clicker = synced(Clicker.OFF, 1),
            mainMenuRows = mapOf(MainMenuRow.MUSIC to synced(MainMenuRowVisibility.HIDDEN, 1)),
        )
        network.byUser[USER] = remotePreferences
        val device = device()

        device.syncer.sync(USER)

        assertEquals(remotePreferences, network.byUser[USER])
        assertEquals(remotePreferences.right(), device.local.getSyncedPreferences())
        assertEquals(0, network.writes)
    }

    @Test
    fun syncingAgain_changesNothing() = runTest {
        val device = device()
        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 1))
        device.syncer.sync(USER)

        device.syncer.sync(USER)

        assertEquals(1, network.writes)
    }

    @Test
    fun usersDontShareData() = runTest {
        val device = device()
        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 1))

        device.syncer.sync(USER)

        assertEquals(null, network.byUser[OTHER_USER])
    }

    @Test
    fun remoteUnavailable_isReturnedAndLocalIsUnchanged() = runTest {
        val device = device()
        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 1))
        network.error = RemoteError.Unavailable

        assertEquals(SyncError.Remote(RemoteError.Unavailable).left(), device.syncer.sync(USER))
        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 1)).right(),
            device.local.getSyncedPreferences(),
        )
    }

    @Test
    fun localReadError_isReturnedAndRemoteIsUnchanged() = runTest {
        val device = device()
        device.local.setReadError(StorageError.Corrupted)

        assertEquals(SyncError.Local(StorageError.Corrupted).left(), device.syncer.sync(USER))
        assertEquals(0, network.writes)
    }

    @Test
    fun localWriteError_isReturned() = runTest {
        val device = device()
        network.byUser[USER] = SyncedPreferences(accentColor = synced(AccentColor.PINK, 1))
        device.local.writeError = StorageError.Unavailable

        assertEquals(SyncError.Local(StorageError.Unavailable).left(), device.syncer.sync(USER))
    }

    @Test
    fun observeLocalChanges_skipsTheCurrentValueAndEmitsEachChange() = runTest {
        val device = device()
        val changes = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            device.syncer.observeLocalChanges().toList(changes)
        }

        device.local.setDarkThemeConfig(synced(DarkThemeConfig.DARK, 1))
        device.local.setAccentColor(synced(AccentColor.PINK, 1))

        assertEquals(2, changes.size)
    }

    // Each device has its own local storage, and they all share the one remote.
    private fun device(): Device {
        val local = FakePreferencesLocalDataSource()
        return Device(local, PreferencesSyncer(local, network, FakeLastWriteWins()))
    }

    private class Device(val local: FakePreferencesLocalDataSource, val syncer: PreferencesSyncer)

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))

    private companion object {
        const val USER = "alice"
        const val OTHER_USER = "bob"
    }
}
