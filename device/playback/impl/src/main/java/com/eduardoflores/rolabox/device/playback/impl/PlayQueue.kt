package com.eduardoflores.rolabox.device.playback.impl

import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import com.eduardoflores.rolabox.device.playback.local.api.ShuffledBy

/** The fewest items a round needs for the player's order to carry the next round while it ends (ADR-028). */
private const val ROUND_HANDOVER_SIZE = 3

/**
 * The order the queue plays in (ADR-028). An index is where an item is in the list, which is the player's playlist and
 * never moves; a position is where it is in the play order. Not shuffled, the play order is the list's. Shuffled, it
 * is the order of the round that plays, and under Repeat All the next round is made while the last item of this one
 * plays.
 */
// What the queue knows of its order, and the two ways it moves through it: the player's, and a skip's.
@Suppress("TooManyFunctions")
internal class PlayQueue(private val orders: PlayOrders) {
    private var places: List<AlbumPlace> = emptyList()
    private var positions = IntArray(0)
    private var nextRound: List<Int>? = null

    /** What shuffled the order, or `null` when it is the list's. */
    var shuffledBy: ShuffledBy? = null
        private set

    /** The indices of the list, in the order they play. */
    var order: List<Int> = emptyList()
        private set

    /** Where the item at [index] is in its album. */
    fun placeOf(index: Int): AlbumPlace = places[index]

    /** Where the item at [index] is in [order], or -1 for an index the list doesn't have. */
    fun positionOf(index: Int): Int = positions.getOrElse(index) { -1 }

    /** A list of [places] that starts at [from], in an order shuffled [by] a value, or in its own when it is `null`. */
    fun load(places: List<AlbumPlace>, by: ShuffledBy?, from: Int) {
        this.places = places
        reorder(by, from)
    }

    /** A list of [places] in a [round] that was stored [by] a value, and checked against the list. */
    fun restore(places: List<AlbumPlace>, by: ShuffledBy, round: List<Int>) {
        this.places = places
        shuffledBy = by
        setOrder(round)
    }

    /**
     * A new order [by] a value, which the item at [from] starts, as if it had just been selected, or the list's order
     * again when it is `null` (ADR-028, "Changing a setting while a queue plays").
     */
    fun reorder(by: ShuffledBy?, from: Int) {
        shuffledBy = by
        setOrder(if (by == null || places.isEmpty()) places.indices.toList() else orders.from(by, places, from))
    }

    fun clear() {
        places = emptyList()
        shuffledBy = null
        setOrder(emptyList())
    }

    /** The item at [index] left the list, as it leaves the player's playlist: the indices after it move down by one. */
    fun remove(index: Int) {
        places = places.filterIndexed { itemIndex, _ -> itemIndex != index }
        setOrder(order.filter { it != index }.map { if (it > index) it - 1 else it })
    }

    /**
     * The order to give the player while the item at [current] plays under [repeat], or `null` when the queue isn't
     * shuffled and the player's shuffle mode is off (ADR-028, rule 6). It is the round's, but on the last item of a
     * round under Repeat All: then the next round is made, and the player's order wraps to its first item while ⏮ still
     * goes to the item before [current]. The next round never starts with either of those two, so one order can hold
     * both, and once its first item starts, [startedNextRound] makes it this round.
     */
    fun playerOrder(current: Int, repeat: RepeatMode): List<Int>? {
        val by = shuffledBy ?: return null
        val endsRound = repeat == RepeatMode.All && order.size >= ROUND_HANDOVER_SIZE && order.last() == current
        if (!endsRound) nextRound = null
        return if (endsRound) handover(by) else order
    }

    /** This round's last two items, after the next round: ⏮ goes back in this round, and ⏭ wraps to the next. */
    private fun handover(by: ShuffledBy): List<Int> {
        val ending = order.takeLast(2)
        val next = nextRound ?: orders.nextRound(by, places, ending.toSet()).also { nextRound = it }
        return listOf(next.first()) + next.filter { it != next.first() && it !in ending } + ending
    }

    /** Whether the item at [current] starts the next round, which the queue wrapped to: that round is now this one. */
    fun startedNextRound(current: Int): Boolean {
        val next = nextRound?.takeIf { it.first() == current } ?: return false
        setOrder(next)
        return true
    }

    /**
     * The nearest item in the play order that isn't [failed], from the item at [from]: backward when [backward], and
     * otherwise forward (ADR-023). Without Repeat All, the other way is tried when backward finds nothing, and the
     * search stops at the ends. Under Repeat All it wraps, and forward past the end of a shuffled round it goes into
     * the next round, which is then this one. `null` when every item failed.
     */
    fun nextPlayable(from: Int, repeat: RepeatMode, backward: Boolean, failed: (Int) -> Boolean): Int? {
        val at = positionOf(from)
        val after = order.drop(at + 1).firstOrNull { !failed(it) }
        val before = order.take(at).asReversed().firstOrNull { !failed(it) }
        val wraps = repeat == RepeatMode.All
        return when {
            backward && wraps -> before ?: order.drop(at + 1).asReversed().firstOrNull { !failed(it) }
            backward -> before ?: after
            wraps -> after ?: wrapForward(at, failed)
            else -> after
        }
    }

    /**
     * Whether anything plays after the item at [from], in the play order and under [repeat], that [gone] doesn't hold.
     */
    fun hasNext(from: Int, repeat: RepeatMode, gone: (Int) -> Boolean): Boolean {
        val after = order.drop(positionOf(from) + 1)
        return after.any { !gone(it) } || (repeat == RepeatMode.All && order.any { !gone(it) })
    }

    private fun wrapForward(at: Int, failed: (Int) -> Boolean): Int? {
        val by = shuffledBy
        if (by == null || order.size < ROUND_HANDOVER_SIZE) return order.take(at).firstOrNull { !failed(it) }
        setOrder(nextRound ?: orders.nextRound(by, places, order.takeLast(2).toSet()))
        return order.firstOrNull { !failed(it) }
    }

    private fun setOrder(order: List<Int>) {
        this.order = order
        positions =
            IntArray(order.size).also { positions -> order.forEachIndexed { at, index -> positions[index] = at } }
        nextRound = null
    }
}
