package com.eduardoflores.rolabox.core.data.repository

import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    val userData: Flow<UserData>

    suspend fun setDarkThemeConfig(config: DarkThemeConfig)
}
