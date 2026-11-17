package com.eduardoflores.rolabox.device.podcasts.impl

import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.PlayedState
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.testing.FakePodcastsLocalDataSource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultEpisodeProgressRepositoryTest {
    private val local = FakePodcastsLocalDataSource()
    private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
    private val podcasts = DefaultPodcastsRepository(local, FakeEpisodeDownloads(), clock)
    private val progress = DefaultEpisodeProgressRepository(local, local, clock)

    private suspend fun episodeOf(feedLength: Duration?): EpisodeId = podcasts.addEpisode(
        DirectoryShow(DirectoryId.itunes(1), "The Daily", "NY Times", "https://example.com/feed"),
        DirectoryEpisode("g1", "Who Owns the Moon?", null, feedLength, null, "https://example.com/1.mp3"),
    ).getOrNull()!!

    private suspend fun stateOf(id: EpisodeId): PlayedState = podcasts.getEpisode(id).getOrNull()!!.playedState

    @Test
    fun anEpisodeNeverStarted_isUnplayed_atZero() = runTest {
        val id = episodeOf(30.minutes)

        assertEquals(PlayedState.Unplayed, stateOf(id))
        assertEquals(Duration.ZERO.right(), progress.getPosition(id))
    }

    @Test
    fun aPositionBefore90Percent_isPartlyPlayed() = runTest {
        val id = episodeOf(30.minutes)

        progress.setPosition(id, 26.minutes, length = 30.minutes)

        assertEquals(PlayedState.PartlyPlayed, stateOf(id))
        assertEquals(26.minutes.right(), progress.getPosition(id))
    }

    @Test
    fun aPositionAt90Percent_isPlayed() = runTest {
        val id = episodeOf(30.minutes)

        progress.setPosition(id, 27.minutes, length = 30.minutes)

        assertEquals(PlayedState.Played, stateOf(id))
    }

    @Test
    fun withNoLengthFromThePlayer_theFeedsLengthDecides() = runTest {
        val id = episodeOf(30.minutes)

        progress.setPosition(id, 27.minutes, length = null)

        assertEquals(PlayedState.Played, stateOf(id))
    }

    @Test
    fun thePlayersLength_replacesTheFeeds() = runTest {
        val id = episodeOf(60.minutes)

        progress.setPosition(id, 27.minutes, length = 30.minutes)

        assertEquals(PlayedState.Played, stateOf(id))
        assertEquals(30.minutes, podcasts.getEpisode(id).getOrNull()?.length)
    }

    @Test
    fun thePlayersLength_isKept_whenALaterPositionComesWithout() = runTest {
        val id = episodeOf(60.minutes)
        progress.setPosition(id, 1.minutes, length = 30.minutes)

        progress.setPosition(id, 27.minutes, length = null)

        assertEquals(PlayedState.Played, stateOf(id))
    }

    @Test
    fun withNoLengthKnown_noPositionMakesItPlayed_onlyTheEnd() = runTest {
        val id = episodeOf(feedLength = null)

        progress.setPosition(id, 3.minutes * 60, length = null)
        assertEquals(PlayedState.PartlyPlayed, stateOf(id))

        progress.setEnded(id, length = null)
        assertEquals(PlayedState.Played, stateOf(id))
    }

    @Test
    fun theEnd_isPlayed_atTheEnd() = runTest {
        val id = episodeOf(30.minutes)
        progress.setPosition(id, 10.minutes, length = 31.minutes)

        progress.setEnded(id, length = 31.minutes)

        assertEquals(PlayedState.Played, stateOf(id))
        assertEquals(31.minutes.right(), progress.getPosition(id))
    }

    @Test
    fun playedStaysPlayed_andTheNewPositionIsStored() = runTest {
        val id = episodeOf(30.minutes)
        progress.setPosition(id, 29.minutes, length = 30.minutes)

        progress.setPosition(id, 5.seconds, length = 30.minutes)

        assertEquals(PlayedState.Played, stateOf(id))
        assertEquals(5.seconds.right(), progress.getPosition(id))
    }

    @Test
    fun aPositionBackAtZero_ofAnEpisodeNotPlayed_isUnplayedAgain() = runTest {
        val id = episodeOf(30.minutes)
        progress.setPosition(id, 10.minutes, length = 30.minutes)

        progress.setPosition(id, Duration.ZERO, length = 30.minutes)

        assertEquals(PlayedState.Unplayed, stateOf(id))
    }

    @Test
    fun anEpisodeThatIsntThere_isEpisodeNotFound() = runTest {
        val missing = EpisodeId(9)

        assertEquals(PodcastError.EpisodeNotFound(missing).left(), progress.getPosition(missing))
        assertEquals(PodcastError.EpisodeNotFound(missing).left(), progress.setPosition(missing, 1.minutes, null))
        assertEquals(PodcastError.EpisodeNotFound(missing).left(), progress.setEnded(missing, null))
    }

    private companion object {
        const val NOW = 1_000L
    }
}
