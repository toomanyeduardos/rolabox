package com.eduardoflores.rolabox.common.userdata.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferencesRepository
import com.eduardoflores.rolabox.common.userdata.api.UserData
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import java.time.Clock
import javax.inject.Inject
import kotlin.enums.EnumEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DefaultUserDataRepository @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val clock: Clock,
    private val lastWriteWins: LastWriteWins,
) : UserDataRepository,
    SyncedPreferencesRepository {
    override fun observeUserData(): Flow<Either<StorageError, UserData>> = observeSyncedPreferences()
        .map { result -> result.map { it.toUserData() } }
        // A sync that only changes timestamps changes nothing a screen shows.
        .distinctUntilChanged()

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit> =
        set(DarkThemeConfigField, config)

    override suspend fun setAccentColor(color: AccentColor): Either<StorageError, Unit> = set(AccentColorField, color)

    override fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>> =
        preferencesStore.observeString(OFFLINE_MODE_CHOSEN_KEY).map { result -> result.map { it == TRUE } }

    override suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit> =
        preferencesStore.setString(OFFLINE_MODE_CHOSEN_KEY, chosen.toString())

    override fun observeSyncedPreferences(): Flow<Either<StorageError, SyncedPreferences>> =
        preferencesStore.observeStrings(ALL_KEYS).map { result -> result.map { it.toSyncedPreferences() } }

    override suspend fun getSyncedPreferences(): Either<StorageError, SyncedPreferences> =
        observeSyncedPreferences().first()

    override suspend fun applySyncedPreferences(preferences: SyncedPreferences): Either<StorageError, Unit> =
        preferencesStore.updateStrings(ALL_KEYS) { stored ->
            val merged = stored.toSyncedPreferences().mergedWith(preferences)
            DarkThemeConfigField.toStored(merged.darkThemeConfig) + AccentColorField.toStored(merged.accentColor)
        }

    // Each field is resolved on its own, with [remote] as the remote side.
    private fun SyncedPreferences.mergedWith(remote: SyncedPreferences) = SyncedPreferences(
        darkThemeConfig = lastWriteWins.resolve(darkThemeConfig, remote.darkThemeConfig),
        accentColor = lastWriteWins.resolve(accentColor, remote.accentColor),
    )

    private suspend fun <T : Enum<T>> set(field: StoredField<T>, value: T): Either<StorageError, Unit> {
        val entries = field.toStored(SyncedValue(value, SyncTimestamp(clock.millis())))
        return preferencesStore.updateStrings(entries.keys) { entries }
    }
}

// Not one of the synced fields below: it's stored on this device only. The key is stored on users'
// devices, so it doesn't change.
private const val OFFLINE_MODE_CHOSEN_KEY = "offline_mode_chosen"
private const val TRUE = "true"

/**
 * A synced preference stored under two keys: its value (an enum name) and when it was last changed
 * (epoch milliseconds). Both keys are stored on users' devices, so they don't change.
 */
private class StoredField<T : Enum<T>>(val key: String, private val entries: EnumEntries<T>, val default: T) {
    val updatedAtKey = "${key}_updated_at"

    fun fromStored(stored: Map<String, String?>): SyncedValue<T>? {
        val value = stored[key] ?: return null
        // A value stored before preferences carried timestamps counts as the oldest possible change.
        val updatedAt = stored[updatedAtKey]?.toLongOrNull()?.let(::SyncTimestamp) ?: SyncTimestamp.Epoch
        // A value from a version that no longer exists reads as the default.
        return SyncedValue(entries.firstOrNull { it.name == value } ?: default, updatedAt)
    }

    fun toStored(synced: SyncedValue<T>?): Map<String, String> = synced?.let {
        mapOf(key to it.value.name, updatedAtKey to it.updatedAt.epochMillis.toString())
    }.orEmpty()
}

private val DarkThemeConfigField =
    StoredField("dark_theme_config", DarkThemeConfig.entries, DarkThemeConfig.FOLLOW_SYSTEM)
private val AccentColorField = StoredField("accent_color", AccentColor.entries, AccentColor.BLUE)

private val ALL_KEYS = listOf(DarkThemeConfigField, AccentColorField).flatMap {
    listOf(it.key, it.updatedAtKey)
}.toSet()

private fun Map<String, String?>.toSyncedPreferences() = SyncedPreferences(
    darkThemeConfig = DarkThemeConfigField.fromStored(this),
    accentColor = AccentColorField.fromStored(this),
)

private fun SyncedPreferences.toUserData() = UserData(
    darkThemeConfig = darkThemeConfig?.value ?: DarkThemeConfigField.default,
    accentColor = accentColor?.value ?: AccentColorField.default,
)
