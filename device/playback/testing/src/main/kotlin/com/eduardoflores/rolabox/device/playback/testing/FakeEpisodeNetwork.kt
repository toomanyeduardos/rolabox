package com.eduardoflores.rolabox.device.playback.testing

import com.eduardoflores.rolabox.device.playback.api.EpisodeNetwork
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Whether episodes need Wi-Fi, as tests set it: not to begin with. */
@Singleton
class FakeEpisodeNetwork @Inject constructor() : EpisodeNetwork {
    val needsWifi = MutableStateFlow(false)

    override fun observeNeedsWifi(): Flow<Boolean> = needsWifi
}
