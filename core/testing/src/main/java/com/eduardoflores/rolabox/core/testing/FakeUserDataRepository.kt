package com.eduardoflores.rolabox.core.testing

import com.eduardoflores.rolabox.core.data.repository.UserDataRepository
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class FakeUserDataRepository @Inject constructor() : UserDataRepository {
    private val data = MutableStateFlow(UserData(darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM))

    override val userData: Flow<UserData> = data

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig) {
        data.update { it.copy(darkThemeConfig = config) }
    }
}
