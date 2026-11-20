package com.eduardoflores.rolabox.common.sync.impl

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Keeps sync away from data while an account's data is deleted (ADR-037). [pausedFor] is the user
 * whose sync is paused, and [running] is held by a sync while it runs and by the delete, so neither
 * runs during the other.
 *
 * It lives in memory, so a process that dies while paused starts unpaused. Sync then writes the
 * phone's data back into the account, which wasn't deleted, and nothing is lost.
 */
@Singleton
internal class SyncPause @Inject constructor() {
    val pausedFor = MutableStateFlow<String?>(null)
    val running = Mutex()

    fun isPaused(userId: String): Boolean = pausedFor.value == userId
}
