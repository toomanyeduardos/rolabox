package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.firestore.asLoggedRemoteError
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import dagger.Lazy
import kotlinx.coroutines.tasks.await

/**
 * A collection of the user's documents, `users/{uid}/[collection]/{showKey}`, one per show, named by its directory id,
 * each a value synced with last-write-wins as a whole: the follows (ADR-031) and the choices of episode order
 * (ADR-043). [read] is a document's value, or `null` when it isn't there or has fields this version can't read, and
 * [write] the document of a value. Sync never deletes a document: only deleting the account does, every one
 * (ADR-037).
 *
 * Firestore is created on the first call, never before, so no-account mode never creates it (ADR-008, rule 8).
 */
internal class ShowDocuments<T>(
    private val firestore: Lazy<FirebaseFirestore>,
    private val report: NonFatalReport,
    private val collection: String,
    private val read: (DocumentSnapshot) -> SyncedValue<T>?,
    private val write: (SyncedValue<T>) -> Map<String, Any>,
) {
    /**
     * Reads the user's documents, and writes back each value [merge] returns that differs from the remote one, as one
     * transaction. Returns the merged values.
     */
    suspend fun merge(
        userId: String,
        merge: (remote: Map<DirectoryId, SyncedValue<T>>) -> Map<DirectoryId, SyncedValue<T>>,
    ): Either<RemoteError, Map<DirectoryId, SyncedValue<T>>> = catchNamed({ it.asLoggedRemoteError(report) }) {
        val db = firestore.get()
        val documents = db.collection(USERS).document(userId).collection(collection)
        // A transaction can't run a query, so the documents there are listed first, and each is read again inside it,
        // where a write from another phone makes it start over.
        val listed = documents.get().await().documents.map { DirectoryId(it.id) }
        db.runTransaction { transaction ->
            var remote = transaction.read(documents, listed)
            var merged = merge(remote)
            // A document this phone adds may have been added by another phone since the list: reading it in the
            // transaction too means it is merged, never overwritten.
            val appeared = transaction.read(documents, merged.keys - listed.toSet())
            if (appeared.isNotEmpty()) {
                remote = remote + appeared
                merged = merge(remote)
            }
            merged.forEach { (directoryId, value) ->
                if (remote[directoryId] != value) transaction.set(documents.document(directoryId.value), write(value))
            }
            merged
        }.await()
    }

    // Listed from the server, so offline fails here and deletes nothing. Each batch waits for the server too:
    // offline, Firestore would queue it and never fail it, which would read as done (ADR-037, rule 4).
    suspend fun delete(userId: String): Either<RemoteError, Unit> = catchNamed({ it.asLoggedRemoteError(report) }) {
        val db = firestore.get()
        val documents = db.collection(USERS).document(userId).collection(collection)
        val listed = documents.get(Source.SERVER).await().documents
        either {
            listed.chunked(MAX_WRITES_PER_BATCH).forEach { chunk ->
                withinServerTimeout {
                    db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
                }.bind()
            }
        }
    }.flatMap { it }

    private fun Transaction.read(
        documents: CollectionReference,
        ids: Collection<DirectoryId>,
    ): Map<DirectoryId, SyncedValue<T>> =
        ids.mapNotNull { id -> read(get(documents.document(id.value)))?.let { id to it } }.toMap()

    private companion object {
        const val USERS = "users"

        // Firestore's limit of writes in one batch.
        const val MAX_WRITES_PER_BATCH = 500
    }
}
