package com.eduardoflores.rolabox.common.userdata.network.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/** How long a delete waits for the server before it's reported as offline (ADR-037, rule 4). */
internal val SERVER_TIMEOUT = 15.seconds

/**
 * Runs [block], a write that waits for the server, and fails with [RemoteError.Unavailable] when it takes longer than
 * [timeout]. Offline, Firestore queues a write and never fails it, so without a limit it would wait forever, or read
 * as done. A queued write that reaches the server later still lands, which is what the user asked for.
 */
internal suspend fun withinServerTimeout(
    timeout: Duration = SERVER_TIMEOUT,
    block: suspend () -> Unit,
): Either<RemoteError, Unit> = withTimeoutOrNull(timeout) { block() }?.right() ?: RemoteError.Unavailable.left()
