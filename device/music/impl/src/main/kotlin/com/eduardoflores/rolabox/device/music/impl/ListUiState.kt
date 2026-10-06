package com.eduardoflores.rolabox.device.music.impl

import arrow.core.Either
import com.eduardoflores.rolabox.device.library.api.LibraryError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a list on the display shows: nothing yet, its items (possibly none), or that the library couldn't be read. */
internal sealed interface ListUiState<out T> {
    data object Loading : ListUiState<Nothing>

    data class Loaded<T>(val items: List<T>) : ListUiState<T>

    /** Every kind of [LibraryError] looks the same on the display: nothing the user can do from the wheel. */
    data object Failed : ListUiState<Nothing>
}

internal fun <T> Flow<Either<LibraryError, List<T>>>.asListState(): Flow<ListUiState<T>> = map { result ->
    result.fold(ifLeft = { ListUiState.Failed }, ifRight = { items -> ListUiState.Loaded(items) })
}

internal fun <T, R> ListUiState<T>.map(transform: (T) -> R): ListUiState<R> = when (this) {
    ListUiState.Loading -> ListUiState.Loading
    is ListUiState.Loaded -> ListUiState.Loaded(items.map(transform))
    ListUiState.Failed -> ListUiState.Failed
}
