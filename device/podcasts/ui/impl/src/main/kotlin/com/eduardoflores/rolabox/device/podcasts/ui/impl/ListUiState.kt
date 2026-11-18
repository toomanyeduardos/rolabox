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
}

internal fun <T> Flow<Either<PodcastError, T>>.asListState(): Flow<ListUiState<T>> = map { result ->
    result.fold(
        ifLeft = { error ->
            when (error) {
                is PodcastError.OwnerNotFound,
                is PodcastError.ShowNotFound,
                is PodcastError.EpisodeNotFound,
                -> ListUiState.Gone

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
}

internal const val STOP_TIMEOUT_MILLIS = 5_000L
