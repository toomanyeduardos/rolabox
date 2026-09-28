package com.eduardoflores.rolabox.core.sync.impl

import arrow.core.Either
import arrow.core.raise.either
import com.eduardoflores.rolabox.core.userdata.api.SyncedPreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/** Syncs the preferences field by field, with last-write-wins (ADR-011). */
internal class PreferencesSyncer @Inject constructor(
    private val local: SyncedPreferencesRepository,
    private val remote: RemotePreferences,
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
        val merged = remote.merge(userId) { remotePreferences -> localPreferences.merge(remotePreferences) }
            .mapLeft(SyncError::Remote)
            .bind()
        // Applying checks each field again, so a change made while this sync ran isn't overwritten.
        if (merged != localPreferences) {
            local.applySyncedPreferences(merged).mapLeft(SyncError::Local).bind()
        }
    }
}
