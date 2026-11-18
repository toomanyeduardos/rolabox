package com.eduardoflores.rolabox.device.podcasts.local.impl

import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import com.eduardoflores.rolabox.device.podcasts.api.ShowId
import com.eduardoflores.rolabox.device.podcasts.local.api.ArtworkName
import com.eduardoflores.rolabox.device.podcasts.local.api.PodcastArtworkLocalDataSource.Companion.ARTWORK_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The files of the artwork are named for what they are of and their URL (ADR-031, Artwork). */
class ArtworkFileNameTest {
    @Test
    fun aName_isItsOwnersKindAndId_andItsUrls() {
        val episode = ArtworkName.OfEpisode(EpisodeId(7), "https://example.com/a.jpg").fileName()

        assertTrue(episode, episode.matches(Regex("episode-7-[0-9a-f]{16}\\.jpg")))
        assertTrue(ArtworkName.OfShow(ShowId(7), "https://example.com/a.jpg").fileName().startsWith("show-7-"))
    }

    @Test
    fun anImageTheFeedChanged_isANewFile() {
        assertNotEquals(
            ArtworkName.OfShow(ShowId(1), "https://example.com/a.jpg").fileName(),
            ArtworkName.OfShow(ShowId(1), "https://example.com/b.jpg").fileName(),
        )
    }

    @Test
    fun theSampleSize_neverGoesUnderNowPlayingsSize() {
        assertEquals(1, sampleSizeFor(ARTWORK_SIZE))
        assertEquals(1, sampleSizeFor(ARTWORK_SIZE * 2 - 1))
        assertEquals(2, sampleSizeFor(ARTWORK_SIZE * 2))
        assertEquals(4, sampleSizeFor(3_000))
    }
}
