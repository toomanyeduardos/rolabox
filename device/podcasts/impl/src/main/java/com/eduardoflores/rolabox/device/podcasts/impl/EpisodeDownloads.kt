package com.eduardoflores.rolabox.device.podcasts.impl

import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The download of each episode, which is playback's (ADR-031, Downloads). An episode missing from the map isn't
 * downloaded.
 */
internal interface EpisodeDownloads {
    fun observe(): Flow<Map<EpisodeId, DownloadState>>
}

/** Until podcasts/07 reads playback's `ItemDownloads`, no episode is downloaded. */
internal class NoEpisodeDownloads @Inject constructor() : EpisodeDownloads {
    override fun observe(): Flow<Map<EpisodeId, DownloadState>> = flowOf(emptyMap())
}
