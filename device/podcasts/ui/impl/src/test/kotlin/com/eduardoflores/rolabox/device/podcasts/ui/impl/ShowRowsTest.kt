package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDot
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.podcasts.testing.PodcastSamples
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowRowsTest {
    private val words = ShowWords(follow = "Follow", on = "On", off = "Off", unknownEpisode = "Unknown episode")

    @Test
    fun unplayedAndNotDownloaded_hasAFullDotAndTheArrow() {
        assertEquals(
            DeviceListRow(
                "A Heat Wave Over the North Atlantic",
                dot = DeviceListDot.Unplayed,
                download = DeviceListDownload.NotDownloaded,
            ),
            episodeRow(PodcastSamples.unplayed, words.unknownEpisode),
        )
    }

    @Test
    fun downloading_showsItsWholePercent() {
        assertEquals(
            DeviceListDownload.Downloading(percent = 38),
            episodeRow(PodcastSamples.downloading, words.unknownEpisode).download,
        )
    }

    @Test
    fun downloaded_hasNothingAfterItsTitle() {
        val row = episodeRow(PodcastSamples.downloaded, words.unknownEpisode)

        assertEquals(null, row.download)
        assertEquals(DeviceListDot.Unplayed, row.dot)
    }

    @Test
    fun partlyPlayed_hasAHalfDot() {
        assertEquals(DeviceListDot.PartlyPlayed, episodeRow(PodcastSamples.partlyPlayed, words.unknownEpisode).dot)
    }

    @Test
    fun played_hasNoDot() {
        assertEquals(null, episodeRow(PodcastSamples.played, words.unknownEpisode).dot)
    }

    @Test
    fun episodeWithNoTitle_isUnknown() {
        val row = episodeRow(PodcastSamples.played.copy(title = null), words.unknownEpisode)

        assertEquals("Unknown episode", row.label)
    }

    @Test
    fun followIsFirst_withItsValue_thenTheEpisodes() {
        val on = showRows(ShowContent(PodcastSamples.theDaily, PodcastSamples.theDailyEpisodes), words)
        val off = showRows(ShowContent(PodcastSamples.hardFork, emptyList()), words)

        assertEquals(DeviceListRow("Follow", value = "On"), on.first())
        assertEquals(PodcastSamples.theDailyEpisodes.size + 1, on.size)
        assertEquals(listOf(DeviceListRow("Follow", value = "Off")), off)
    }
}
