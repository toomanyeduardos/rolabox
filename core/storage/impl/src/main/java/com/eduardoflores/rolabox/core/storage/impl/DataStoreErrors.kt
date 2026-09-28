package com.eduardoflores.rolabox.core.storage.impl

import androidx.datastore.core.CorruptionException
import com.eduardoflores.rolabox.core.storage.api.StorageError
import java.io.IOException

/**
 * Names the DataStore exceptions that become errors (ADR-007). Returns null for anything else,
 * which the caller rethrows.
 */
internal fun Throwable.asDataStoreError(): StorageError? = when (this) {
    is CorruptionException -> StorageError.Corrupted
    is IOException -> StorageError.Unavailable
    else -> null
}
