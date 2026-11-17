package com.eduardoflores.rolabox.device.podcasts.network.api

import arrow.core.Either
import com.eduardoflores.rolabox.device.podcasts.api.DirectoryShow
import com.eduardoflores.rolabox.device.podcasts.api.FoundEpisode

/**
 * The podcast directory (ADR-031, The directory). Only the text and the phone's country are sent. Its answers are the
 * part's own models, so another directory can replace it. A show with no feed URL, and an episode with no audio, are
 * left out of an answer. No results is an empty list.
 */
interface DirectoryNetworkDataSource {
    /** Shows matching [text] by name or creator, in the directory's order. */
    suspend fun searchShows(text: String): Either<DirectoryNetworkError, List<DirectoryShow>>

    /** Episodes matching [text] by title or description, each with its show, in the directory's order. */
    suspend fun searchEpisodes(text: String): Either<DirectoryNetworkError, List<FoundEpisode>>
}

/** Why the directory gave no answer (ADR-007). */
sealed interface DirectoryNetworkError {
    /** No network, a connection that failed, or a timeout. */
    data object Unreachable : DirectoryNetworkError

    /** The directory refused for the rate of requests: HTTP 403 or 429. */
    data object Busy : DirectoryNetworkError

    /** Any other answer that can't be used: another HTTP error, or a body that can't be read. */
    data object Failed : DirectoryNetworkError
}
