package com.eduardoflores.rolabox.core.data.repository

import arrow.core.Either
import com.eduardoflores.rolabox.core.data.util.catchNamed
import com.eduardoflores.rolabox.core.datastore.RolaboxPreferencesDataSource
import com.eduardoflores.rolabox.core.datastore.asDataStoreError
import com.eduardoflores.rolabox.core.domain.error.DatabaseError
import com.eduardoflores.rolabox.core.domain.repository.UserDataRepository
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

// DataStore runs its I/O on the injected IO dispatcher (see DataStoreModule), so this is main-safe
// without switching dispatchers here.
internal class DefaultUserDataRepository @Inject constructor(
    private val preferencesDataSource: RolaboxPreferencesDataSource,
) : UserDataRepository {
    override fun observeUserData(): Flow<Either<DatabaseError, UserData>> =
        preferencesDataSource.userData.catchNamed(Throwable::asDataStoreError)

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<DatabaseError, Unit> =
        catchNamed(Throwable::asDataStoreError) { preferencesDataSource.setDarkThemeConfig(config) }
}
