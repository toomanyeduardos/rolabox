package com.eduardoflores.rolabox.device.playback.impl

import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.playback.api.RepeatMode
import com.eduardoflores.rolabox.device.playback.local.api.ShuffledBy
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The orders of ADR-028, rules 2 and 3, as indices into the list, over many seeds of the random source: each rule holds
 * for every order, whichever it is.
 */
class PlayOrdersTest {
    // A list as an artist's songs are shown, by title: three albums, with their songs mixed, and one with two discs.
    private val places = listOf(
        AlbumPlace(A, disc = 1, track = 2), // 0
        AlbumPlace(B, disc = null, track = 1), // 1
        AlbumPlace(A, disc = 2, track = 1), // 2
        AlbumPlace(C, disc = null, track = null), // 3
        AlbumPlace(A, disc = 1, track = 1), // 4
        AlbumPlace(B, disc = null, track = 2), // 5
        AlbumPlace(A, disc = 1, track = 3), // 6
    )

    // Album A's own order: disc 1 tracks 1, 2 and 3, then disc 2.
    private val albumA = listOf(4, 0, 6, 2)
    private val albumB = listOf(1, 5)
    private val albumC = listOf(3)

    @Test
    fun songs_playsTheSelectedRowFirst_andEveryOtherSongOnce() = forEverySeed { orders ->
        val order = orders.from(ShuffledBy.Songs, places, from = 3)

        assertEquals(3, order.first())
        assertEquals(places.indices.toSet(), order.toSet())
        assertEquals(places.size, order.size)
    }

    @Test
    fun songs_isntAlwaysTheSameOrder() {
        val orders = (0 until SEEDS).map { PlayOrders(Random(it)).from(ShuffledBy.Songs, places, from = 0) }

        assertTrue(orders.toSet().size > 1)
    }

    @Test
    fun albums_playsTheRowsAlbumFromTheRow_thenTheOtherAlbumsWhole_thenTheAlbumsSongsBeforeTheRow() =
        forEverySeed { orders ->
            val order = orders.from(ShuffledBy.Albums, places, from = 6)

            // From disc 1 track 3 to the end of album A.
            assertEquals(listOf(6, 2), order.take(2))
            // The other two albums whole, each in its own order, in either order.
            assertTrue(order.subList(2, 5) == albumB + albumC || order.subList(2, 5) == albumC + albumB)
            // Album A's songs before the row, last.
            assertEquals(listOf(4, 0), order.takeLast(2))
        }

    @Test
    fun albums_withOneAlbum_playsItsOrderFromTheRow_thenTheSongsBeforeIt() = forEverySeed { orders ->
        val oneAlbum = albumA.map(places::get).sortedWith(compareBy({ it.disc }, { it.track }))

        assertEquals(listOf(2, 3, 0, 1), orders.from(ShuffledBy.Albums, oneAlbum, from = 2))
    }

    @Test
    fun aNextRound_byAlbums_isEveryAlbumWhole_andNeverStartsWithTheSongThatJustPlayed() = forEverySeed { orders ->
        val round = orders.nextRound(ShuffledBy.Albums, places, notFirst = setOf(albumB.first()))

        assertNotEquals(albumB.first(), round.first())
        val albums = listOf(albumA, albumB, albumC)
        assertTrue(albums.permutations().any { it.flatten() == round })
    }

    @Test
    fun aNextRound_bySongs_neverStartsWithTheSongsItIsTold() = forEverySeed { orders ->
        val round = orders.nextRound(ShuffledBy.Songs, places, notFirst = setOf(5, 6))

        assertFalse(round.first() in setOf(5, 6))
        assertEquals(places.indices.toSet(), round.toSet())
    }

    @Test
    fun theQueue_givesThePlayerTheNextRoundOnTheLastSongOfARound_underRepeatAllOnly() = forEverySeed { orders ->
        val queue = PlayQueue(orders)
        queue.load(places, ShuffledBy.Songs, from = 0)
        val round = queue.order
        val last = round.last()

        assertEquals(round, queue.playerOrder(round.first(), RepeatMode.All))
        assertEquals(round, queue.playerOrder(last, RepeatMode.Off))
        val handover = queue.playerOrder(last, RepeatMode.All)!!
        // ⏮ still goes to the song before it in this round, and ⏭ wraps to a song that is neither.
        assertEquals(round.takeLast(2), handover.takeLast(2))
        assertFalse(handover.first() in round.takeLast(2))

        assertTrue(queue.startedNextRound(handover.first()))
        assertEquals(handover.first(), queue.order.first())
        assertEquals(places.indices.toSet(), queue.order.toSet())
    }

    @Test
    fun theQueue_notShuffled_givesThePlayerNoOrder() {
        val queue = PlayQueue(PlayOrders(Random(0)))
        queue.load(places, by = null, from = 3)

        assertEquals(places.indices.toList(), queue.order)
        assertNull(queue.playerOrder(3, RepeatMode.All))
        assertEquals(3, queue.positionOf(3))
    }

    @Test
    fun theQueue_keepsItsOrderWhenAnItemLeaves() {
        val queue = PlayQueue(PlayOrders(Random(0)))
        queue.load(places, ShuffledBy.Songs, from = 2)
        val before = queue.order

        queue.remove(4)

        assertEquals(before.filter { it != 4 }.map { if (it > 4) it - 1 else it }, queue.order)
    }

    @Test
    fun aSkip_followsThePlayOrder_andWrapsOnlyUnderRepeatAll() {
        val queue = PlayQueue(PlayOrders(Random(0)))
        queue.load(places, by = null, from = 0)

        assertEquals(6, queue.nextPlayable(5, RepeatMode.Off, backward = false) { false })
        assertNull(queue.nextPlayable(6, RepeatMode.Off, backward = false) { false })
        assertNull(queue.nextPlayable(6, RepeatMode.One, backward = false) { false })
        assertEquals(0, queue.nextPlayable(6, RepeatMode.All, backward = false) { false })
        assertEquals(1, queue.nextPlayable(6, RepeatMode.All, backward = false) { it == 0 || it == 6 })
        assertEquals(6, queue.nextPlayable(0, RepeatMode.All, backward = true) { it == 0 })
        assertNull(queue.nextPlayable(3, RepeatMode.All, backward = false) { true })
    }

    @Test
    fun aSkipPastTheEndOfAShuffledRound_underRepeatAll_startsTheNextRound() = forEverySeed { orders ->
        val queue = PlayQueue(orders)
        queue.load(places, ShuffledBy.Songs, from = 0)
        val round = queue.order

        val next = queue.nextPlayable(round.last(), RepeatMode.All, backward = false) { it == round.last() }

        assertEquals(queue.order.first(), next)
        assertFalse(next in round.takeLast(2))
    }

    private fun forEverySeed(check: (PlayOrders) -> Unit) = repeat(SEEDS) { check(PlayOrders(Random(it))) }

    private companion object {
        const val SEEDS = 50
        val A = AlbumId(1)
        val B = AlbumId(2)
        val C = AlbumId(3)
    }
}

private fun <T> List<T>.permutations(): List<List<T>> =
    if (size <= 1) listOf(this) else flatMap { first -> (this - first).permutations().map { listOf(first) + it } }
