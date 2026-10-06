package com.eduardoflores.rolabox.common.storage.impl

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStorePreferencesStoreTest {
    @Test
    fun observeString_missingKey_emitsNull() = runTest {
        assertEquals(null.right(), DataStorePreferencesStore(InMemoryDataStore()).observeString(KEY).first())
    }

    @Test
    fun setString_isObserved() = runTest {
        val store = DataStorePreferencesStore(InMemoryDataStore())

        assertEquals(Unit.right(), store.setString(KEY, "value"))
        assertEquals("value".right(), store.observeString(KEY).first())
    }

    @Test
    fun observeStrings_emitsEveryRequestedKey() = runTest {
        val store = DataStorePreferencesStore(InMemoryDataStore())
        store.setString(KEY, "value")

        assertEquals(
            mapOf(KEY to "value", OTHER_KEY to null).right(),
            store.observeStrings(setOf(KEY, OTHER_KEY)).first(),
        )
    }

    @Test
    fun observeStrings_corruptedFile_emitsCorruptedAndEnds() = runTest {
        val store = DataStorePreferencesStore(FailingDataStore(CorruptionException("bad file")))

        assertEquals(listOf(StorageError.Corrupted.left()), store.observeStrings(setOf(KEY)).toList())
    }

    @Test
    fun updateStrings_passesCurrentValuesAndWritesTheResult() = runTest {
        val store = DataStorePreferencesStore(InMemoryDataStore())
        store.setString(KEY, "old")
        var seen: Map<String, String?> = emptyMap()

        val result = store.updateStrings(setOf(KEY, OTHER_KEY)) { current ->
            seen = current
            mapOf(KEY to "new", OTHER_KEY to "other")
        }

        assertEquals(Unit.right(), result)
        assertEquals(mapOf(KEY to "old", OTHER_KEY to null), seen)
        assertEquals("new".right(), store.observeString(KEY).first())
        assertEquals("other".right(), store.observeString(OTHER_KEY).first())
    }

    @Test
    fun updateStrings_ioFailure_isUnavailable() = runTest {
        val store = DataStorePreferencesStore(FailingDataStore(IOException("disk")))

        assertEquals(StorageError.Unavailable.left(), store.updateStrings(setOf(KEY)) { mapOf(KEY to "value") })
    }

    @Test
    fun observeString_corruptedFile_emitsCorruptedAndEnds() = runTest {
        val store = DataStorePreferencesStore(FailingDataStore(CorruptionException("bad file")))

        assertEquals(listOf(StorageError.Corrupted.left()), store.observeString(KEY).toList())
    }

    @Test
    fun setString_ioFailure_isUnavailable() = runTest {
        val store = DataStorePreferencesStore(FailingDataStore(IOException("disk")))

        assertEquals(StorageError.Unavailable.left(), store.setString(KEY, "value"))
    }

    @Test(expected = IllegalStateException::class)
    fun setString_unnamedException_isRethrown() = runTest {
        DataStorePreferencesStore(FailingDataStore(IllegalStateException("bug"))).setString(KEY, "value")
    }

    private companion object {
        const val KEY = "key"
        const val OTHER_KEY = "other_key"
    }
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
