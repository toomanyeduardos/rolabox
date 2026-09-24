package com.eduardoflores.rolabox.core.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.domain.error.FetchError
import com.eduardoflores.rolabox.core.domain.repository.AuthRepository
import com.eduardoflores.rolabox.core.model.AuthUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.transformWhile

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    private val user = MutableStateFlow<Either<FetchError, AuthUser?>>(null.right())

    // Like a real repository, a Left ends the flow.
    override fun observeCurrentUser(): Flow<Either<FetchError, AuthUser?>> = user.transformWhile {
        emit(it)
        it.isRight()
    }

    fun setUser(authUser: AuthUser?) {
        user.value = authUser.right()
    }

    /** Makes the observed flow emit [error] and end. */
    fun setError(error: FetchError) {
        user.value = error.left()
    }
}
