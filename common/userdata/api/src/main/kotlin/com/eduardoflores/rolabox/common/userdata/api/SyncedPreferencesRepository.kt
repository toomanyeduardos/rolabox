package com.eduardoflores.rolabox.common.userdata.api

import arrow.core.Either
import com.eduardoflores.rolabox.common.storage.api.StorageError
import kotlinx.coroutines.flow.Flow

/**
 * The same preferences as [UserDataRepository], with the timestamps sync needs (ADR-011). Only sync
 * uses it; screens use [UserDataRepository].
 */
interface SyncedPreferencesRepository {
    /** Emits the synced preferences, and again whenever one is set or applied. A Left ends the flow. */
    fun observeSyncedPreferences(): Flow<Either<StorageError, SyncedPreferences>>

    suspend fun getSyncedPreferences(): Either<StorageError, SyncedPreferences>

    /**
     * Stores each field of [preferences] that wins over the stored one ([SyncedPreferences.merge],
     * with [preferences] as the remote side), keeping its timestamp. The check and the write are one
     * atomic change, so a local change made while a sync was running is never overwritten by an older
     * value.
     */
    suspend fun applySyncedPreferences(preferences: SyncedPreferences): Either<StorageError, Unit>
}
