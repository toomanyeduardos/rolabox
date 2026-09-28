package com.eduardoflores.rolabox.core.userdata.api

import arrow.core.Either
import com.eduardoflores.rolabox.core.storage.api.StorageError
import kotlinx.coroutines.flow.Flow

interface UserDataRepository {
    fun observeUserData(): Flow<Either<StorageError, UserData>>

    suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit>

    suspend fun setAccentColor(color: AccentColor): Either<StorageError, Unit>

    /**
     * Emits whether the user chose to use the app without an account, and again when it changes.
     * The choice belongs to this device, so it isn't part of [UserData] and never syncs.
     */
    fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>>

    suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit>
}
