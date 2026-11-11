package com.eduardoflores.rolabox.common.sync.impl

import android.util.Log
import com.eduardoflores.rolabox.common.sync.api.SyncError
import javax.inject.Inject

/**
 * Where [SyncRunner] reports each error a `Syncer` returns (ADR-029). It's an interface so that the
 * runner's tests can check that no error goes unreported.
 */
internal fun interface SyncFailureLog {
    fun failed(error: SyncError, outcome: SyncOutcome)
}

/**
 * Writes each sync error to logcat, in every build type (ADR-029). The line holds the error's type,
 * never the user or a synced value. Why the backend failed is logged by the `:network:impl` that
 * converted its exception.
 */
internal class LogcatSyncFailureLog @Inject constructor() : SyncFailureLog {
    override fun failed(error: SyncError, outcome: SyncOutcome) {
        Log.w(TAG, "Sync failed with $error, so this run is $outcome")
    }

    private companion object {
        const val TAG = "Sync"
    }
}
