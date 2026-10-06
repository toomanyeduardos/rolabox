package com.eduardoflores.rolabox.common.storage.api

/** Local storage couldn't be read or written, whatever it's backed by (ADR-007). */
sealed interface StorageError {
    /** The stored data can't be parsed. */
    data object Corrupted : StorageError

    /** Reading or writing the storage failed for any other I/O reason. */
    data object Unavailable : StorageError
}
