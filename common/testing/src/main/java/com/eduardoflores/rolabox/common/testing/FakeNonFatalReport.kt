package com.eduardoflores.rolabox.common.testing

import com.eduardoflores.rolabox.common.util.NonFatalReport

/** Keeps the kind of every report, so a test can check what was reported, and that an expected error wasn't. */
class FakeNonFatalReport : NonFatalReport {
    private val reported = mutableListOf<String>()

    /** The kinds reported so far, in order. */
    val kinds: List<String> get() = reported.toList()

    override fun report(kind: String, cause: Throwable?) {
        reported += kind
    }
}
