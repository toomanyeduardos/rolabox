package com.eduardoflores.rolabox.core.domain.error

/** Local storage (the database or DataStore) couldn't be read or written. */
sealed interface DatabaseError : FetchError {
    /** The stored data can't be parsed. */
    data object Corrupted : DatabaseError

    /** Reading or writing the storage failed for any other I/O reason. */
    data object Unavailable : DatabaseError
}
