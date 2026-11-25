package com.eduardoflores.rolabox.device.podcasts.network.impl

import com.eduardoflores.rolabox.common.testing.FakeNonFatalReport
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreEpisodeOrdersNetworkDataSourceTest {
    private var created = false
    private val firestore = Lazy<FirebaseFirestore> {
        created = true
        throw FirestoreCreated()
    }

    // ADR-008 rule 8: Firestore is created by the first call, which only the choices' Syncer makes (ADR-022, rule 6).
    @Test
    fun creatingTheDataSource_doesNotCreateFirestore() {
        FirestoreEpisodeOrdersNetworkDataSource(firestore, FakeNonFatalReport())

        assertFalse(created)
    }

    @Test
    fun merging_createsFirestore() {
        val dataSource = FirestoreEpisodeOrdersNetworkDataSource(firestore, FakeNonFatalReport())

        assertThrows(FirestoreCreated::class.java) { runBlocking { dataSource.merge(USER) { it } } }
        assertTrue(created)
    }

    @Test
    fun deleting_createsFirestore() {
        val dataSource = FirestoreEpisodeOrdersNetworkDataSource(firestore, FakeNonFatalReport())

        assertThrows(FirestoreCreated::class.java) { runBlocking { dataSource.delete(USER) } }
        assertTrue(created)
    }

    private class FirestoreCreated : RuntimeException()

    private companion object {
        const val USER = "alice"
    }
}
