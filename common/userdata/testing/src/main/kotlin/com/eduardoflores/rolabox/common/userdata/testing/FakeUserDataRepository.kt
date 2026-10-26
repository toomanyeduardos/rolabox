package com.eduardoflores.rolabox.common.userdata.testing

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.eduardoflores.rolabox.common.storage.api.StorageError
import com.eduardoflores.rolabox.common.userdata.api.AccentColor
import com.eduardoflores.rolabox.common.userdata.api.Clicker
import com.eduardoflores.rolabox.common.userdata.api.DarkThemeConfig
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRow
import com.eduardoflores.rolabox.common.userdata.api.MainMenuRowVisibility
import com.eduardoflores.rolabox.common.userdata.api.UserData
import com.eduardoflores.rolabox.common.userdata.api.UserDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update

@Singleton
class FakeUserDataRepository @Inject constructor() : UserDataRepository {
    private val userData = MutableStateFlow(
        UserData(
            darkThemeConfig = DarkThemeConfig.FOLLOW_SYSTEM,
            accentColor = AccentColor.BLUE,
            clicker = Clicker.ON,
            mainMenuRows = MainMenuRow.entries.associateWith { MainMenuRowVisibility.SHOWN },
        ),
    )
    private val readError = MutableStateFlow<StorageError?>(null)
    private val offlineModeChosen = MutableStateFlow(false)

    /** When set, writes fail with this error and leave the data unchanged. */
    var writeError: StorageError? = null

    // Like a real repository, a Left ends the flow.
    override fun observeUserData(): Flow<Either<StorageError, UserData>> =
        combine(userData, readError) { stored, error -> error?.left() ?: stored.right() }.untilLeft()

    override suspend fun setDarkThemeConfig(config: DarkThemeConfig): Either<StorageError, Unit> =
        write { it.copy(darkThemeConfig = config) }

    override suspend fun setAccentColor(color: AccentColor): Either<StorageError, Unit> =
        write { it.copy(accentColor = color) }

    override suspend fun setClicker(clicker: Clicker): Either<StorageError, Unit> = write { it.copy(clicker = clicker) }

    override suspend fun setMainMenuRowVisibility(
        row: MainMenuRow,
        visibility: MainMenuRowVisibility,
    ): Either<StorageError, Unit> = write { it.copy(mainMenuRows = it.mainMenuRows + (row to visibility)) }

    override fun observeOfflineModeChosen(): Flow<Either<StorageError, Boolean>> =
        combine(offlineModeChosen, readError) { chosen, error -> error?.left() ?: chosen.right() }.untilLeft()

    override suspend fun setOfflineModeChosen(chosen: Boolean): Either<StorageError, Unit> =
        writeError?.left() ?: offlineModeChosen.update { chosen }.right()

    /** Makes the observed flows emit [error] and end. */
    fun setReadError(error: StorageError) {
        readError.value = error
    }

    private fun write(transform: (UserData) -> UserData): Either<StorageError, Unit> =
        writeError?.left() ?: userData.update(transform).right()

    private fun <T> Flow<Either<StorageError, T>>.untilLeft() = transformWhile {
        emit(it)
        it.isRight()
    }
}
