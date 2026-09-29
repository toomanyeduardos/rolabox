package com.eduardoflores.rolabox.core.domain

import arrow.core.Either
import com.eduardoflores.rolabox.core.storage.api.StorageError
import com.eduardoflores.rolabox.core.userdata.api.UserDataRepository
import javax.inject.Inject

/**
 * Turns offline mode off, for a step that reaches the network, such as Google sign-in (ADR-008,
 * rule 7; ADR-009, rule 5). Run it when the user starts such a step, before the step itself.
 *
 * It's a use case (ADR-001) because it holds a business rule that more than one ViewModel needs:
 * the app makes no network requests while offline mode is chosen, so a network step has to end it
 * first. Signing out or going offline again later is the user's choice, and isn't done here.
 */
class LeaveOfflineModeUseCase @Inject constructor(private val userDataRepository: UserDataRepository) {
    suspend operator fun invoke(): Either<StorageError, Unit> = userDataRepository.setOfflineModeChosen(false)
}
