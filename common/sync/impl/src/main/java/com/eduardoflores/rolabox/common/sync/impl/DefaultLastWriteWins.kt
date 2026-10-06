package com.eduardoflores.rolabox.common.sync.impl

import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import javax.inject.Inject

internal class DefaultLastWriteWins @Inject constructor() : LastWriteWins {
    override fun <T> resolve(local: SyncedValue<T>?, remote: SyncedValue<T>?): SyncedValue<T>? = when {
        local == null -> remote
        remote == null -> local
        local.updatedAt > remote.updatedAt -> local
        else -> remote
    }

    override fun <K, V : Any> mergeById(
        local: Map<K, SyncedValue<V?>>,
        remote: Map<K, SyncedValue<V?>>,
    ): Map<K, SyncedValue<V?>> = (local.keys + remote.keys).associateWith { id ->
        checkNotNull(resolve(local[id], remote[id])) { "id $id is in one of the maps" }
    }
}
