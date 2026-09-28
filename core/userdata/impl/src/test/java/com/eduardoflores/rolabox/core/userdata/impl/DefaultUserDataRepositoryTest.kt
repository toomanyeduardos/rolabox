package com.eduardoflores.rolabox.core.userdata.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.storage.testing.FakePreferencesStore
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.UserData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUserDataRepositoryTest {
    private val preferencesStore = FakePreferencesStore()
    private val repository = DefaultUserDataRepository(preferencesStore)

    @Test
    fun observeUserData_nothingStored_followsSystem() = runTest {
        assertEquals(
            UserData(darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM).right(),
            repository.observeUserData().first(),
        )
    }

    @Test
    fun setDarkThemeConfig_isObserved() = runTest {
        assertEquals(Unit.right(), repository.setDarkThemeConfig(DarkThemeConfig.DARK))
        assertEquals(
            UserData(darkThemeConfig = DarkThemeConfig.DARK).right(),
            repository.observeUserData().first(),
        )
    }

    @Test
    fun observeUserData_unknownStoredValue_followsSystem() = runTest {
        preferencesStore.setString("dark_theme_config", "SEPIA")

        assertEquals(
            UserData(darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM).right(),
            repository.observeUserData().first(),
        )
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
}
