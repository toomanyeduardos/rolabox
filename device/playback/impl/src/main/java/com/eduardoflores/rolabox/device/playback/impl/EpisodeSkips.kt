package com.eduardoflores.rolabox.device.playback.impl

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.ListenableFuture
import kotlin.time.Duration.Companion.seconds

/** How far ⏮ skips back during an episode (ADR-031, rule 11): the player's seek-back increment. */
internal val SkipBack = 15.seconds

/** How far ⏭ skips forward during an episode (ADR-031, rule 11): the player's seek-forward increment. */
internal val SkipForward = 30.seconds

/**
 * The session's player: the service's `ExoPlayer`, with previous and next turned into skip back and skip forward while
 * an episode is loaded, so the notification, the lock screen and a headset skip by seconds as the wheel does (ADR-031,
 * rule 11). The notification shows ⏮ and ⏭ for an episode too, which is a queue of one, so they are offered while it is
 * loaded. A song is left as the player plays it.
 */
@OptIn(UnstableApi::class)
internal class EpisodeSkips(player: Player) : ForwardingSimpleBasePlayer(player) {
    override fun getState(): State {
        val state = super.getState()
        if (!isEpisodeLoaded()) return state
        val commands = state.availableCommands.buildUpon()
        if (state.availableCommands.contains(Player.COMMAND_SEEK_BACK)) {
            commands.addAll(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }
        if (state.availableCommands.contains(Player.COMMAND_SEEK_FORWARD)) {
            commands.addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        return state.buildUpon().setAvailableCommands(commands.build()).build()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        val skip = when (seekCommand) {
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Player.COMMAND_SEEK_BACK
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> Player.COMMAND_SEEK_FORWARD
            else -> null
        }
        return if (skip != null && isEpisodeLoaded()) {
            super.handleSeek(mediaItemIndex, positionMs, skip)
        } else {
            super.handleSeek(mediaItemIndex, positionMs, seekCommand)
        }
    }

    private fun isEpisodeLoaded(): Boolean = player.currentMediaItem?.mediaId?.isEpisodeMediaId() == true
}
