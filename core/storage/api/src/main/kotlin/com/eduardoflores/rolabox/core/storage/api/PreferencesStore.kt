package com.eduardoflores.rolabox.core.storage.api

import arrow.core.Either
import kotlinx.coroutines.flow.Flow

/**
 * Small key-value settings. Each area owns its keys and maps the stored values to its own models,
 * so this store knows nothing about what it holds. Only strings for now; add a type when an area
 * needs it.
 */
interface PreferencesStore {
    /**
     * Emits the value stored under [key], or null when there is none, and again when it changes. A
     * Left ends the flow.
     */
    fun observeString(key: String): Flow<Either<StorageError, String?>>

    /**
     * Emits the values stored under [keys] (null when there is none), all read from the same
     * snapshot, and again when any of them changes. A Left ends the flow.
     */
    fun observeStrings(keys: Set<String>): Flow<Either<StorageError, Map<String, String?>>>

    suspend fun setString(key: String, value: String): Either<StorageError, Unit>

    /**
     * Reads the values stored under [keys] (null when there is none) and writes the entries that
     * [transform] returns, as one atomic change: no other write lands between the read and the
     * write, and either every entry is written or none is. [transform] may run more than once.
     */
    suspend fun updateStrings(
        keys: Set<String>,
        transform: (Map<String, String?>) -> Map<String, String>,
    ): Either<StorageError, Unit>
}
