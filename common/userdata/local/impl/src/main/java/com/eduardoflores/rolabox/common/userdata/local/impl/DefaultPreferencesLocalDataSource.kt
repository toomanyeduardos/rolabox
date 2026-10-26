package com.eduardoflores.rolabox.common.userdata.local.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.PreferencesStore
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.local.api.PreferencesLocalDataSource
import javax.inject.Inject
import kotlin.enums.EnumEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Stores the preferences in [PreferencesStore], each synced field under two keys (ADR-011). */
internal class DefaultPreferencesLocalDataSource @Inject constructor(
    private val preferencesStore: PreferencesStore,
    private val lastWriteWins: LastWriteWins,
) : PreferencesLocalDataSource {
    override fun observeSyncedPreferences(): Flow<Either<StorageError, SyncedPreferences>> =
        preferencesStore.observeStrings(ALL_KEYS).map { result -> result.map { it.toSyncedPreferences() } }

    override suspend fun getSyncedPreferences(): Either<StorageError, SyncedPreferences> =
        observeSyncedPreferences().first()

    override suspend fun setDarkThemeConfig(config: SyncedValue<DarkThemeConfig>): Either<StorageError, Unit> =
        set(DarkThemeConfigField, config)

    override suspend fun setAccentColor(color: SyncedValue<AccentColor>): Either<StorageError, Unit> =
        set(AccentColorField, color)

    override suspend fun setClicker(clicker: SyncedValue<Clicker>): Either<StorageError, Unit> =
        set(ClickerField, clicker)

    override suspend fun setMainMenuRowVisibility(
        row: MainMenuRow,
        visibility: SyncedValue<MainMenuRowVisibility>,
    ): Either<StorageError, Unit> = set(MainMenuRowFields.getValue(row), visibility)

    override suspend fun applySyncedPreferences(preferences: SyncedPreferences): Either<StorageError, Unit> =
        preferencesStore.updateStrings(ALL_KEYS) { stored ->
            stored.toSyncedPreferences().mergedWith(preferences).toStored()
        }

    override fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>> =
        preferencesStore.observeString(OFFLINE_MODE_CHOSEN_KEY).map { result -> result.map { it == TRUE } }

    override suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit> =
        preferencesStore.setString(OFFLINE_MODE_CHOSEN_KEY, chosen.toString())

    // Each field is resolved on its own, with [remote] as the remote side. Each main menu row too.
    private fun SyncedPreferences.mergedWith(remote: SyncedPreferences) = SyncedPreferences(
        darkThemeConfig = lastWriteWins.resolve(darkThemeConfig, remote.darkThemeConfig),
        accentColor = lastWriteWins.resolve(accentColor, remote.accentColor),
        clicker = lastWriteWins.resolve(clicker, remote.clicker),
        mainMenuRows = MainMenuRow.entries
            .mapNotNull { row -> lastWriteWins.resolve(mainMenuRows[row], remote.mainMenuRows[row])?.let { row to it } }
            .toMap(),
    )

    // The value and its timestamp are written together, or not at all.
    private suspend fun <T : Enum<T>> set(field: StoredField<T>, value: SyncedValue<T>): Either<StorageError, Unit> {
        val entries = field.toStored(value)
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
private class StoredField<T : Enum<T>>(val key: String, private val entries: EnumEntries<T>, private val default: T) {
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
private val ClickerField = StoredField("clicker", Clicker.entries, Clicker.ON)

// Each row is a field of its own (ADR-011, rule 9). The keys are written out, not derived from the
// enum, so renaming a row in code never moves its stored value.
private val MainMenuRowFields = MainMenuRow.entries.associateWith { row ->
    val key = when (row) {
        MainMenuRow.MUSIC -> "main_menu_music"
        MainMenuRow.PODCASTS -> "main_menu_podcasts"
        MainMenuRow.AUDIOBOOKS -> "main_menu_audiobooks"
        MainMenuRow.SHUFFLE_SONGS -> "main_menu_shuffle_songs"
    }
    StoredField(key, MainMenuRowVisibility.entries, MainMenuRowVisibility.SHOWN)
}

private val ALL_KEYS = (listOf(DarkThemeConfigField, AccentColorField, ClickerField) + MainMenuRowFields.values)
    .flatMap { listOf(it.key, it.updatedAtKey) }
    .toSet()

private fun Map<String, String?>.toSyncedPreferences() = SyncedPreferences(
    darkThemeConfig = DarkThemeConfigField.fromStored(this),
    accentColor = AccentColorField.fromStored(this),
    clicker = ClickerField.fromStored(this),
    mainMenuRows = MainMenuRowFields
        .mapNotNull { (row, field) -> field.fromStored(this)?.let { row to it } }
        .toMap(),
)

private fun SyncedPreferences.toStored(): Map<String, String> = buildMap {
    putAll(DarkThemeConfigField.toStored(darkThemeConfig))
    putAll(AccentColorField.toStored(accentColor))
    putAll(ClickerField.toStored(clicker))
    mainMenuRows.forEach { (row, visibility) -> putAll(MainMenuRowFields.getValue(row).toStored(visibility)) }
}
