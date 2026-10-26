package com.eduardoflores.rolabox.common.userdata.network.impl

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Lazy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestorePreferencesNetworkDataSourceTest {
    private var created = false
    private val firestore = Lazy<FirebaseFirestore> {
        created = true
        throw FirestoreCreated()
    }

    // ADR-008 rule 8: Firestore is created by the first call, which only a sync makes (ADR-022, rule 6).
    // That a sync runs for nobody in offline mode is tested by the runner, in :common:sync:impl.
    @Test
    fun creatingTheDataSource_doesNotCreateFirestore() {
        FirestorePreferencesNetworkDataSource(firestore)

        assertFalse(created)
    }

    @Test
    fun merging_createsFirestore() {
        val dataSource = FirestorePreferencesNetworkDataSource(firestore)

        assertThrows(FirestoreCreated::class.java) { runBlocking { dataSource.merge(USER) { it } } }
        assertTrue(created)
    }

    private class FirestoreCreated : RuntimeException()

    private companion object {
        const val USER = "alice"
    }
}
