package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState.PartlyPlayed
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState.Played
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState.Unplayed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// ADR-043, Where the list opens, and the order of a feed's episodes from search.
class EpisodeOrderingTest {
    @Test
    fun oldestFirst_withNothingPlayed_opensOnTheFirstEpisode() {
        assertEquals(0, startingEpisodeOf(List(5) { Unplayed }, EpisodeOrder.Oldest))
    }

    @Test
    fun oldestFirst_with40Of60Played_opensOnThe41st() {
        val played = List(40) { Played } + List(20) { Unplayed }

        assertEquals(40, startingEpisodeOf(played, EpisodeOrder.Oldest))
    }

    @Test
    fun oldestFirst_opensOnTheOnePartlyPlayed_afterTheLastPlayed() {
        assertEquals(2, startingEpisodeOf(listOf(Played, Played, PartlyPlayed, Unplayed), EpisodeOrder.Oldest))
    }

    // An episode skipped and left unplayed earlier doesn't hold the list back: the next is after the last played.
    @Test
    fun oldestFirst_skipsAnEpisodeLeftUnplayedBeforeTheLastPlayed() {
        assertEquals(3, startingEpisodeOf(listOf(Played, Unplayed, Played, Unplayed), EpisodeOrder.Oldest))
    }

    @Test
    fun oldestFirst_withAllPlayed_opensOnTheLastEpisode() {
        assertEquals(2, startingEpisodeOf(List(3) { Played }, EpisodeOrder.Oldest))
    }

    @Test
    fun newestFirst_opensOnTheFirstRow() {
        assertNull(startingEpisodeOf(listOf(Played, Unplayed), EpisodeOrder.Newest))
    }

    @Test
    fun noEpisodes_openOnTheFirstRow() {
        assertNull(startingEpisodeOf(emptyList<PlayedState>(), EpisodeOrder.Oldest))
    }

    @Test
    fun newestFirst_listsTheFeedAsItIs() {
        assertEquals(listOf(0, 1, 2), listedIndices(listOf(3, 2, null), EpisodeOrder.Newest))
    }

    @Test
    fun oldestFirst_reversesTheDatedEpisodes_andKeepsTheUndatedLast() {
        assertEquals(listOf(2, 1, 0, 3, 4), listedIndices(listOf(3, 2, 1, null, null), EpisodeOrder.Oldest))
    }
}
