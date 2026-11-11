package com.eduardoflores.rolabox.common.userdata.network.impl

import android.util.Log
import com.eduardoflores.rolabox.common.sync.api.RemoteError
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

        // A missing document reads as a snapshot that doesn't exist, so NOT_FOUND means the database
        // itself doesn't exist: the project isn't set up.
        Code.PERMISSION_DENIED, Code.INVALID_ARGUMENT, Code.FAILED_PRECONDITION, Code.NOT_FOUND ->
            RemoteError.Rejected

        else -> null
    }
}

/**
 * [asRemoteError], logging each exception it converts with Firestore's code and message, since the
 * [RemoteError] only says whether to try again (ADR-029, rule 2).
 */
internal fun Throwable.asLoggedRemoteError(): RemoteError? = asRemoteError()?.also { error ->
    Log.w(TAG, "Firestore failed with ${(this as FirebaseFirestoreException).code}, so sync gets $error", this)
}

private const val TAG = "FirestoreSync"
