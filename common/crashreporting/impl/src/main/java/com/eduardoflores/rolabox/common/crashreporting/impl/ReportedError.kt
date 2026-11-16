package com.eduardoflores.rolabox.common.crashreporting.impl

/**
 * What Crashlytics records for a non-fatal error (ADR-030 rule 5): the kind as its message, and the stack trace of the
 * exception that caused it, copied. The cause itself isn't attached, so its message and its own causes, which can hold
 * the uid, a path or a song's name, never leave the phone.
 */
internal class ReportedError(kind: String, stackTrace: Array<StackTraceElement>) : Exception(kind) {
    init {
        this.stackTrace = stackTrace
    }

    companion object {
        /** The error for [kind], with [cause]'s stack trace, or the caller's when there's no cause. */
        fun of(kind: String, cause: Throwable?): ReportedError =
            ReportedError(kind, cause?.stackTrace ?: Thread.currentThread().stackTrace)
    }
}
