package com.eduardoflores.rolabox.common.userdata.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import kotlinx.coroutines.flow.Flow

// One function for each preference: observing it and setting it.
@Suppress("TooManyFunctions")
interface UserDataRepository {
    fun observeUserData(): Flow<Either<StorageError, UserData>>

    suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit>

    suspend fun setAccentColor(color: AccentColor): Either<StorageError, Unit>

    suspend fun setClicker(clicker: Clicker): Either<StorageError, Unit>

    suspend fun setShuffle(shuffle: Shuffle): Either<StorageError, Unit>

    suspend fun setRepeat(repeat: Repeat): Either<StorageError, Unit>

    suspend fun setMainMenuRowVisibility(
        row: MainMenuRow,
        visibility: MainMenuRowVisibility,
    ): Either<StorageError, Unit>

    /**
     * Emits whether the user chose to use the app without an account, and again when it changes.
     * The choice belongs to this device, so it isn't part of [UserData] and never syncs.
     */
    fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>>

    suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit>

    /**
     * Emits whether episodes may use a metered network, and again when it changes: [EpisodesOnMobileData.ON] when never
     * set. It describes this phone's data plan, so it isn't part of [UserData] and never syncs (ADR-031, ADR-011 rule
     * 10).
     */
    fun observeEpisodesOnMobileData(): Flow<Either<StorageError, EpisodesOnMobileData>>

    suspend fun setEpisodesOnMobileData(value: EpisodesOnMobileData): Either<StorageError, Unit>
}
