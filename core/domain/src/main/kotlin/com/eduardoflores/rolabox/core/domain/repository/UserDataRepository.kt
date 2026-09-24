package com.eduardoflores.rolabox.core.domain.repository

import arrow.core.Either
import com.eduardoflores.rolabox.core.domain.error.DatabaseError
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    fun observeUserData(): Flow<Either<DatabaseError, UserData>>

    suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<DatabaseError, Unit>
}
