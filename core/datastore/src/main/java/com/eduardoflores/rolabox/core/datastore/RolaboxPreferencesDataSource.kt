package com.eduardoflores.rolabox.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RolaboxPreferencesDataSource @Inject constructor(
    private val preferences: DataStore<Preferences>,
) {
    val userData: Flow<UserData> = preferences.data.map { prefs ->
        UserData(
            darkThemeConfig = DarkThemeConfig.entries
                .firstOrNull { it.name == prefs[DARK_THEME_CONFIG] }
                ?: DarkThemeConfig.FOLLOW_SYSTEM,
        )
    }

    suspend fun setDarkThemeConfig(config: DarkThemeConfig) {
        preferences.edit { it[DARK_THEME_CONFIG] = config.name }
    }

    private companion object {
        val DARK_THEME_CONFIG = stringPreferencesKey("dark_theme_config")
    }
}
