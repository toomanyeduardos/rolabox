package com.eduardoflores.rolabox.core.data.repository

import com.eduardoflores.rolabox.core.datastore.RolaboxPreferencesDataSource
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultUserDataRepository @Inject constructor(
    private val preferencesDataSource: RolaboxPreferencesDataSource,
) : UserDataRepository {
    override val userData: Flow<UserData> = preferencesDataSource.userData

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig) {
        preferencesDataSource.setDarkThemeConfig(config)
    }
}
