package com.eduardoflores.rolabox.core.userdata.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.core.sync.api.SyncedValue
import com.eduardoflores.rolabox.core.userdata.api.AccentColor
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.core.userdata.api.SyncedPreferencesRepository
import com.eduardoflores.rolabox.core.userdata.api.UserData
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

/** Both views of the same preferences, so a change through one is seen through the other. */
@Singleton
class FakeUserDataRepository @Inject constructor() :
    UserDataRepository,
    SyncedPreferencesRepository {
    private val preferences = MutableStateFlow(SyncedPreferences())
    private val readError = MutableStateFlow<StorageError?>(null)

    /** When set, writes fail with this error and leave the data unchanged. */
    var writeError: StorageError? = null

    /** The time the next set is stamped with. */
    var now = SyncTimestamp(1)

    override fun observeUserData(): Flow<Either<StorageError, UserData>> = observeSyncedPreferences()
        .map { result ->
            result.map {
                UserData(
                    darkThemeConfig = it.darkThemeConfig?.value ?: DarkThemeConfig.FOLLOW_SYSTEM,
                    accentColor = it.accentColor?.value ?: AccentColor.BLUE,
                )
            }
        }
        .distinctUntilChanged()

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit> =
        write { it.copy(darkThemeConfig = SyncedValue(config, now)) }

    override suspend fun setAccentColor(color: AccentColor): Either<StorageError, Unit> =
        write { it.copy(accentColor = SyncedValue(color, now)) }

    // Like a real repository, a Left ends the flow.
    override fun observeSyncedPreferences(): Flow<Either<StorageError, SyncedPreferences>> =
        combine(preferences, readError) { stored, error -> error?.left() ?: stored.right() }
            .transformWhile {
                emit(it)
                it.isRight()
            }

    override suspend fun getSyncedPreferences(): Either<StorageError, SyncedPreferences> =
        observeSyncedPreferences().first()

    override suspend fun applySyncedPreferences(preferences: SyncedPreferences): Either<StorageError, Unit> =
        write { it.merge(preferences) }

    /** Makes the observed flows emit [error] and end. */
    fun setReadError(error: StorageError) {
        readError.value = error
    }

    private fun write(transform: (SyncedPreferences) -> SyncedPreferences): Either<StorageError, Unit> =
        writeError?.left() ?: preferences.update(transform).right()
}
