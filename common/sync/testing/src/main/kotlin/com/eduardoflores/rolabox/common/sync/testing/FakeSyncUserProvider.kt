package com.eduardoflores.rolabox.common.sync.testing

import com.eduardoflores.rolabox.common.sync.api.SyncUserProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class FakeSyncUserProvider @Inject constructor() : SyncUserProvider {
    /** The user to sync for. Null, the default, is nobody. */
    val syncUser = MutableStateFlow<String?>(null)

    override fun observeSyncUser(): Flow<String?> = syncUser
}
