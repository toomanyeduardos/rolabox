package com.eduardoflores.rolabox.device.playback.impl

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * An episode that ran out of bytes waits for the network, still playing, and goes on by itself when the network allows
 * it (ADR-031, rule 15). The player stopped with an error and keeps the episode, its position and `playWhenReady`, so
 * it reads as playing; going on is preparing it again.
 *
 * [waitsFor] is what an episode would wait for now, from the network and Use Mobile Data. Each time it changes while
 * one waits, the wait takes its reason, or ends with the player prepared again when it is `null`. A wait that began
 * while the network seemed fine, as with a server that doesn't answer, goes on when the network changes, or with ⏯.
 * [onPlayer] runs on the player's thread, where every call here is made.
 */
internal class NetworkWait(
    private val waitsFor: StateFlow<PlaybackError.Waiting?>,
    scope: CoroutineScope,
    private val onPlayer: suspend ((Player) -> Unit) -> Unit,
) {
    private val waiting = MutableStateFlow<PlaybackError.Waiting?>(null)

    /** What the loaded episode waits for, or `null`. */
    val waitingFor: StateFlow<PlaybackError.Waiting?> = waiting.asStateFlow()

    init {
        scope.launch {
            waitsFor.collect { now ->
                onPlayer { player ->
                    if (waiting.value != null) {
                        if (now == null) goOn(player) else waiting.value = now
                    }
                }
            }
        }
    }

    /**
     * Starts a wait for [error] of an [episode], and says whether it did: only a connection that failed or timed out is
     * one. Anything else fails the item as it fails a song.
     */
    fun onError(error: PlaybackException, episode: Boolean): Boolean {
        val lostConnection = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        if (episode && lostConnection) waiting.value = waitsFor.value ?: PlaybackError.NoConnection
        return episode && lostConnection
    }

    /** Ends the wait: the episode plays again, was paused, or something else was loaded. */
    fun end() {
        waiting.value = null
    }

    private fun goOn(player: Player) {
        if (player.playbackState == Player.STATE_IDLE && player.mediaItemCount > 0) player.prepare()
    }
}
