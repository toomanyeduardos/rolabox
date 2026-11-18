package com.eduardoflores.rolabox.device.playback.impl

import arrow.core.Either
import com.eduardoflores.rolabox.device.playback.api.ItemDownload
import com.eduardoflores.rolabox.device.playback.api.PlaybackError
import com.eduardoflores.rolabox.device.playback.api.PlaybackItemId
import com.eduardoflores.rolabox.device.playback.impl.downloads.EpisodeDownloader
import com.eduardoflores.rolabox.device.podcasts.api.EpisodeId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Downloads that record what playback asks for and answer with [result]. The player reads an episode's audio from its
 * URL as it is, so the tests that don't test downloading play files.
 */
internal class FakeEpisodeDownloader : EpisodeDownloader {
    override val downloads = MutableStateFlow<Map<PlaybackItemId, ItemDownload>>(emptyMap())

    override val waitsFor = MutableStateFlow<PlaybackError.Waiting?>(null)

    var result: Either<PlaybackError, Unit> = Either.Right(Unit)

    data class Call(val episode: EpisodeId, val url: String, val sizeBytes: Long?)

    val calls = mutableListOf<Call>()

    override suspend fun download(episode: EpisodeId, url: String, sizeBytes: Long?): Either<PlaybackError, Unit> {
        calls += Call(episode, url, sizeBytes)
        return result
    }
}
