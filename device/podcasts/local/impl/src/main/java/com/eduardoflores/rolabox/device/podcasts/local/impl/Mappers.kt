package com.eduardoflores.rolabox.device.podcasts.local.impl

import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.Follow
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.api.ShowKind
import com.eduardoflores.rolabox.device.podcasts.api.SyncedFollows
import com.eduardoflores.rolabox.device.podcasts.api.episodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.order
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

internal fun ShowRow.toShow(): Show {
    val feedOrder = showKindOf(kind).episodeOrder
    return Show(
        id = ShowId(id),
        directoryId = DirectoryId(directoryId),
        title = title,
        ownerId = OwnerId(ownerKey),
        ownerName = ownerName,
        isFollowed = followed,
        episodeOrder = episodeOrderChoiceOf(orderChoice)?.order ?: feedOrder,
        feedEpisodeOrder = feedOrder,
    )
}

// A name this version doesn't know is none: the show is listed as if its feed, or the user, said nothing.
private fun showKindOf(name: String?): ShowKind? = ShowKind.entries.firstOrNull { it.name == name }

internal fun EpisodeRow.toRecord(): EpisodeRecord = EpisodeRecord(
    id = EpisodeId(id),
    showId = ShowId(showId),
    itemKey = itemKey,
    title = title,
    publishedAt = publishedAt?.let(Instant::ofEpochMilli),
    feedLength = lengthMillis?.milliseconds,
    description = description,
    audioUrl = audioUrl,
    audioSizeBytes = audioSize,
    progress = if (positionMillis != null && played != null && lastPlayedAt != null) {
        ProgressRecord(
            position = positionMillis.milliseconds,
            isPlayed = played,
            playerLength = playerLengthMillis?.milliseconds,
            lastPlayedAt = Instant.ofEpochMilli(lastPlayedAt),
        )
    } else {
        null
    },
    leftFeedAt = leftFeedAt?.let(Instant::ofEpochMilli),
)

internal fun ProgressRecord.toEntity(episodeId: EpisodeId): ProgressEntity = ProgressEntity(
    episodeId = episodeId.value,
    positionMillis = position.inWholeMilliseconds,
    played = isPlayed,
    playerLengthMillis = playerLength?.inWholeMilliseconds,
    lastPlayedAt = lastPlayedAt.toEpochMilli(),
)

internal fun ShowWrite.toEntity(joinedAt: Instant): ShowEntity = ShowEntity(
    directoryId = directoryId.value,
    feedUrl = feedUrl,
    title = title,
    titleSortKey = titleSortKey,
    ownerName = ownerName,
    ownerKey = ownerKey.value,
    ownerSortKey = ownerSortKey,
    joinedAt = joinedAt.toEpochMilli(),
    kind = kind?.name,
)

/** The show's id is set by [PodcastsDao.addEpisode], once the show is known. */
internal fun EpisodeWrite.toEntity(listedAt: Instant): EpisodeEntity = EpisodeEntity(
    showId = 0,
    itemKey = itemKey,
    guid = guid,
    title = title,
    publishedAt = publishedAt?.toEpochMilli(),
    lengthMillis = length?.inWholeMilliseconds,
    description = description,
    audioUrl = audioUrl,
    audioSize = audioSizeBytes,
    audioType = audioType,
    imageUrl = imageUrl,
    listedAt = listedAt.toEpochMilli(),
)

internal fun FollowEntity.toSynced(): SyncedValue<Follow> =
    SyncedValue(Follow(feedUrl = feedUrl, followed = followed), SyncTimestamp(updatedAt))

internal fun SyncedValue<Follow>.toEntity(directoryId: String): FollowEntity =
    FollowEntity(directoryId, value.feedUrl, value.followed, updatedAt.epochMillis)

internal fun List<FollowEntity>.toSyncedFollows(): SyncedFollows = associate {
    DirectoryId(it.directoryId) to
        it.toSynced()
}
