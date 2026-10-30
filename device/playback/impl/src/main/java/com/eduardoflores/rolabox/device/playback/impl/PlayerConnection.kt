package com.eduardoflores.rolabox.device.playback.impl

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.Main
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How [MediaPlayback] reaches the player. */
internal interface PlayerConnection {
    /** Where the player is called, since Media3 is called on the thread its player was made on. */
    val dispatcher: CoroutineDispatcher

    /** The player, once it's connected, or why it couldn't be. Called on [dispatcher]. */
    suspend fun player(): Either<PlaybackError.PlayerUnavailable, Player>
}

/**
 * A `MediaController` connected to [PlaybackService]'s session (ADR-023, rule 3). Connecting starts the service,
 * and a controller that lost its connection is made again. The session refusing the connection is the one failure
 * Media3 reports, as a `SecurityException` (ADR-007, rule 4), and the next call tries again.
 */
internal class SessionConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(Main) override val dispatcher: CoroutineDispatcher,
) : PlayerConnection {
    private val mutex = Mutex()
    private var controller: MediaController? = null

    override suspend fun player(): Either<PlaybackError.PlayerUnavailable, Player> = mutex.withLock {
        controller?.takeIf { it.isConnected }?.right() ?: connect()
    }

    private suspend fun connect(): Either<PlaybackError.PlayerUnavailable, Player> {
        controller?.release()
        controller = null
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return try {
            MediaController.Builder(context, token).buildAsync().await().also { controller = it }.right()
        } catch (_: SecurityException) {
            PlaybackError.PlayerUnavailable.left()
        }
    }
}
