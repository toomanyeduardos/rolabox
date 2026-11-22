package com.eduardoflores.rolabox.auth.ui.impl

import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreScreenshot
import com.eduardoflores.rolabox.common.designsystem.screenshot.StoreSet
import com.eduardoflores.rolabox.common.designsystem.theme.RolaboxTheme
import org.junit.Test

/** Sign in, with the way to use Rolabox without an account, for the Play listing (ADR-039). */
class StoreScreenshotTest : StoreScreenshot() {
    @Test
    fun signIn() = shoot(StoreSet.Play, "07-sign-in") {
        RolaboxTheme {
            SignInScreen(
                state = SignInUiState(email = "maren@example.com", password = "paper-satellites"),
                onEmailChange = {},
                onPasswordChange = {},
                onSignInClick = {},
                onForgotPasswordClick = {},
                onProviderClick = {},
                onOfflineClick = {},
                onCreateAccountClick = {},
            )
        }
    }
}
