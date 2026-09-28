package com.eduardoflores.rolabox.core.userdata.api

import arrow.core.Either
import com.eduardoflores.rolabox.core.storage.api.StorageError
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    fun observeUserData(): Flow<Either<StorageError, UserData>>

    suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit>
}
