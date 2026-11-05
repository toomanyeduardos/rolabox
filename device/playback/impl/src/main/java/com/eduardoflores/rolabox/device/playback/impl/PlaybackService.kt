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
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.eduardoflores.rolabox.common.designsystem.R
import com.eduardoflores.rolabox.device.library.api.ArtworkRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/**
 * Owns the player and its session (ADR-023): nothing else holds the [ExoPlayer]. The device reaches it through
 * a `MediaController`, as the system's controls and the media buttons do, so every way in plays the same
 * player. Media3 makes it a foreground service while the player plays, with the media notification, and takes the
 * notification away when nothing is loaded. It stores where it is when it stops, and what a media button resumes after
 * the app was killed is what was stored (ADR-023).
 */
@AndroidEntryPoint
internal class PlaybackService : MediaSessionService() {
    // Absent in tests that replace PlaybackModule, whose session only plays what it is given.
    @Inject
    lateinit var resumption: Optional<SessionResumption>

    // Absent in this module's instrumented tests, whose graph has no library: the session then loads what Media3 does.
    @Inject
    lateinit var artwork: Optional<ArtworkRepository>

    private var session: MediaSession? = null

    // Where the artwork loads, for as long as the service lives.
    private val scope = MainScope()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ds_ic_notification)
            },
        )
        val player = buildPlayer(this)
        // The play order that Rolabox's controller sends, which only the ExoPlayer can take (ADR-028, rule 6).
        player.addListener(PlayOrderKeeper(player))
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback(resumption.orElse(null), packageName))
            .apply { artwork.ifPresent { setBitmapLoader(artworkLoader(it)) } }
            .apply { theApp()?.let(::setSessionActivity) }
            .build()
    }

    /** The artwork of the notification and the lock screen, as Now Playing shows it (ADR-026, rule 6). */
    @OptIn(UnstableApi::class)
    private fun artworkLoader(artwork: ArtworkRepository) =
        ArtworkBitmapLoader(artwork, DataSourceBitmapLoader(this), scope)

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
        scope.cancel()
        super.onDestroy()
    }

    private fun release() {
        session?.run {
            resumption.ifPresent { it.storeFrom(player) }
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

/** The connection hint that marks Rolabox's own controller, which [SessionConnection] sends (ADR-028, rule 8). */
internal const val OWN_CONTROLLER = "com.eduardoflores.rolabox.OWN_CONTROLLER"

private val MusicAttributes = AudioAttributes.Builder()
    .setUsage(C.USAGE_MEDIA)
    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
    .build()

/**
 * A controller sends its items to the session without where their audio is, unless it's in the same process.
 * The URI goes along in the request metadata, as Media3 expects, and is put back here. A play with nothing loaded, from
 * a media button or the system's controls, gets what [resumption] restores.
 *
 * Shuffle and repeat are the device's settings, which change only in its Settings (ADR-028, rule 8): only Rolabox's
 * own controller, from [packageName] and with [OWN_CONTROLLER] in its hints, may set them, and only it may set the
 * playlist's metadata, which carries the play order (setPlayOrder). Every other controller, the notification's and the
 * system's among them, is offered none of the three, so the system's controls show no button for them.
 */
internal class SessionCallback(private val resumption: SessionResumption?, private val packageName: String) :
    MediaSession.Callback {
    @OptIn(UnstableApi::class)
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val isOwn = controller.packageName == packageName && controller.connectionHints.getBoolean(OWN_CONTROLLER)
        val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        if (!isOwn) {
            result.setAvailablePlayerCommands(
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .removeAll(
                        Player.COMMAND_SET_SHUFFLE_MODE,
                        Player.COMMAND_SET_REPEAT_MODE,
                        Player.COMMAND_SET_PLAYLIST_METADATA,
                    )
                    .build(),
            )
        }
        return result.build()
    }

    @OptIn(UnstableApi::class)
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        resumption?.resumption() ?: super.onPlaybackResumption(mediaSession, controller, isForPlayback)

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
