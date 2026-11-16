package com.eduardoflores.rolabox.common.util

/**
 * Reports an error that means something is broken, without crashing (ADR-030). It's called where the exception
 * becomes a typed error, and only for errors that aren't expected outcomes: no connection, a deleted file or a wrong
 * password are never reported.
 *
 * [kind] names the typed error and, where there is one, the backend's code, as in
 * `"RemoteError.Rejected PERMISSION_DENIED"`. It never holds a field's value, a URI, a path, an ID or an exception's
 * message. [cause] is the exception that was converted: its stack trace is sent, and its message isn't. It's null
 * where there is no exception, and the caller's stack trace is sent instead.
 */
fun interface NonFatalReport {
    fun report(kind: String, cause: Throwable?)
}
