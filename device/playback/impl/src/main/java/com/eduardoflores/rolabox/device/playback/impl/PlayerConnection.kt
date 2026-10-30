package com.eduardoflores.rolabox.device.playback.impl

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.eduardoflores.rolabox.common.util.Dispatcher
import com.eduardoflores.rolabox.common.util.RolaboxDispatchers.Main
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

    /** The player, once it's connected. Called on [dispatcher]. */
    suspend fun player(): Player
}

/**
 * A `MediaController` connected to [PlaybackService]'s session (ADR-023, rule 3). Connecting starts the service,
 * and a controller that lost its connection is made again.
 */
internal class SessionConnection @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(Main) override val dispatcher: CoroutineDispatcher,
) : PlayerConnection {
    private val mutex = Mutex()
    private var controller: MediaController? = null

    override suspend fun player(): Player = mutex.withLock {
        controller?.takeIf { it.isConnected } ?: run {
            controller?.release()
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().await().also { controller = it }
        }
    }
}
