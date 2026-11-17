package com.eduardoflores.rolabox.device.podcasts.impl

import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Downloads that tests set, as playback's would report them once podcasts/07 reads them. */
internal class FakeEpisodeDownloads : EpisodeDownloads {
    val states = MutableStateFlow<Map<EpisodeId, DownloadState>>(emptyMap())

    override fun observe(): Flow<Map<EpisodeId, DownloadState>> = states
}
