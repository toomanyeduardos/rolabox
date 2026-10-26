package com.eduardoflores.rolabox.common.userdata.impl

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.SyncError
import com.eduardoflores.rolabox.common.sync.api.Syncer
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.SyncedPreferences
import com.eduardoflores.rolabox.common.userdata.local.api.PreferencesLocalDataSource
import com.eduardoflores.rolabox.common.userdata.network.api.PreferencesNetworkDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Syncs the preferences field by field, with last-write-wins (ADR-011). It is the only class that
 * uses both the local and the network side of the preferences (ADR-022, rule 2).
 */
internal class PreferencesSyncer @Inject constructor(
    private val local: PreferencesLocalDataSource,
    private val network: PreferencesNetworkDataSource,
    private val lastWriteWins: LastWriteWins,
) : Syncer {
    // The first value is what's stored when collection starts, not a change.
    override fun observeLocalChanges(): Flow<Unit> = local.observeSyncedPreferences()
        .drop(1)
        // A read error isn't a change, so it's dropped here (ADR-007 rule 7). It isn't lost: sync()
        // reads the same storage through getSyncedPreferences() and reports it there, where it's
        // retried or failed. The Left ends this flow, so local changes stop triggering sync until
        // the app restarts, but sign-in and foreground still do. Retrying here instead would loop on
        // a corrupted file.
        .filter { it.isRight() }
        .map { }

    override suspend fun sync(userId: String): Either<SyncError, Unit> = either {
        val localPreferences = local.getSyncedPreferences().mapLeft(SyncError::Local).bind()
        val merged = network.merge(userId) { remotePreferences -> localPreferences.mergedWith(remotePreferences) }
            .mapLeft(SyncError::Remote)
            .bind()
        // Applying checks each field again, so a change made while this sync ran isn't overwritten.
        if (merged != localPreferences) {
            local.applySyncedPreferences(merged).mapLeft(SyncError::Local).bind()
        }
    }

    // Each field is resolved on its own, so a change to one never overwrites a newer change to another.
    // Each main menu row is a field (ADR-011, rule 9), so hiding two rows on two devices keeps both.
    private fun SyncedPreferences.mergedWith(remote: SyncedPreferences) = SyncedPreferences(
        darkThemeConfig = lastWriteWins.resolve(darkThemeConfig, remote.darkThemeConfig),
        accentColor = lastWriteWins.resolve(accentColor, remote.accentColor),
        clicker = lastWriteWins.resolve(clicker, remote.clicker),
        mainMenuRows = MainMenuRow.entries
            .mapNotNull { row -> lastWriteWins.resolve(mainMenuRows[row], remote.mainMenuRows[row])?.let { row to it } }
            .toMap(),
    )
}
