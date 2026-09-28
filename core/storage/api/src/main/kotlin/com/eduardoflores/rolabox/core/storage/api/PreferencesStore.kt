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

    suspend fun setString(key: String, value: String): Either<StorageError, Unit>
}
