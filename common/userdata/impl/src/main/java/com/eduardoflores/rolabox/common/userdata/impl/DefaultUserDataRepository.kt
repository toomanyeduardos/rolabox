package com.eduardoflores.rolabox.common.userdata.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.api.UserData
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import com.eduardoflores.rolabox.common.userdata.local.api.PreferencesLocalDataSource
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Reads and writes the local side only (ADR-002); the [PreferencesSyncer] brings the remote side. */
internal class DefaultUserDataRepository @Inject constructor(
    private val local: PreferencesLocalDataSource,
    private val clock: Clock,
) : UserDataRepository {
    override fun observeUserData(): Flow<Either<StorageError, UserData>> = local.observeSyncedPreferences()
        .map { result -> result.map { it.toUserData() } }
        // A sync that only changes timestamps changes nothing a screen shows.
        .distinctUntilChanged()

    // Each change is stamped with this device's clock, so sync can tell which is newer (ADR-011).
    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit> =
        local.setDarkThemeConfig(SyncedValue(config, now()))

    override suspend fun setAccentColor(color: AccentColor): Either<StorageError, Unit> =
        local.setAccentColor(SyncedValue(color, now()))

    override suspend fun setClicker(clicker: Clicker): Either<StorageError, Unit> =
        local.setClicker(SyncedValue(clicker, now()))

    override suspend fun setMainMenuRowVisibility(
        row: MainMenuRow,
        visibility: MainMenuRowVisibility,
    ): Either<StorageError, Unit> = local.setMainMenuRowVisibility(row, SyncedValue(visibility, now()))

    override fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>> = local.observeOfflineModeChosen()

    override suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit> =
        local.setOfflineModeChosen(chosen)

    private fun now() = SyncTimestamp(clock.millis())
}

// What a field that was never set reads as (ADR-011).
private fun SyncedPreferences.toUserData() = UserData(
    darkThemeConfig = darkThemeConfig?.value ?: DarkThemeConfig.FOLLOW_SYSTEM,
    accentColor = accentColor?.value ?: AccentColor.BLUE,
    clicker = clicker?.value ?: Clicker.ON,
    mainMenuRows = MainMenuRow.entries.associateWith { mainMenuRows[it]?.value ?: MainMenuRowVisibility.SHOWN },
)
