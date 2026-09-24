package com.eduardoflores.rolabox.core.datastore

import androidx.datastore.core.CorruptionException
import com.eduardoflores.rolabox.core.domain.error.DatabaseError
import java.io.IOException

/**
 * Names the DataStore exceptions the data layer converts into errors (ADR-007). Returns null for
 * anything else, which the caller rethrows.
 */
fun Throwable.asDataStoreError(): DatabaseError? = when (this) {
    is CorruptionException -> DatabaseError.Corrupted
    is IOException -> DatabaseError.Unavailable
    else -> null
}
