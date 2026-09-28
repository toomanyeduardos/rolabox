package com.eduardoflores.rolabox.core.sync.testing

import com.eduardoflores.rolabox.core.sync.api.SyncManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSyncManager @Inject constructor() : SyncManager {
    var syncRequests = 0
        private set

    override fun requestSync() {
        syncRequests++
    }
}
