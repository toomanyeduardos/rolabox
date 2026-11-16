package com.eduardoflores.rolabox.common.crashreporting.impl

import android.util.Log
import com.eduardoflores.rolabox.common.util.NonFatalReport
import javax.inject.Inject

private const val TAG = "NonFatalReport"

/**
 * Debug builds don't start Crashlytics (ADR-030 rule 7), so a report goes to logcat, where the developer at the
 * device sees it, with the cause's full message.
 */
internal class LogcatNonFatalReport @Inject constructor() : NonFatalReport {
    override fun report(kind: String, cause: Throwable?) {
        Log.w(TAG, "Would report $kind to Crashlytics in a release build", cause)
    }
}
