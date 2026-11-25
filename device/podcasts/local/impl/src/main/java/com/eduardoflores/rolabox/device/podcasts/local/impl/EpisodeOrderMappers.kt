package com.eduardoflores.rolabox.device.podcasts.local.impl

import com.eduardoflores.rolabox.common.sync.api.SyncTimestamp
import com.eduardoflores.rolabox.common.sync.api.SyncedValue
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrderChoice
import com.eduardoflores.rolabox.device.podcasts.api.SyncedEpisodeOrders

// The user's choices of episode order as stored, and as sync speaks them (ADR-043).

/** The choice a stored name is, or `null` for none, or a name this version doesn't know. */
internal fun episodeOrderChoiceOf(name: String?): EpisodeOrderChoice? =
    EpisodeOrderChoice.entries.firstOrNull { it.name == name }

// A row with a value this version doesn't know isn't synced: a newer version wrote it, and keeps it.
internal fun List<EpisodeOrderEntity>.toSyncedEpisodeOrders(): SyncedEpisodeOrders = mapNotNull { entity ->
    episodeOrderChoiceOf(entity.value)?.let { DirectoryId(entity.directoryId) to entity.toSynced(it) }
}.toMap()

internal fun EpisodeOrderEntity.toSynced(choice: EpisodeOrderChoice): SyncedValue<EpisodeOrderChoice> =
    SyncedValue(choice, SyncTimestamp(updatedAt))

internal fun SyncedValue<EpisodeOrderChoice>.toEntity(directoryId: String): EpisodeOrderEntity =
    EpisodeOrderEntity(directoryId, value.name, updatedAt.epochMillis)
