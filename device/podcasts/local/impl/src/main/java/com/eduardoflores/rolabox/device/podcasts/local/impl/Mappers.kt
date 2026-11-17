package com.eduardoflores.rolabox.device.podcasts.local.impl

import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.OwnerId
import com.eduardoflores.rolabox.device.podcasts.api.Show
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.EpisodeWrite
import com.eduardoflores.rolabox.device.podcasts.local.api.ProgressRecord
import com.eduardoflores.rolabox.device.podcasts.local.api.ShowWrite
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

internal fun ShowRow.toShow(): Show = Show(
    id = ShowId(id),
    directoryId = DirectoryId(directoryId),
    title = title,
    ownerId = OwnerId(ownerKey),
    ownerName = ownerName,
    isFollowed = followed,
)

internal fun EpisodeRow.toRecord(): EpisodeRecord = EpisodeRecord(
    id = EpisodeId(id),
    showId = ShowId(showId),
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
    listedAt = listedAt.toEpochMilli(),
)
