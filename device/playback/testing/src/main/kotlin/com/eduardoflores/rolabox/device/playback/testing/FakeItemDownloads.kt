package com.eduardoflores.rolabox.device.playback.testing

import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.ItemDownloads
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Downloads that tests set: [downloads] is what [observe] emits, and none to begin with. */
@Singleton
class FakeItemDownloads @Inject constructor() : ItemDownloads {
    private val state = MutableStateFlow<Map<PlaybackItemId, ItemDownload>>(emptyMap())

    var downloads: Map<PlaybackItemId, ItemDownload>
        get() = state.value
        set(value) {
            state.value = value
        }

    override fun observe(): Flow<Map<PlaybackItemId, ItemDownload>> = state
}
