package com.eduardoflores.rolabox.core.sync.impl

import com.eduardoflores.rolabox.core.sync.api.SyncRepository
import javax.inject.Inject

// The offline flavor has no backend to sync with (ADR-008).
internal class NoOpSyncRepository @Inject constructor() : SyncRepository {
    override fun start() = Unit

    override fun requestSync() = Unit
}
