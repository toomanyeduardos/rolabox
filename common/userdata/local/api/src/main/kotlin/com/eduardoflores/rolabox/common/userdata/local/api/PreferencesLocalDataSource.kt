package com.eduardoflores.rolabox.common.userdata.local.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import kotlinx.coroutines.flow.Flow

/**
 * The user's preferences as stored on this device, the source of truth (ADR-002). Each synced field
 * is kept with when it last changed, so sync can merge it (ADR-011).
 */
interface PreferencesLocalDataSource {
    /** Emits the synced preferences, and again whenever one is set or applied. A Left ends the flow. */
    fun observeSyncedPreferences(): Flow<Either<StorageError, SyncedPreferences>>

    suspend fun getSyncedPreferences(): Either<StorageError, SyncedPreferences>

    /** Stores [config] with its timestamp, in one atomic write. */
    suspend fun setDarkThemeConfig(config: SyncedValue<DarkThemeConfig>): Either<StorageError, Unit>

    /** Stores [color] with its timestamp, in one atomic write. */
    suspend fun setAccentColor(color: SyncedValue<AccentColor>): Either<StorageError, Unit>

    /** Stores [clicker] with its timestamp, in one atomic write. */
    suspend fun setClicker(clicker: SyncedValue<Clicker>): Either<StorageError, Unit>

    /** Stores the [visibility] of [row] with its timestamp, in one atomic write. Each row is a field of its own. */
    suspend fun setMainMenuRowVisibility(
        row: MainMenuRow,
        visibility: SyncedValue<MainMenuRowVisibility>,
    ): Either<StorageError, Unit>

    /**
     * Stores each field of [preferences] that wins over the stored one (last-write-wins, with
     * [preferences] as the remote side), keeping its timestamp. The check and the write are one
     * atomic change, so a local change made while a sync was running is never overwritten by an older
     * value.
     */
    suspend fun applySyncedPreferences(preferences: SyncedPreferences): Either<StorageError, Unit>

    /**
     * Emits whether the user chose to use the app without an account, and again when it changes.
     * The choice belongs to this device, so it has no timestamp and never syncs (ADR-011, rule 10).
     */
    fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>>

    suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit>
}
