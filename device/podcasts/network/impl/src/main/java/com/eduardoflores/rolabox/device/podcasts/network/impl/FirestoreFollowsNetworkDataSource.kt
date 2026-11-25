package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.device.podcasts.api.Follow
import com.eduardoflores.rolabox.device.podcasts.api.SyncedFollows
import com.eduardoflores.rolabox.device.podcasts.network.api.FollowsNetworkDataSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Lazy
import java.util.Date
import javax.inject.Inject

/**
 * Stores the follows in `users/{uid}/podcastFollows/{showKey}` (ADR-031, Syncing follows), where `showKey` is the
 * show's directory id, `itunes-<collectionId>`, with `feedUrl`, `followed` and `updatedAt`. The fields are checked by
 * `firebase/firestore.rules`, so they change only together with the rules. Sync never deletes a follow: Off is the
 * record that wins over an older On. Only deleting the account does, every one of them (ADR-037).
 *
 * Firestore is created on the first sync, never before, so no-account mode never creates it (ADR-008, rule 8).
 */
internal class FirestoreFollowsNetworkDataSource @Inject constructor(
    firestore: Lazy<FirebaseFirestore>,
    report: NonFatalReport,
) : FollowsNetworkDataSource {
    private val follows = ShowDocuments(firestore, report, PODCAST_FOLLOWS, ::followOf, ::documentOf)

    override suspend fun merge(
        userId: String,
        merge: (remote: SyncedFollows) -> SyncedFollows,
    ): Either<RemoteError, SyncedFollows> = follows.merge(userId, merge)

    override suspend fun delete(userId: String): Either<RemoteError, Unit> = follows.delete(userId)
}

private const val PODCAST_FOLLOWS = "podcastFollows"
private const val FEED_URL = "feedUrl"
private const val FOLLOWED = "followed"
private const val UPDATED_AT = "updatedAt"

// A document that isn't there, or has fields this version can't read, is no follow.
private fun followOf(document: DocumentSnapshot): SyncedValue<Follow>? {
    val feedUrl = document.getString(FEED_URL)
    val followed = document.getBoolean(FOLLOWED)
    val updatedAt = document.getTimestamp(UPDATED_AT)
    return if (feedUrl == null || followed == null || updatedAt == null) {
        null
    } else {
        SyncedValue(Follow(feedUrl, followed), SyncTimestamp(updatedAt.toDate().time))
    }
}

private fun documentOf(follow: SyncedValue<Follow>): Map<String, Any> = mapOf(
    FEED_URL to follow.value.feedUrl,
    FOLLOWED to follow.value.followed,
    UPDATED_AT to Timestamp(Date(follow.updatedAt.epochMillis)),
)
