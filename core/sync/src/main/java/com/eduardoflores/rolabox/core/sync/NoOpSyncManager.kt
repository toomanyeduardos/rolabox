package com.eduardoflores.rolabox.core.sync

import javax.inject.Inject

// Placeholder until there is data to sync.
internal class NoOpSyncManager @Inject constructor() : SyncManager {
    override fun requestSync() = Unit
}
