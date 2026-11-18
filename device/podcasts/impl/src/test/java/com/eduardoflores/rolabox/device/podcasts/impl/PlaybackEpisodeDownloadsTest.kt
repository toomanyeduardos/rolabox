package com.eduardoflores.rolabox.device.podcasts.impl

import com.eduardoflores.rolabox.device.library.api.SongId
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.testing.FakeItemDownloads
import com.eduardoflores.rolabox.device.podcasts.api.DownloadState
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Playback's downloads, as each episode's download state (ADR-031, Downloads). */
class PlaybackEpisodeDownloadsTest {
    private val playback = FakeItemDownloads()
    private val downloads = PlaybackEpisodeDownloads(playback)

    @Test
    fun waitingAndRunning_areDownloading_withTheirFraction_andDone_isDownloaded() = runTest {
        playback.downloads = mapOf(
            PlaybackItemId.Episode(EpisodeId(1)) to ItemDownload.Waiting(0.2f),
            PlaybackItemId.Episode(EpisodeId(2)) to ItemDownload.Downloading(0.5f),
            PlaybackItemId.Episode(EpisodeId(3)) to ItemDownload.Done,
        )

        assertEquals(
            mapOf(
                EpisodeId(1) to DownloadState.Downloading(0.2f),
                EpisodeId(2) to DownloadState.Downloading(0.5f),
                EpisodeId(3) to DownloadState.Downloaded,
            ),
            downloads.observe().first(),
        )
    }

    @Test
    fun aFailedDownload_isNone_soTheRowShowsTheArrowAgain() = runTest {
        playback.downloads = mapOf(PlaybackItemId.Episode(EpisodeId(1)) to ItemDownload.Failed)

        assertEquals(emptyMap<EpisodeId, DownloadState>(), downloads.observe().first())
    }

    @Test
    fun aSong_isntAnEpisode() = runTest {
        playback.downloads = mapOf(PlaybackItemId.Song(SongId(1)) to ItemDownload.Done)

        assertEquals(emptyMap<EpisodeId, DownloadState>(), downloads.observe().first())
    }
}
