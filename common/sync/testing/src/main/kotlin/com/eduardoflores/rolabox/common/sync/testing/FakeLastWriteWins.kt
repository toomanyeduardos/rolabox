package com.eduardoflores.rolabox.common.sync.testing

import com.eduardoflores.rolabox.common.sync.api.LastWriteWins
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the way the real one does, so the tests of what stores synced data read as they would in
 * the app: the later change wins, and the remote one on a tie. The rule itself is tested in
 * `:common:sync:impl`.
 */
@Singleton
class FakeLastWriteWins @Inject constructor() : LastWriteWins {
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
