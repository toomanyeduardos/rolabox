package com.eduardoflores.rolabox.common.sync.impl

import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LastWriteWinsTest {
    private val lastWriteWins = DefaultLastWriteWins()

    @Test
    fun resolve_localNewer_keepsLocal() {
        assertEquals(synced("local", 2), lastWriteWins.resolve(synced("local", 2), synced("remote", 1)))
    }

    @Test
    fun resolve_remoteNewer_takesRemote() {
        assertEquals(synced("remote", 2), lastWriteWins.resolve(synced("local", 1), synced("remote", 2)))
    }

    @Test
    fun resolve_sameTime_remoteWins() {
        assertEquals(synced("remote", 1), lastWriteWins.resolve(synced("local", 1), synced("remote", 1)))
    }

    @Test
    fun resolve_neverSetLocally_takesRemote() {
        assertEquals(synced("remote", 1), lastWriteWins.resolve(null, synced("remote", 1)))
    }

    @Test
    fun resolve_neverSetRemotely_keepsLocal() {
        assertEquals(synced("local", 1), lastWriteWins.resolve(synced("local", 1), null))
    }

    @Test
    fun resolve_neverSetAnywhere_isNull() {
        assertNull(lastWriteWins.resolve<String>(null, null))
    }

    @Test
    fun resolve_storedBeforeTimestamps_losesToAnyRealChange() {
        val legacy = SyncedValue("local", SyncTimestamp.Epoch)

        assertEquals(synced("remote", 1), lastWriteWins.resolve(legacy, synced("remote", 1)))
    }

    @Test
    fun mergeById_addsOnDifferentDevices_keepsBoth() {
        val local = mapOf("a" to synced<String?>("A", 1))
        val remote = mapOf("b" to synced<String?>("B", 2))

        assertEquals(local + remote, lastWriteWins.mergeById(local, remote))
    }

    @Test
    fun mergeById_removalNewerThanAdd_removalWins() {
        val local = mapOf("a" to synced<String?>(null, 3))
        val remote = mapOf("a" to synced<String?>("A", 2))

        assertEquals(local, lastWriteWins.mergeById(local, remote))
    }

    @Test
    fun mergeById_addNewerThanRemoval_addWins() {
        val local = mapOf("a" to synced<String?>("A", 3))
        val remote = mapOf("a" to synced<String?>(null, 2))

        assertEquals(local, lastWriteWins.mergeById(local, remote))
    }

    @Test
    fun mergeById_resolvesEachItemOnItsOwn() {
        val local = mapOf("a" to synced<String?>("A local", 5), "b" to synced<String?>("B local", 1))
        val remote = mapOf("a" to synced<String?>("A remote", 4), "b" to synced<String?>("B remote", 2))

        assertEquals(
            mapOf("a" to synced<String?>("A local", 5), "b" to synced<String?>("B remote", 2)),
            lastWriteWins.mergeById(local, remote),
        )
    }

    private fun <T> synced(value: T, updatedAt: Long) = SyncedValue(value, SyncTimestamp(updatedAt))
}
