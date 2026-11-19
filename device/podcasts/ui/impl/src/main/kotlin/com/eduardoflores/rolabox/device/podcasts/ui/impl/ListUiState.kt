package com.eduardoflores.rolabox.device.podcasts.ui.impl

import arrow.core.Either
import com.eduardoflores.rolabox.device.podcasts.api.PodcastError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a podcasts screen shows: nothing yet, what it lists, or that the podcasts couldn't be read. */
internal sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>

    data class Loaded<T>(val value: T) : ListUiState<T>

    /** Every other [PodcastError] looks the same on the display: nothing the user can do from the wheel. */
    data object Failed : ListUiState<Nothing>

    /**
     * The owner, show or episode this screen is of is no longer in the library. The display says so, and MENU goes
     * back, as Music's screens do.
     */
    data object Gone : ListUiState<Nothing>

    /** A show or an episode from search, read from its feed, and there is no connection (frame 33's words). */
    data object NoConnection : ListUiState<Nothing>

    /** A show from search whose feed couldn't be read (ADR-031, Search). */
    data object FeedUnreadable : ListUiState<Nothing>

    /** An episode from search that its show's feed no longer lists. */
    data object NotInFeed : ListUiState<Nothing>
}

internal fun <T> Flow<Either<PodcastError, T>>.asListState(): Flow<ListUiState<T>> = map { result ->
    result.fold(
        ifLeft = { error ->
            when (error) {
                is PodcastError.OwnerNotFound,
                is PodcastError.ShowNotFound,
                is PodcastError.EpisodeNotFound,
                -> ListUiState.Gone

                PodcastError.NoConnection -> ListUiState.NoConnection

                PodcastError.FeedUnreadable -> ListUiState.FeedUnreadable

                else -> ListUiState.Failed
            }
        },
        ifRight = { value -> ListUiState.Loaded(value) },
    )
}

internal fun <T, R> ListUiState<T>.map(transform: (T) -> R): ListUiState<R> = when (this) {
    ListUiState.Loading -> ListUiState.Loading
    is ListUiState.Loaded -> ListUiState.Loaded(transform(value))
    ListUiState.Failed -> ListUiState.Failed
    ListUiState.Gone -> ListUiState.Gone
    ListUiState.NoConnection -> ListUiState.NoConnection
    ListUiState.FeedUnreadable -> ListUiState.FeedUnreadable
    ListUiState.NotInFeed -> ListUiState.NotInFeed
}

internal const val STOP_TIMEOUT_MILLIS = 5_000L
