package com.eduardoflores.rolabox.core.data.repository

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.datastore.RolaboxPreferencesDataSource
import com.eduardoflores.rolabox.core.domain.error.DatabaseError
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultUserDataRepositoryTest {
    @Test
    fun observeUserData_emitsStoredData() = runTest {
        val repository = repository(InMemoryDataStore())

        assertEquals(
            UserData(darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM).right(),
            repository.observeUserData().first(),
        )
    }

    @Test
    fun setDarkThemeConfig_isObserved() = runTest {
        val repository = repository(InMemoryDataStore())

        assertEquals(Unit.right(), repository.setDarkThemeConfig(DarkThemeConfig.DARK))
        assertEquals(
            UserData(darkThemeConfig = DarkThemeConfig.DARK).right(),
            repository.observeUserData().first(),
        )
    }

    @Test
    fun observeUserData_corruptedFile_emitsCorruptedAndEnds() = runTest {
        val repository = repository(FailingDataStore(CorruptionException("bad file")))

        assertEquals(listOf(DatabaseError.Corrupted.left()), repository.observeUserData().toList())
    }

    @Test
    fun setDarkThemeConfig_ioFailure_isUnavailable() = runTest {
        val repository = repository(FailingDataStore(IOException("disk")))

        assertEquals(DatabaseError.Unavailable.left(), repository.setDarkThemeConfig(DarkThemeConfig.DARK))
    }

    @Test(expected = IllegalStateException::class)
    fun setDarkThemeConfig_unnamedException_isRethrown() = runTest {
        repository(FailingDataStore(IllegalStateException("bug"))).setDarkThemeConfig(DarkThemeConfig.DARK)
    }

    private fun repository(dataStore: DataStore<Preferences>) =
        DefaultUserDataRepository(RolaboxPreferencesDataSource(dataStore))
}

private class InMemoryDataStore : DataStore<Preferences> {
    private val preferences = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = preferences

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(preferences.value).also { preferences.value = it }
}

private class FailingDataStore(private val error: Throwable) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw error }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
}
