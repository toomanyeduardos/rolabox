package com.eduardoflores.rolabox.device.playback.impl

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture

/** What [PlaybackService] asks of [MediaPlayback], which keeps the queue's list and its items. */
internal interface SessionResumption {
    /** The queue stored for a play with nothing loaded, failing when nothing was stored. */
    @OptIn(UnstableApi::class)
    fun resumption(): ListenableFuture<MediaSession.MediaItemsWithStartPosition>

    /** Stores where [player] is, as the service stops and its controller may already be gone. */
    fun storeFrom(player: Player)
}
