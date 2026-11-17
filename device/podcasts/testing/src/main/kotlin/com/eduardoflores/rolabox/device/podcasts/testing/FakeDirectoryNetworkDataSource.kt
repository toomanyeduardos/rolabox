package com.eduardoflores.rolabox.device.podcasts.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkDataSource
import com.eduardoflores.rolabox.device.podcasts.network.api.DirectoryNetworkError

/**
 * A directory's network side that tests fill in: the [shows] and [episodes] of each text, none when unset, or an
 * error for each kind of request. Every request is recorded in [requests], as the kind and the text.
 */
class FakeDirectoryNetworkDataSource : DirectoryNetworkDataSource {
    var shows: Map<String, List<DirectoryShow>> = emptyMap()
    var episodes: Map<String, List<FoundEpisode>> = emptyMap()
    var showsError: DirectoryNetworkError? = null
    var episodesError: DirectoryNetworkError? = null

    val requests = mutableListOf<Pair<Kind, String>>()

    enum class Kind { Shows, Episodes }

    override suspend fun searchShows(text: String): Either<DirectoryNetworkError, List<DirectoryShow>> {
        requests += Kind.Shows to text
        return showsError?.left() ?: shows[text].orEmpty().right()
    }

    override suspend fun searchEpisodes(text: String): Either<DirectoryNetworkError, List<FoundEpisode>> {
        requests += Kind.Episodes to text
        return episodesError?.left() ?: episodes[text].orEmpty().right()
    }
}
