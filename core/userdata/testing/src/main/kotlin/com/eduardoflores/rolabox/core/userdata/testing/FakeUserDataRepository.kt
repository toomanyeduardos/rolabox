package com.eduardoflores.rolabox.core.userdata.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.core.userdata.api.UserData
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

@Singleton
class FakeUserDataRepository @Inject constructor() : UserDataRepository {
    private val data = MutableStateFlow(UserData(darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM))
    private val readError = MutableStateFlow<StorageError?>(null)

    /** When set, writes fail with this error and leave the data unchanged. */
    var writeError: StorageError? = null

    // Like a real repository, a Left ends the flow.
    override fun observeUserData(): Flow<Either<StorageError, UserData>> =
        combine(data, readError) { userData, error -> error?.left() ?: userData.right() }
            .transformWhile {
                emit(it)
                it.isRight()
            }

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit> =
        writeError?.left() ?: data.update { it.copy(darkThemeConfig = config) }.right()

    /** Makes the observed flow emit [error] and end. */
    fun setReadError(error: StorageError) {
        readError.value = error
    }
}
