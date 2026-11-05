package com.eduardoflores.rolabox.device.playback.impl

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaController
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import java.util.concurrent.atomic.AtomicInteger

/**
 * Gives the player the play order of ADR-028, as the media IDs of its items in the order they play. Only an `ExoPlayer`
 * takes a [ShuffleOrder], and a `MediaController` has no way to set one, so Rolabox's controller sends it to the
 * session in the extras of the playlist's metadata, which only that controller may set (ADR-028, rule 8). It is a
 * player command, so the session runs it in turn with the seeks and the items sent before it: a custom command would
 * run first, and a ⏮ or ⏭ already sent would then follow the new order. The tests' player is the `ExoPlayer` itself,
 * with no session between, and takes it as the session would.
 */
internal fun Player.setPlayOrder(mediaIds: List<String>) {
    when (this) {
        is MediaController -> setPlaylistMetadata(
            MediaMetadata.Builder()
                .setExtras(Bundle().apply { putStringArrayList(PLAY_ORDER_KEY, ArrayList(mediaIds)) })
                // The player ignores metadata equal to what it has, and equality leaves the extras out, so each order
                // is numbered: one given again, after its items, still arrives. A playlist has no track number to show.
                .setTrackNumber(sent.incrementAndGet())
                .build(),
        )

        is ExoPlayer -> applyPlayOrder(mediaIds)
    }
}

/** How an item of the queue is named to the player, so an order sent to the session can name it. */
internal val PlaybackItemId.mediaId: String
    get() = when (this) {
        is PlaybackItemId.Song -> "song:${id.value}"
    }

private const val PLAY_ORDER_KEY = "com.eduardoflores.rolabox.PLAY_ORDER"
private val sent = AtomicInteger()

/** The play order that [setPlayOrder] put in the playlist's metadata, or `null` when it has none. */
internal fun MediaMetadata.playOrder(): List<String>? = extras?.getStringArrayList(PLAY_ORDER_KEY)

/**
 * The session's side of [setPlayOrder]: it gives the session's player each order that arrives in the playlist's
 * metadata. Setting the items gives the player the list's order again, and a playback resumption sets them with no
 * order after them, so the latest order is kept, and given again each time the playlist changes, while it names each of
 * its items.
 */
internal class PlayOrderKeeper(private val player: ExoPlayer) : Player.Listener {
    private var latest: List<String>? = null

    override fun onPlaylistMetadataChanged(mediaMetadata: MediaMetadata) {
        val mediaIds = mediaMetadata.playOrder() ?: return
        latest = mediaIds
        player.applyPlayOrder(mediaIds)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) latest?.let(player::applyPlayOrder)
    }
}

/**
 * Gives the player the order that [mediaIds] name, when they name each item of its playlist once, unless it already has
 * it. Returns whether they do.
 */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.applyPlayOrder(mediaIds: List<String>): Boolean {
    val indexOf = (0 until mediaItemCount).associateBy { getMediaItemAt(it).mediaId }
    val order = mediaIds.mapNotNull { indexOf[it] }
    val namesEachItemOnce =
        order.size == mediaIds.size && order.size == mediaItemCount && order.toSet().size == order.size
    if (namesEachItemOnce && shuffledOrder() != order) setShuffleOrder(FixedShuffleOrder(order.toIntArray()))
    return namesEachItemOnce
}

/** The playlist's indices in the order the player's shuffle order has them. */
private fun Player.shuffledOrder(): List<Int> {
    val timeline = currentTimeline
    val first = timeline.getFirstWindowIndex(true).takeIf { it != C.INDEX_UNSET }
    return generateSequence(first) { index ->
        timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true).takeIf { it != C.INDEX_UNSET }
    }.toList()
}

/**
 * A [ShuffleOrder] that is the order it is given (ADR-028, rule 6). Media3 asks it for the next and the previous item,
 * and keeps it as the playlist changes: an item removed leaves it, and the others keep their order. Items added go
 * right after the item before them in the playlist, so an item that Media3 replaces, adding the new one after it and
 * then removing it, keeps its place.
 */
@UnstableApi
internal class FixedShuffleOrder(private val order: IntArray) : ShuffleOrder {
    private val positions = IntArray(order.size).also { positions ->
        order.forEachIndexed { at, index -> positions[index] = at }
    }

    override fun getLength(): Int = order.size

    override fun getNextIndex(index: Int): Int = order.getOrElse(positions[index] + 1) { C.INDEX_UNSET }

    override fun getPreviousIndex(index: Int): Int = order.getOrElse(positions[index] - 1) { C.INDEX_UNSET }

    override fun getLastIndex(): Int = order.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = order.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val shifted = order.map { if (it >= insertionIndex) it + insertionCount else it }
        val added = (insertionIndex until insertionIndex + insertionCount).toList()
        val at = if (insertionIndex == 0) 0 else positions[insertionIndex - 1] + 1
        return FixedShuffleOrder((shifted.take(at) + added + shifted.drop(at)).toIntArray())
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        return FixedShuffleOrder(
            order.filter { it !in indexFrom until indexToExclusive }
                .map { if (it >= indexToExclusive) it - count else it }
                .toIntArray(),
        )
    }

    override fun cloneAndClear(): ShuffleOrder = FixedShuffleOrder(IntArray(0))
}
