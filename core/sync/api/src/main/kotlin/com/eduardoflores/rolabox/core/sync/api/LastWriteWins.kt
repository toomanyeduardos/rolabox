package com.eduardoflores.rolabox.core.sync.api

/** How synced data resolves conflicts (ADR-011). */
object LastWriteWins {
    /**
     * The more recently changed of [local] and [remote]. Null means the value was never set on that
     * side, so it never wins over a value that was. On a tie, [remote] wins, so every device that
     * merges the same two values keeps the same one.
     */
    fun <T> resolve(local: SyncedValue<T>?, remote: SyncedValue<T>?): SyncedValue<T>? = when {
        local == null -> remote
        remote == null -> local
        local.updatedAt > remote.updatedAt -> local
        else -> remote
    }

    /**
     * Merges two collections item by item, keyed by id, resolving each item on its own. A removed
     * item is kept as a tombstone (a value of null) with the time it was removed, so a removal wins
     * over an older add on another device, and an add wins over an older removal. Replacing the
     * whole collection instead would drop an item added on one device while another was offline.
     */
    fun <K, V : Any> mergeById(
        local: Map<K, SyncedValue<V?>>,
        remote: Map<K, SyncedValue<V?>>,
    ): Map<K, SyncedValue<V?>> = (local.keys + remote.keys).associateWith { id ->
        checkNotNull(resolve(local[id], remote[id])) { "id $id is in one of the maps" }
    }
}
