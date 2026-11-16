package com.eduardoflores.rolabox.common.crashreporting.impl

import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject

/**
 * Records non-fatal errors in Crashlytics, as a [ReportedError] (ADR-030). This is the one place that calls
 * Crashlytics, and it sets no user ID, custom key or log line (rule 3). It's bound in release builds only: debug
 * builds don't start Crashlytics (rule 7), and bind [NonFatalReport] to logcat. With collection off, nothing is
 * recorded, so nothing waits on the phone to be sent later.
 */
internal class CrashlyticsNonFatalReport @Inject constructor() : NonFatalReport {
    override fun report(kind: String, cause: Throwable?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        if (crashlytics.isCrashlyticsCollectionEnabled) crashlytics.recordException(ReportedError.of(kind, cause))
    }
}
