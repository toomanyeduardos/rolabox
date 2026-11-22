package com.eduardoflores.rolabox.auth.data.impl

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.auth.data.api.AuthError
import com.eduardoflores.rolabox.auth.data.api.AuthState
import com.eduardoflores.rolabox.auth.data.api.AuthUser
import com.eduardoflores.rolabox.auth.data.api.DeleteAccountError
import com.eduardoflores.rolabox.auth.data.api.SignInCredential
import com.eduardoflores.rolabox.auth.data.testing.FakeAuthRepository
import com.eduardoflores.rolabox.common.sync.api.RemoteError
import com.eduardoflores.rolabox.common.sync.testing.FakeSyncRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// ADR-037, rule 2: re-authenticate, delete the data in the cloud with sync paused, delete the account, then erase
// the app's data on the phone. The account is kept whenever something before its own delete fails. Once it's deleted,
// Credential Manager's saved state is cleared, as on sign-out (ADR-009, rule 6).
class DeleteAccountUseCaseTest {
    private val auth = FakeAuthRepository()
    private val sync = FakeSyncRepository()
    private val eraser = RecordingEraser()
    private val clearer = RecordingClearer()
    private val deleteAccount = DefaultDeleteAccountUseCase(auth, sync, eraser, clearer)

    @Before
    fun signIn() {
        auth.setAuthState(AuthState.SignedIn(AuthUser(id = USER, displayName = "Alice", photoUrl = null)))
    }

    @Test
    fun withAPassword_runsEveryStepInOrder() = runTest {
        assertEquals(Unit.right(), deleteAccount(PASSWORD))

        assertEquals(listOf("reauthenticateWithPassword($PASSWORD)", "deleteUser"), auth.accountCalls)
        assertEquals(listOf("deleteRemoteData($USER)"), sync.calls)
        assertTrue(auth.deleted)
        assertEquals(AuthState.SignedOut, auth.currentState())
        assertEquals(1, eraser.erases)
        assertEquals(1, clearer.clears)
    }

    // Firebase signs out by itself, so signOut() doesn't clear it. The erase closes the app, so the clear comes first.
    @Test
    fun theSavedSignIn_isClearedAfterTheAccountIsDeleted_andBeforeTheErase() = runTest {
        clearer.onClear = {
            assertTrue("the account is deleted first", auth.deleted)
            assertEquals("the phone's data is erased after", 0, eraser.erases)
        }

        deleteAccount(PASSWORD)

        assertEquals(1, clearer.clears)
    }

    // The account is gone, so a failed clear doesn't fail the deletion, and the phone's data is still erased.
    @Test
    fun clearingTheSavedSignInFailing_stillSucceeds_andErases() = runTest {
        clearer.result = AuthError.CredentialStateNotCleared.left()

        assertEquals(Unit.right(), deleteAccount(PASSWORD))

        assertTrue(auth.deleted)
        assertEquals(1, eraser.erases)
    }

    // The phone's data is erased only once the account is gone, so a failed deletion keeps everything.
    @Test
    fun thePhonesData_isErasedAfterTheAccountIsDeleted() = runTest {
        eraser.onErase = { assertTrue("the account is deleted first", auth.deleted) }

        deleteAccount(PASSWORD)

        assertEquals(1, eraser.erases)
    }

    // The account is gone, so Android refusing doesn't fail the deletion. Sync resumes, for nobody, as signed out.
    @Test
    fun androidRefusingToErase_stillSucceeds_andResumesSync() = runTest {
        eraser.accepts = false

        assertEquals(Unit.right(), deleteAccount(PASSWORD))

        assertTrue(auth.deleted)
        assertNull(sync.pausedFor)
    }

    @Test
    fun withGoogle_signsInAgainWithTheCredential() = runTest {
        assertEquals(Unit.right(), deleteAccount(GOOGLE))

        assertEquals(listOf("reauthenticate($GOOGLE)", "deleteUser"), auth.accountCalls)
        assertTrue(auth.deleted)
    }

    // The data is deleted before the account: once the account is gone, the rules refuse it.
    @Test
    fun theDataInTheCloud_isDeletedBeforeTheAccount() = runTest {
        sync.deleteRemoteError = RemoteError.Unavailable

        deleteAccount(PASSWORD)

        assertFalse(auth.deleted)
    }

    // Offline, or with the wrong password: nothing is deleted.
    @Test
    fun signingInAgainFailing_deletesNothing() = runTest {
        auth.reauthenticateError = AuthError.Network

        assertEquals(DeleteAccountError.Auth(AuthError.Network).left(), deleteAccount(PASSWORD))

        assertEquals(emptyList<String>(), sync.calls)
        assertFalse(auth.deleted)
        assertNull(sync.pausedFor)
        assertEquals(0, eraser.erases)
        assertEquals(0, clearer.clears)
    }

    @Test
    fun wrongPassword_isInvalidCredential() = runTest {
        auth.reauthenticateError = AuthError.InvalidCredential

        assertEquals(DeleteAccountError.Auth(AuthError.InvalidCredential).left(), deleteAccount(PASSWORD))
    }

    // Kept paused, so the phone doesn't write the data back into the account, which still exists.
    @Test
    fun deletingTheDataFailing_keepsTheAccount_andTheSyncPause() = runTest {
        sync.deleteRemoteError = RemoteError.Unavailable

        assertEquals(DeleteAccountError.Remote(RemoteError.Unavailable).left(), deleteAccount(PASSWORD))

        assertFalse(auth.deleted)
        assertEquals(USER, sync.pausedFor)
        assertEquals(0, eraser.erases)
        assertEquals(0, clearer.clears)
    }

    @Test
    fun deletingTheAccountFailing_keepsTheSyncPause_andTheDataOnThePhone() = runTest {
        auth.deleteUserError = AuthError.RequiresRecentLogin

        assertEquals(DeleteAccountError.Auth(AuthError.RequiresRecentLogin).left(), deleteAccount(PASSWORD))

        assertEquals(USER, sync.pausedFor)
        assertEquals(0, eraser.erases)
        assertEquals(0, clearer.clears)
    }

    @Test
    fun retryingAfterAFailure_finishesTheJob() = runTest {
        sync.deleteRemoteError = RemoteError.Unavailable
        deleteAccount(PASSWORD)
        sync.deleteRemoteError = null

        assertEquals(Unit.right(), deleteAccount(PASSWORD))

        assertTrue(auth.deleted)
        assertEquals(1, eraser.erases)
    }

    @Test
    fun cancel_resumesSync() = runTest {
        sync.deleteRemoteError = RemoteError.Unavailable
        deleteAccount(PASSWORD)

        deleteAccount.cancel()

        assertNull(sync.pausedFor)
    }

    @Test
    fun signedOut_deletesNothing() = runTest {
        auth.setAuthState(AuthState.SignedOut)

        assertEquals(DeleteAccountError.Auth(AuthError.Unknown).left(), deleteAccount(PASSWORD))

        assertEquals(emptyList<String>(), auth.accountCalls)
        assertEquals(emptyList<String>(), sync.calls)
    }

    private suspend fun FakeAuthRepository.currentState() = observeAuthState().first()

    private class RecordingEraser : AppDataEraser {
        var accepts = true
        var onErase: () -> Unit = {}
        var erases = 0
            private set

        override fun erase(): Boolean {
            onErase()
            erases++
            return accepts
        }
    }

    private class RecordingClearer : CredentialStateClearer {
        var result: Either<AuthError, Unit> = Unit.right()
        var onClear: () -> Unit = {}
        var clears = 0
            private set

        override suspend fun clear(): Either<AuthError, Unit> {
            onClear()
            clears++
            return result
        }
    }

    private companion object {
        const val USER = "alice"
        const val PASSWORD = "correct horse"
        val GOOGLE = SignInCredential.GoogleIdToken("token")
    }
}
