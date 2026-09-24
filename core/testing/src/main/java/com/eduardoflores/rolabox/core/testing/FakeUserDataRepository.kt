package com.eduardoflores.rolabox.core.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.domain.error.DatabaseError
import com.eduardoflores.rolabox.core.domain.repository.UserDataRepository
import com.eduardoflores.rolabox.core.model.DarkThemeConfig
import com.eduardoflores.rolabox.core.model.UserData
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
    private val readError = MutableStateFlow<DatabaseError?>(null)

    /** When set, writes fail with this error and leave the data unchanged. */
    var writeError: DatabaseError? = null

    // Like a real repository, a Left ends the flow.
    override fun observeUserData(): Flow<Either<DatabaseError, UserData>> =
        combine(data, readError) { userData, error -> error?.left() ?: userData.right() }
            .transformWhile {
                emit(it)
                it.isRight()
            }

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<DatabaseError, Unit> =
        writeError?.left() ?: data.update { it.copy(darkThemeConfig = config) }.right()

    /** Makes the observed flow emit [error] and end. */
    fun setReadError(error: DatabaseError) {
        readError.value = error
    }
}
