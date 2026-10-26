package com.eduardoflores.rolabox.common.userdata.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.local.api.PreferencesLocalDataSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

/** The preferences of one device, in memory, applied the way the stored ones are. */
@Suppress("TooManyFunctions") // One per function of the interface, and the controls a test needs.
@Singleton
class FakePreferencesLocalDataSource @Inject constructor() : PreferencesLocalDataSource {
    private val preferences = MutableStateFlow(SyncedPreferences())
    private val readError = MutableStateFlow<StorageError?>(null)
    private val offlineModeChosen = MutableStateFlow(false)

    /** When set, writes fail with this error and leave the data unchanged. */
    var writeError: StorageError? = null

    // Like the real data source, a Left ends the flow.
    override fun observeSyncedPreferences(): Flow<Either<StorageError, SyncedPreferences>> =
        combine(preferences, readError) { stored, error -> error?.left() ?: stored.right() }.untilLeft()

    override suspend fun getSyncedPreferences(): Either<StorageError, SyncedPreferences> =
        observeSyncedPreferences().first()

    override suspend fun setDarkThemeConfig(config: SyncedValue<DarkThemeConfig>): Either<StorageError, Unit> =
        write { it.copy(darkThemeConfig = config) }

    override suspend fun setAccentColor(color: SyncedValue<AccentColor>): Either<StorageError, Unit> =
        write { it.copy(accentColor = color) }

    override suspend fun setClicker(clicker: SyncedValue<Clicker>): Either<StorageError, Unit> =
        write { it.copy(clicker = clicker) }

    override suspend fun setMainMenuRowVisibility(
        row: MainMenuRow,
        visibility: SyncedValue<MainMenuRowVisibility>,
    ): Either<StorageError, Unit> = write { it.copy(mainMenuRows = it.mainMenuRows + (row to visibility)) }

    override suspend fun applySyncedPreferences(preferences: SyncedPreferences): Either<StorageError, Unit> = write {
        it.copy(
            darkThemeConfig = newer(it.darkThemeConfig, preferences.darkThemeConfig),
            accentColor = newer(it.accentColor, preferences.accentColor),
            clicker = newer(it.clicker, preferences.clicker),
            mainMenuRows = MainMenuRow.entries
                .mapNotNull { row -> newer(it.mainMenuRows[row], preferences.mainMenuRows[row])?.let { row to it } }
                .toMap(),
        )
    }

    override fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>> =
        combine(offlineModeChosen, readError) { chosen, error -> error?.left() ?: chosen.right() }.untilLeft()

    override suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit> =
        writeError?.left() ?: offlineModeChosen.update { chosen }.right()

    /** Makes the observed flows emit [error] and end. */
    fun setReadError(error: StorageError) {
        readError.value = error
    }

    // Like the real data source, a field is replaced only by a change that isn't older, and a field
    // that was never set never replaces one that was.
    private fun <T> newer(stored: SyncedValue<T>?, applied: SyncedValue<T>?): SyncedValue<T>? = when {
        applied == null -> stored
        stored == null || applied.updatedAt >= stored.updatedAt -> applied
        else -> stored
    }

    private fun write(transform: (SyncedPreferences) -> SyncedPreferences): Either<StorageError, Unit> =
        writeError?.left() ?: preferences.update(transform).right()

    private fun <T> Flow<Either<StorageError, T>>.untilLeft() = transformWhile {
        emit(it)
        it.isRight()
    }
}
