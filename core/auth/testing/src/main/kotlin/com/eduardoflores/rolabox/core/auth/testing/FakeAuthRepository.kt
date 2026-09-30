package com.eduardoflores.rolabox.core.auth.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.core.auth.api.AuthError
import com.eduardoflores.rolabox.core.auth.api.AuthRepository
import com.eduardoflores.rolabox.core.auth.api.AuthState
import com.eduardoflores.rolabox.core.auth.api.AuthUser
import com.eduardoflores.rolabox.core.auth.api.SignInCredential
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

data class SignInWithEmailRequest(val email: String, val password: String)

data class SignUpRequest(val name: String, val email: String, val password: String)

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.SignedOut)

    /** The user a successful [signIn] signs in as. */
    var signInUser = AuthUser(id = "fake-user", displayName = "Fake User", photoUrl = null)

    /** When set, [signIn] fails with this error and the state doesn't change. */
    var signInError: AuthError? = null

    /** When set, [signInWithEmail] fails with this error and the state doesn't change. */
    var signInWithEmailError: AuthError? = null

    /** When set, [signUp] fails with this error and the state doesn't change. */
    var signUpError: AuthError? = null

    /** When set, [sendPasswordResetEmail] fails with this error. */
    var sendPasswordResetError: AuthError? = null

    /** When set, [signOut] fails with this error and the state doesn't change. */
    var signOutError: AuthError? = null

    /** The credential passed to the last [signIn] call. */
    var lastSignInCredential: SignInCredential? = null
        private set

    /** The email and password passed to the last [signInWithEmail] call. */
    var lastSignInWithEmail: SignInWithEmailRequest? = null
        private set

    /** The name, email and password passed to the last [signUp] call. */
    var lastSignUp: SignUpRequest? = null
        private set

    /** The email passed to the last [sendPasswordResetEmail] call, and how many calls there were. */
    var lastPasswordResetEmail: String? = null
        private set
    var passwordResetEmailCount = 0
        private set

    override fun observeAuthState(): Flow<AuthState> = state

    override suspend fun signIn(credential: SignInCredential): Either<AuthError, AuthUser> {
        lastSignInCredential = credential
        return signInError?.left() ?: signInUser.also { state.value = AuthState.SignedIn(it) }.right()
    }

    override suspend fun signInWithEmail(email: String, password: String): Either<AuthError, AuthUser> {
        lastSignInWithEmail = SignInWithEmailRequest(email, password)
        return signInWithEmailError?.left() ?: signInUser.also { state.value = AuthState.SignedIn(it) }.right()
    }

    /** Signs in as a user with the given [name]. */
    override suspend fun signUp(name: String, email: String, password: String): Either<AuthError, AuthUser> {
        lastSignUp = SignUpRequest(name, email, password)
        return signUpError?.left()
            ?: AuthUser(id = "fake-user", displayName = name, photoUrl = null)
                .also { state.value = AuthState.SignedIn(it) }
                .right()
    }

    override suspend fun sendPasswordResetEmail(email: String): Either<AuthError, Unit> {
        lastPasswordResetEmail = email
        passwordResetEmailCount++
        return sendPasswordResetError?.left() ?: Unit.right()
    }

    override suspend fun signOut(): Either<AuthError, Unit> =
        signOutError?.left() ?: state.update { AuthState.SignedOut }.right()

    fun setAuthState(authState: AuthState) {
        state.value = authState
    }
}
