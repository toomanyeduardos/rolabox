package com.eduardoflores.rolabox.device.playback.impl

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.eduardoflores.rolabox.common.designsystem.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint

/**
 * Owns the player and its session (ADR-023): nothing else holds the [ExoPlayer]. The device reaches it through
 * a `MediaController`, as the system's controls and the media buttons do, so every way in plays the same
 * player. Media3 makes it a foreground service while the player plays, with the media notification, and takes the
 * notification away when nothing is loaded.
 */
@AndroidEntryPoint
internal class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ds_ic_notification)
            },
        )
        session = MediaSession.Builder(this, buildPlayer(this))
            .setCallback(ItemsWithTheirUris)
            .apply { theApp()?.let(::setSessionActivity) }
            .build()
    }

    /** What the notification opens: the app's launcher activity, which only `:app` knows (ADR-023, rule 2). */
    private fun theApp(): PendingIntent? = packageManager.getLaunchIntentForPackage(packageName)?.let {
        PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    // Dismissed while it plays, it plays on. Media3 would stop it whenever the player isn't making sound at that
    // moment, so it is only asked when the player isn't about to: a call or buffering is still playing (ADR-023).
    //
    // Dismissed with nothing playing, the service stops and its notification goes with it (ADR-023). Media3 only stops
    // it, and the system kills the process right after, before onDestroy, so what Media3 posts while it stops stays
    // on screen. So the session is removed first, which keeps Media3 from posting, and the notification is cancelled.
    @OptIn(UnstableApi::class)
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = session
        if (current?.player?.playsOn() == true) return
        current?.let { idle ->
            removeSession(idle)
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java)
                .cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        super.onTaskRemoved(rootIntent)
        // The process may outlive this, and the app be opened again. A session the service no longer has would play
        // with no foreground and no notification, so it goes: its controller disconnects, and connects to a new
        // service.
        release()
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        session?.run {
            player.release()
            release()
        }
        session = null
    }
}

/**
 * The player of the service, with the threshold of ⏮ (ADR-023, rule 7) and the phone's media volume as the
 * volume (rule 6). It handles audio focus as music, and pauses when the headphones go (ADR-023, "Audio focus, noise
 * and buttons"). [configure] is for the tests, which set Media3's fake clock.
 */
// The threshold and the device volume are what ADR-023 decides, and Media3 marks them unstable: an upgrade may
// change them.
@OptIn(UnstableApi::class)
internal fun buildPlayer(context: Context, configure: ExoPlayer.Builder.() -> Unit = {}): ExoPlayer =
    ExoPlayer.Builder(context)
        .setMaxSeekToPreviousPositionMs(RestartThreshold.inWholeMilliseconds)
        .setDeviceVolumeControlEnabled(true)
        .setAudioAttributes(MusicAttributes, true)
        .setHandleAudioBecomingNoisy(true)
        .apply(configure)
        .build()

/**
 * Whether the player goes on without the app: an item is loaded and asked to play, even if a call has it silent for
 * now or it is buffering. Paused, ended or with nothing loaded, it doesn't.
 */
internal fun Player.playsOn(): Boolean =
    playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

private val MusicAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
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
