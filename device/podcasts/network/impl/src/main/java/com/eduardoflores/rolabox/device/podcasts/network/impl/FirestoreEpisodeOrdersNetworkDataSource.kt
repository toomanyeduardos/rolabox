package com.eduardoflores.rolabox.device.podcasts.network.impl

import arrow.core.Either
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.common.util.NonFatalReport
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice
import com.eduardoflores.rolabox.device.podcasts.api.SyncedEpisodeOrders
import com.eduardoflores.rolabox.device.podcasts.network.api.EpisodeOrdersNetworkDataSource
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Lazy
import java.util.Date
import javax.inject.Inject

/**
 * Stores the user's choices of episode order in `users/{uid}/podcastEpisodeOrders/{showKey}` (ADR-043, Syncing the
 * choice), where `showKey` is the show's directory id, with `value`, the choice's name, and `updatedAt`. The fields
 * are checked by `firebase/firestore.rules`, so they change only together with the rules. Apart from the follows, so
 * a phone that doesn't know the choices never writes over one. A cleared choice is `Feed`, never a deleted document:
 * only deleting the account deletes them, every one (ADR-037).
 *
 * Firestore is created on the first sync, never before, so no-account mode never creates it (ADR-008, rule 8).
 */
internal class FirestoreEpisodeOrdersNetworkDataSource @Inject constructor(
    firestore: Lazy<FirebaseFirestore>,
    report: NonFatalReport,
) : EpisodeOrdersNetworkDataSource {
    private val choices = ShowDocuments(firestore, report, PODCAST_EPISODE_ORDERS, ::choiceOf, ::documentOf)

    override suspend fun merge(
        userId: String,
        merge: (remote: SyncedEpisodeOrders) -> SyncedEpisodeOrders,
    ): Either<RemoteError, SyncedEpisodeOrders> = choices.merge(userId, merge)

    override suspend fun delete(userId: String): Either<RemoteError, Unit> = choices.delete(userId)
}

private const val PODCAST_EPISODE_ORDERS = "podcastEpisodeOrders"
private const val VALUE = "value"
private const val UPDATED_AT = "updatedAt"

// A document that isn't there, or has a value this version doesn't know, is no choice.
private fun choiceOf(document: DocumentSnapshot): SyncedValue<EpisodeOrderChoice>? {
    val value = document.getString(VALUE)?.let { name -> EpisodeOrderChoice.entries.firstOrNull { it.name == name } }
    val updatedAt = document.getTimestamp(UPDATED_AT)
    return if (value == null || updatedAt == null) null else SyncedValue(value, SyncTimestamp(updatedAt.toDate().time))
}

private fun documentOf(choice: SyncedValue<EpisodeOrderChoice>): Map<String, Any> = mapOf(
    VALUE to choice.value.name,
    UPDATED_AT to Timestamp(Date(choice.updatedAt.epochMillis)),
)
