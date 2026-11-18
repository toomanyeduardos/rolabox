package com.eduardoflores.rolabox.device.playback.api

import kotlinx.coroutines.flow.Flow

/** Whether an episode's bytes may be fetched now, which playback decides (ADR-031, Mobile data). */
interface EpisodeNetwork {
    /**
     * Whether episodes need Wi-Fi now: Use Mobile Data is Off and the phone's network is metered. With no network at
     * all they don't: they need a connection. The Episode screen shows it in place of Play for an episode with no
     * download.
     */
    fun observeNeedsWifi(): Flow<Boolean>
}
