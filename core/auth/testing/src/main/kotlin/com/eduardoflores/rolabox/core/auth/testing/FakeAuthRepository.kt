package com.eduardoflores.rolabox.core.auth.testing

import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    private val user = MutableStateFlow<AuthUser?>(null)

    override fun observeCurrentUser(): Flow<AuthUser?> = user

    fun setUser(authUser: AuthUser?) {
        user.value = authUser
    }
}
