package com.eduardoflores.rolabox.core.storage.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.storage.api.PreferencesStore
import com.eduardoflores.rolabox.core.storage.api.StorageError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

@Singleton
class FakePreferencesStore @Inject constructor() : PreferencesStore {
    private val values = MutableStateFlow<Map<String, String>>(emptyMap())
    private val readError = MutableStateFlow<StorageError?>(null)

    /** When set, writes fail with this error and leave the values unchanged. */
    var writeError: StorageError? = null

    // Like the real store, a Left ends the flow.
    override fun observeString(key: String): Flow<Either<StorageError, String?>> =
        combine(values, readError) { stored, error -> error?.left() ?: stored[key].right() }
            .distinctUntilChanged()
            .transformWhile {
                emit(it)
                it.isRight()
            }

    override suspend fun setString(key: String, value: String): Either<StorageError, Unit> =
        writeError?.left() ?: values.update { it + (key to value) }.right()

    /** Makes every observed flow emit [error] and end. */
    fun setReadError(error: StorageError) {
        readError.value = error
    }
}
