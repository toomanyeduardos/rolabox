package com.eduardoflores.rolabox.auth.data.testing

import com.eduardoflores.rolabox.auth.data.api.ResolveStartDestinationUseCase
import com.eduardoflores.rolabox.auth.data.api.StartDestination
import javax.inject.Inject
import javax.inject.Singleton

/** Answers what the test set. The real decision is tested in `:auth:data:impl`. */
@Singleton
class FakeResolveStartDestinationUseCase @Inject constructor() : ResolveStartDestinationUseCase {
    var destination = StartDestination.SignIn

    override suspend operator fun invoke(): StartDestination = destination
}
