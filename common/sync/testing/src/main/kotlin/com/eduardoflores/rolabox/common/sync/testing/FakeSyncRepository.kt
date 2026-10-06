package com.eduardoflores.rolabox.common.sync.testing

import com.eduardoflores.rolabox.common.sync.api.SyncRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeSyncRepository @Inject constructor() : SyncRepository {
    var started = false
        private set

    var syncRequests = 0
        private set

    override fun start() {
        started = true
    }

    override fun requestSync() {
        syncRequests++
    }
}
