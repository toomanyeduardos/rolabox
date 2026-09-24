package com.eduardoflores.rolabox.core.domain.error

/**
 * Couldn't get the data. The parent of every infrastructure error, so an `Either<DatabaseError, T>`
 * can be used where an `Either<FetchError, T>` is expected (ADR-007).
 */
sealed interface FetchError
