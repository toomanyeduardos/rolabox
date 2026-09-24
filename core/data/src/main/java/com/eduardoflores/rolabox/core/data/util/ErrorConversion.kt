package com.eduardoflores.rolabox.core.data.util

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

// Where exceptions become typed errors (ADR-007). `toError` names the exceptions to convert and
// returns null for anything else, which is rethrown so bugs still crash. Cancellation is never
// converted.

/** Runs [block], returning its result as a `Right`, or a `Left` for an exception [toError] names. */
suspend fun <E, T> catchNamed(toError: (Throwable) -> E?, block: suspend () -> T): Either<E, T> =
    Either.catch { block() }.mapLeft { toError(it) ?: throw it }

/**
 * Wraps each value in a `Right`. An upstream exception that [toError] names is emitted as a `Left`,
 * which ends the flow.
 */
fun <E, T> Flow<T>.catchNamed(toError: (Throwable) -> E?): Flow<Either<E, T>> {
    val values: Flow<Either<E, T>> = map { it.right() }
    return values.catch { emit((toError(it) ?: throw it).left()) }
}
