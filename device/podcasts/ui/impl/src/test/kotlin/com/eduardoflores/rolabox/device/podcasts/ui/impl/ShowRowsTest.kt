package com.eduardoflores.rolabox.device.podcasts.ui.impl

import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDot
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListDownload
import com.eduardoflores.rolabox.common.designsystem.component.DeviceListRow
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeOrder
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
    fun followIsFirst_withItsValue_thenOrder_thenTheEpisodes() {
        val on = showRows(ShowContent(PodcastSamples.theDaily, PodcastSamples.theDailyEpisodes), words, orderWords)
        val off = showRows(ShowContent(PodcastSamples.hardFork, emptyList()), words, orderWords)

        assertEquals(DeviceListRow("Follow", value = "On"), on[0])
        assertEquals(DeviceListRow("Order", value = "Newest"), on[1])
        assertEquals(PodcastSamples.theDailyEpisodes.size + 2, on.size)
        assertEquals(listOf(DeviceListRow("Follow", value = "Off"), DeviceListRow("Order", value = "Newest")), off)
    }

    // ADR-043: Order says the order the episodes are listed in, the user's or the feed's.
    @Test
    fun order_saysOldest_forAShowListedOldestFirst() {
        val show = PodcastSamples.theDaily.copy(episodeOrder = EpisodeOrder.Oldest)

        assertEquals(
            DeviceListRow("Order", value = "Oldest"),
            showRows(ShowContent(show, emptyList()), words, orderWords)[1],
        )
    }

    private val orderWords = OrderWords(order = "Order", newest = "Newest", oldest = "Oldest")
}
