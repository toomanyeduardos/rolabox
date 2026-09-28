package com.eduardoflores.rolabox.core.storage.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import arrow.core.Either
import com.eduardoflores.rolabox.core.common.util.catchNamed
import com.eduardoflores.rolabox.core.storage.api.PreferencesStore
import com.eduardoflores.rolabox.core.storage.api.StorageError
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

// DataStore runs its I/O on the injected IO dispatcher (see StorageModule), so this is main-safe
// without switching dispatchers here.
internal class DataStorePreferencesStore @Inject constructor(private val dataStore: DataStore<Preferences>) :
    PreferencesStore {
    override fun observeString(key: String): Flow<Either<StorageError, String?>> = dataStore.data
        .map { it[stringPreferencesKey(key)] }
        .distinctUntilChanged()
        .catchNamed(Throwable::asDataStoreError)

    override suspend fun setString(key: String, value: String): Either<StorageError, Unit> =
        catchNamed(Throwable::asDataStoreError) { dataStore.edit { it[stringPreferencesKey(key)] = value } }
}
