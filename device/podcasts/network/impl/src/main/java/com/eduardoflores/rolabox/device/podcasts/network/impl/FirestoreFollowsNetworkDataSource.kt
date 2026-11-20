package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.raise.either
import com.eduardoflores.rolabox.common.firestore.asLoggedRemoteError
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.common.util.catchNamed
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.Follow
import com.eduardoflores.rolabox.device.podcasts.api.SyncedFollows
import com.eduardoflores.rolabox.device.podcasts.network.api.FollowsNetworkDataSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import dagger.Lazy
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Stores the follows in `users/{uid}/podcastFollows/{showKey}` (ADR-031, Syncing follows), where `showKey` is the
 * show's directory id, `itunes-<collectionId>`, with `feedUrl`, `followed` and `updatedAt`. The fields are checked by
 * `firebase/firestore.rules`, so they change only together with the rules. Sync never deletes a follow: Off is the
 * record that wins over an older On. Only deleting the account does, every one of them (ADR-037).
 *
 * Firestore is created on the first sync, never before, so no-account mode never creates it (ADR-008, rule 8).
 */
internal class FirestoreFollowsNetworkDataSource @Inject constructor(
    private val firestore: Lazy<FirebaseFirestore>,
    private val report: NonFatalReport,
) : FollowsNetworkDataSource {
    override suspend fun merge(
        userId: String,
        merge: (remote: SyncedFollows) -> SyncedFollows,
    ): Either<RemoteError, SyncedFollows> = catchNamed({ it.asLoggedRemoteError(report) }) {
        val db = firestore.get()
        val follows = db.collection(USERS).document(userId).collection(PODCAST_FOLLOWS)
        // A transaction can't run a query, so the documents there are listed first, and each is read again inside it,
        // where a write from another phone makes it start over.
        val listed = follows.get().await().documents.map { DirectoryId(it.id) }
        db.runTransaction { transaction ->
            var remote = transaction.read(follows, listed)
            var merged = merge(remote)
            // A follow this phone adds may have been added by another phone since the list: reading it in the
            // transaction too means it is merged, never overwritten.
            val appeared = transaction.read(follows, merged.keys - listed.toSet())
            if (appeared.isNotEmpty()) {
                remote = remote + appeared
                merged = merge(remote)
            }
            merged.forEach { (directoryId, follow) ->
                if (remote[directoryId] !=
                    follow
                ) {
                    transaction.set(follows.document(directoryId.value), follow.toDocument())
                }
            }
            merged
        }.await()
    }

    // Listed from the server, so offline fails here and deletes nothing. Each batch waits for the server too:
    // offline, Firestore would queue it and never fail it, which would read as done (ADR-037, rule 4).
    override suspend fun delete(userId: String): Either<RemoteError, Unit> =
        catchNamed({ it.asLoggedRemoteError(report) }) {
            val db = firestore.get()
            val follows = db.collection(USERS).document(userId).collection(PODCAST_FOLLOWS)
            val listed = follows.get(Source.SERVER).await().documents
            either {
                listed.chunked(MAX_WRITES_PER_BATCH).forEach { chunk ->
                    withinServerTimeout {
                        db.batch().apply { chunk.forEach { delete(it.reference) } }.commit().await()
                    }.bind()
                }
            }
        }.flatMap { it }

    private companion object {
        const val USERS = "users"
        const val PODCAST_FOLLOWS = "podcastFollows"

        // Firestore's limit of writes in one batch.
        const val MAX_WRITES_PER_BATCH = 500
    }
}

private const val FEED_URL = "feedUrl"
private const val FOLLOWED = "followed"
private const val UPDATED_AT = "updatedAt"

private fun Transaction.read(follows: CollectionReference, ids: Collection<DirectoryId>): SyncedFollows =
    ids.mapNotNull { id -> get(follows.document(id.value)).toFollow()?.let { id to it } }.toMap()

// A document that isn't there, or has fields this version can't read, is no follow.
private fun DocumentSnapshot.toFollow(): SyncedValue<Follow>? {
    val feedUrl = getString(FEED_URL)
    val followed = getBoolean(FOLLOWED)
    val updatedAt = getTimestamp(UPDATED_AT)
    return if (feedUrl == null || followed == null || updatedAt == null) {
        null
    } else {
        SyncedValue(Follow(feedUrl, followed), SyncTimestamp(updatedAt.toDate().time))
    }
}

private fun SyncedValue<Follow>.toDocument(): Map<String, Any> = mapOf(
    FEED_URL to value.feedUrl,
    FOLLOWED to value.followed,
    UPDATED_AT to Timestamp(Date(updatedAt.epochMillis)),
)
