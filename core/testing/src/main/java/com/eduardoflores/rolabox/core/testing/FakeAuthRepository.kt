package com.eduardoflores.rolabox.core.testing

import com.eduardoflores.rolabox.core.auth.AuthRepository
import com.eduardoflores.rolabox.core.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    private val user = MutableStateFlow<AuthUser?>(null)

    override val currentUser: Flow<AuthUser?> = user

    fun setUser(authUser: AuthUser?) {
        user.value = authUser
    }
}
