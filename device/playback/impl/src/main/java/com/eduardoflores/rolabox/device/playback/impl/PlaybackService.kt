package com.eduardoflores.rolabox.device.playback.impl

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint

/**
 * Owns the player and its session (ADR-023): nothing else holds the [ExoPlayer]. The device reaches it through
 * a `MediaController`, as the system's controls and the media buttons do, so every way in plays the same
 * player. The notification, the foreground and the service's lifetime are playback/03's.
 */
@AndroidEntryPoint
internal class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, buildPlayer(this))
            .setCallback(ItemsWithTheirUris)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}

/**
 * The player of the service, with the threshold of ⏮ (ADR-023, rule 7) and the phone's media volume as the
 * volume (rule 6). [configure] is for the tests, which set Media3's fake clock.
 */
// Both settings are what ADR-023 decides, and Media3 marks them unstable: an upgrade may change them.
@OptIn(UnstableApi::class)
internal fun buildPlayer(context: Context, configure: ExoPlayer.Builder.() -> Unit = {}): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMaxSeekToPreviousPositionMs(RestartThreshold.inWholeMilliseconds)
        .setDeviceVolumeControlEnabled(true)
        .apply(configure)
        .build()

/**
 * A controller sends its items to the session without where their audio is, unless it's in the same process.
 * The URI goes along in the request metadata, as Media3 expects, and is put back here.
 */
private object ItemsWithTheirUris : MediaSession.Callback {
    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> = Futures.immediateFuture(
        mediaItems.map { item ->
            val uri = item.requestMetadata.mediaUri
            if (item.localConfiguration == null && uri != null) item.buildUpon().setUri(uri).build() else item
        },
    )
}
