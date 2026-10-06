package com.eduardoflores.rolabox.common.sync.api

import kotlinx.coroutines.flow.Flow

/**
 * Who sync runs for. Sync is common, so it doesn't know what an account or offline mode is: it
 * declares what it needs here, and the area that owns accounts implements it (ADR-020, rule 18).
 */
interface SyncUserProvider {
    /**
     * Emits the id of the user to sync for, or null when sync mustn't run, and again when either
     * changes. While it is null, sync requests nothing and makes no Firebase requests (ADR-008 rule 6).
     */
    fun observeSyncUser(): Flow<String?>
}
