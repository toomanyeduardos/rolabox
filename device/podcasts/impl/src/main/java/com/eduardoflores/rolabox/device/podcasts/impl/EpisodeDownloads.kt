package com.eduardoflores.rolabox.device.podcasts.impl

import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.ItemDownloads
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * The download of each episode, which is playback's (ADR-031, Downloads). An episode missing from the map isn't
 * downloaded.
 */
internal interface EpisodeDownloads {
    fun observe(): Flow<Map<EpisodeId, DownloadState>>
}

/**
 * Playback's downloads, as the episodes' own state: waiting and running are [DownloadState.Downloading], with what they
 * have so far, done is [DownloadState.Downloaded], and a download that failed is none, so its row shows the arrow again
 * and playing it adds it again (ADR-031, Downloads).
 */
internal class PlaybackEpisodeDownloads @Inject constructor(private val downloads: ItemDownloads) : EpisodeDownloads {
    override fun observe(): Flow<Map<EpisodeId, DownloadState>> = downloads.observe()
        .map { all ->
            buildMap {
                all.forEach { (item, download) ->
                    val episode = (item as? PlaybackItemId.Episode)?.id ?: return@forEach
                    download.toState()?.let { put(episode, it) }
                }
            }
        }
        .distinctUntilChanged()
}

private fun ItemDownload.toState(): DownloadState? = when (this) {
    is ItemDownload.Waiting -> DownloadState.Downloading(fraction)
    is ItemDownload.Downloading -> DownloadState.Downloading(fraction)
    ItemDownload.Done -> DownloadState.Downloaded
    ItemDownload.Failed -> null
}
