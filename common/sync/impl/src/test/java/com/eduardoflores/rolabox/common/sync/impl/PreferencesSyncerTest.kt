package com.eduardoflores.rolabox.common.sync.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.testing.FakeUserDataRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesSyncerTest {
    private val remote = FakeRemotePreferences()

    @Test
    fun localNewer_isPushed() = runTest {
        val device = device(now = 2)
        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        remote.byUser[USER] = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, 1))

        assertEquals(Unit.right(), device.syncer.sync(USER))

        val expected = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 2))
        assertEquals(expected, remote.byUser[USER])
        assertEquals(expected.right(), device.local.getSyncedPreferences())
    }

    @Test
    fun remoteNewer_isPulled() = runTest {
        val device = device(now = 1)
        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        remote.byUser[USER] = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, 2))

        device.syncer.sync(USER)

        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.LIGHT, 2)).right(),
            device.local.getSyncedPreferences(),
        )
        assertEquals(0, remote.writes)
    }

    @Test
    fun sameTimestamp_remoteWins() = runTest {
        val device = device(now = 1)
        device.local.setAccentColor(AccentColor.GREEN)
        remote.byUser[USER] = SyncedPreferences(accentColor = synced(AccentColor.PINK, 1))

        device.syncer.sync(USER)

        assertEquals(
            SyncedPreferences(accentColor = synced(AccentColor.PINK, 1)).right(),
            device.local.getSyncedPreferences(),
        )
        assertEquals(0, remote.writes)
    }

    @Test
    fun eachFieldIsResolvedOnItsOwn() = runTest {
        val device = device(now = 2)
        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        remote.byUser[USER] = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.LIGHT, 1),
            accentColor = synced(AccentColor.PURPLE, 3),
        )

        device.syncer.sync(USER)

        val expected = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.DARK, 2),
            accentColor = synced(AccentColor.PURPLE, 3),
        )
        assertEquals(expected, remote.byUser[USER])
        assertEquals(expected.right(), device.local.getSyncedPreferences())
    }

    @Test
    fun newDevice_neverOverwritesRemoteWithDefaults() = runTest {
        val remotePreferences = SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 1))
        remote.byUser[USER] = remotePreferences

        device(now = 100).syncer.sync(USER)

        assertEquals(remotePreferences, remote.byUser[USER])
        assertEquals(0, remote.writes)
    }

    @Test
    fun twoDevicesOffline_convergeOnTheLaterChanges() = runTest {
        val phone = device(now = 1)
        val tablet = device(now = 2)
        phone.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        tablet.local.setDarkThemeConfig(DarkThemeConfig.LIGHT)
        phone.local.now = SyncTimestamp(3)
        phone.local.setAccentColor(AccentColor.GREEN)

        phone.syncer.sync(USER)
        tablet.syncer.sync(USER)
        phone.syncer.sync(USER)

        val expected = SyncedPreferences(
            darkThemeConfig = synced(DarkThemeConfig.LIGHT, 2),
            accentColor = synced(AccentColor.GREEN, 3),
        )
        assertEquals(expected, remote.byUser[USER])
        assertEquals(expected.right(), phone.local.getSyncedPreferences())
        assertEquals(expected.right(), tablet.local.getSyncedPreferences())
    }

    @Test
    fun syncingAgain_changesNothing() = runTest {
        val device = device(now = 1)
        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        device.syncer.sync(USER)

        device.syncer.sync(USER)

        assertEquals(1, remote.writes)
    }

    @Test
    fun usersDontShareData() = runTest {
        val device = device(now = 1)
        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)

        device.syncer.sync(USER)

        assertEquals(null, remote.byUser[OTHER_USER])
    }

    @Test
    fun remoteUnavailable_isReturnedAndLocalIsUnchanged() = runTest {
        val device = device(now = 1)
        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        remote.error = RemoteError.Unavailable

        assertEquals(SyncError.Remote(RemoteError.Unavailable).left(), device.syncer.sync(USER))
        assertEquals(
            SyncedPreferences(darkThemeConfig = synced(DarkThemeConfig.DARK, 1)).right(),
            device.local.getSyncedPreferences(),
        )
    }

    @Test
    fun localReadError_isReturnedAndRemoteIsUnchanged() = runTest {
        val device = device(now = 1)
        device.local.setReadError(StorageError.Corrupted)

        assertEquals(SyncError.Local(StorageError.Corrupted).left(), device.syncer.sync(USER))
        assertEquals(0, remote.writes)
    }

    @Test
    fun localWriteError_isReturned() = runTest {
        val device = device(now = 1)
        remote.byUser[USER] = SyncedPreferences(accentColor = synced(AccentColor.PINK, 1))
        device.local.writeError = StorageError.Unavailable

        assertEquals(SyncError.Local(StorageError.Unavailable).left(), device.syncer.sync(USER))
    }

    @Test
    fun observeLocalChanges_skipsTheCurrentValueAndEmitsEachChange() = runTest {
        val device = device(now = 1)
        val changes = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            device.syncer.observeLocalChanges().toList(changes)
        }

        device.local.setDarkThemeConfig(DarkThemeConfig.DARK)
        device.local.setAccentColor(AccentColor.PINK)

        assertEquals(2, changes.size)
    }

    private fun device(now: Long): Device {
        val local = FakeUserDataRepository().apply { this.now = SyncTimestamp(now) }
        return Device(local, PreferencesSyncer(local, remote))
    }

    private class Device(val local: FakeUserDataRepository, val syncer: PreferencesSyncer)

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))

    private companion object {
        const val USER = "alice"
        const val OTHER_USER = "bob"
    }
}
