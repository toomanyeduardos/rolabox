package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.sync.api.SyncManager
import javax.inject.Inject

// Bound by the offline flavor, which has no backend to sync with (ADR-008), and by the cloud flavor
// until there is data to sync.
internal class NoOpSyncManager @Inject constructor() : SyncManager {
    override fun requestSync() = Unit
}
