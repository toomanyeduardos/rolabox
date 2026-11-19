package com.eduardoflores.rolabox.device.podcasts.ui.impl

import arrow.core.Either
import arrow.core.left
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryEpisode
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryId
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.PodcastDirectory
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import com.eduardoflores.rolabox.device.podcasts.api.ShowFeed
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchEpisodeKey
import com.eduardoflores.rolabox.device.podcasts.ui.api.PodcastSearchShowKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.transformWhile

// What the screens opened from search share (ADR-031, Search): their keys, and the feed they read.

internal fun PodcastSearchShowKey.toDirectoryShow() =
    DirectoryShow(directoryId = DirectoryId(directoryId), title = title, creator = creator, feedUrl = feedUrl)

internal fun DirectoryShow.toKey() =
    PodcastSearchShowKey(directoryId = directoryId.value, title = title, creator = creator, feedUrl = feedUrl)

internal fun episodeKeyOf(show: DirectoryShow, episode: DirectoryEpisode) =
    PodcastSearchEpisodeKey(show = show.toKey(), guid = episode.guid, audioUrl = episode.audioUrl)

/**
 * The feed of [show], read once the phone is connected: [PodcastError.NoConnection] until then, and read again when
 * the connection comes back after it, so the screen comes back by itself (frame 33). It ends once the feed is read or
 * fails for any other reason, so a connection that comes and goes later changes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun PodcastDirectory.feedOf(show: DirectoryShow): Flow<Either<PodcastError, ShowFeed>> = observeConnected()
    .distinctUntilChanged()
    .transformLatest { connected -> emit(if (connected) show(show) else PodcastError.NoConnection.left()) }
    .transformWhile {
        emit(it)
        it.leftOrNull() == PodcastError.NoConnection
    }

/** The episode of the feed with [guid], else with [audioUrl], as the feed identifies an episode (ADR-031, rule 2). */
internal fun ShowFeed.episodeOf(guid: String?, audioUrl: String): DirectoryEpisode? =
    guid?.let { wanted -> episodes.firstOrNull { it.guid == wanted } }
        ?: episodes.firstOrNull { it.audioUrl == audioUrl }
