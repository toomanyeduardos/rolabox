package com.eduardoflores.rolabox.device.playback.impl

import com.eduardoflores.rolabox.device.library.api.AlbumId
import com.eduardoflores.rolabox.device.playback.local.api.ShuffledBy
import javax.inject.Inject
import kotlin.random.Random

/** Where an item is in its album, which the Albums order keeps (ADR-028): an album's list is by disc, then track. */
internal data class AlbumPlace(val album: AlbumId, val disc: Int?, val track: Int?)

/**
 * The shuffled orders of ADR-028, as indices into the list. Every order is uniform, drawn from [random], which the
 * tests fix.
 */
internal class PlayOrders(private val random: Random) {
    @Inject
    constructor() : this(Random.Default)

    /** Where Shuffle Songs starts: a random item of a list of [size]. */
    fun randomIndex(size: Int): Int = random.nextInt(size)

    /** [indices] in a random order, for the items a stored order doesn't have yet. */
    fun shuffled(indices: List<Int>): List<Int> = indices.shuffled(random)

    /** The order of a list of [places] shuffled [by] a value, as if the item at [from] had just been selected. */
    fun from(by: ShuffledBy, places: List<AlbumPlace>, from: Int): List<Int> = when (by) {
        ShuffledBy.Songs, ShuffledBy.ShuffleSongs -> bySongs(places.size, from)
        ShuffledBy.Albums -> byAlbums(places, from)
    }

    /**
     * A new round under Repeat All (ADR-028): a whole new order [by] the same value, whose first item isn't one of
     * [notFirst]. A round never starts with the item that just played. When nothing can start it, it starts where it
     * can.
     */
    fun nextRound(by: ShuffledBy, places: List<AlbumPlace>, notFirst: Set<Int>): List<Int> = when (by) {
        ShuffledBy.Songs, ShuffledBy.ShuffleSongs -> {
            val starts = places.indices.filter { it !in notFirst }.ifEmpty { places.indices.toList() }
            bySongs(places.size, starts.random(random))
        }

        ShuffledBy.Albums -> {
            // An album starts at its first item, so only albums whose first item may start the round can be first.
            val albums = albumsOf(places).shuffled(random)
            val starts = albums.filter { it.first() !in notFirst }
            if (starts.isEmpty()) {
                val order = albums.flatten()
                val at = order.indexOfFirst { it !in notFirst }.coerceAtLeast(0)
                order.drop(at) + order.take(at)
            } else {
                val first = starts.random(random)
                (listOf(first) + albums.filter { it !== first }).flatten()
            }
        }
    }

    /** Songs: [from] first, and then every other item once, in a random order. */
    private fun bySongs(size: Int, from: Int): List<Int> =
        listOf(from) + (0 until size).filter { it != from }.shuffled(random)

    /**
     * Albums: the album of [from], from it to the album's end, then the other albums whole and in a random order, each
     * in its own order, and last the items of the first album that come before [from]. Each item plays once.
     */
    private fun byAlbums(places: List<AlbumPlace>, from: Int): List<Int> {
        val albums = albumsOf(places)
        val first = albums.first { from in it }
        val at = first.indexOf(from)
        return first.drop(at) + albums.filter { it !== first }.shuffled(random).flatten() + first.take(at)
    }

    /** The items of each album, in the album's own order, which the list it was played from may not follow. */
    private fun albumsOf(places: List<AlbumPlace>): List<List<Int>> = places.indices
        .groupBy { places[it].album }
        .values
        .map { album -> album.sortedWith(InAlbumOrder(places)) }
}

/** By disc, then by track, with what is missing last, as the album's list sorts them; the list's order settles ties. */
private class InAlbumOrder(private val places: List<AlbumPlace>) : Comparator<Int> {
    override fun compare(a: Int, b: Int): Int = compareValuesBy(
        a,
        b,
        { places[it].disc == null },
        { places[it].disc },
        { places[it].track == null },
        { places[it].track },
        { it },
    )
}
