package com.eduardoflores.rolabox.common.sync.api

/**
 * Keeps the user's synced data in step with the cloud (ADR-011). Local storage stays the source of
 * truth: sync runs in the background, and nothing waits on it. While the user is signed out, or has
 * chosen offline mode, it does nothing, and makes no Firebase requests (ADR-008).
 */
interface SyncRepository {
    /**
     * Starts syncing on sign-in, after local changes, and when the app comes to the foreground. Call
     * it once, from `Application.onCreate`, on the main thread.
     */
    fun start()

    /** Syncs as soon as there is a network connection. Returns without waiting for it. */
    fun requestSync()
}
