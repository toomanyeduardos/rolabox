package com.eduardoflores.rolabox.common.util

/**
 * Something a part does each time the app's process starts, added to Hilt's set from the part's `:impl`. `:app` runs
 * the set and knows nothing of what is in it (ADR-025). [onProcessStart] runs on the main thread before anything is
 * shown, so it only starts work in the application scope, and never waits for it.
 */
fun interface ProcessStartTask {
    fun onProcessStart()
}
