package com.eduardoflores.rolabox.common.sync.impl

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code

/**
 * Names the Firestore exceptions that become errors (ADR-007). Returns null for anything else,
 * which the caller rethrows.
 */
internal fun Throwable.asRemoteError(): RemoteError? = (this as? FirebaseFirestoreException)?.let {
    when (it.code) {
        // An expired token is refreshed by the next attempt.
        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.RESOURCE_EXHAUSTED, Code.UNAUTHENTICATED ->
            RemoteError.Unavailable

        Code.PERMISSION_DENIED, Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION -> RemoteError.Rejected

        else -> null
    }
}
