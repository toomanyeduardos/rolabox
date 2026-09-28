package com.eduardoflores.rolabox.core.userdata.impl

import arrow.core.Either
import com.eduardoflores.rolabox.core.storage.api.PreferencesStore
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.UserData
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultUserDataRepository @Inject constructor(private val preferencesStore: PreferencesStore) :
    UserDataRepository {
    override fun observeUserData(): Flow<Either<StorageError, UserData>> =
        preferencesStore.observeString(DARK_THEME_CONFIG).map { result ->
            result.map { stored -> UserData(darkThemeConfig = stored.toDarkThemeConfig()) }
        }

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit> =
        preferencesStore.setString(DARK_THEME_CONFIG, config.name)

    private companion object {
        // Stored on users' devices, so it doesn't change.
        const val DARK_THEME_CONFIG = "dark_theme_config"
    }
}

// Nothing stored, or a value from a version that no longer exists, follows the system.
private fun String?.toDarkThemeConfig(): DarkThemeConfig =
    DarkThemeConfig.entries.firstOrNull { it.name == this } ?: DarkThemeConfig.FOLLOW_SYSTEM
