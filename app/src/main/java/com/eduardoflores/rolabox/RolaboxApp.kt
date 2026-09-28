package com.eduardoflores.rolabox

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.eduardoflores.rolabox.core.designsystem.component.RolaboxTopBar
import com.eduardoflores.rolabox.core.domain.StartDestination
import com.eduardoflores.rolabox.splash.RolaboxSplash
import com.eduardoflores.rolabox.splash.SplashHandoff
import com.eduardoflores.rolabox.splash.rememberAnimationsEnabled

private const val SPLASH_FADE_OUT_MILLIS = 250

/**
 * The splash covers the screen until the start destination is known. Then it fades out, over the
 * destination, which is already composed underneath. If it's known straight away, that's just a
 * quick fade.
 *
 * When the system splash is showing ([awaitingSystemSplash]), the splash also stays until it has
 * taken over from it, even if the destination is already known. Otherwise a start that resolves
 * before the first frame would go from the system splash straight to the destination.
 */
@Composable
internal fun RolaboxApp(
    startup: StartupUiState,
    splashHandoff: SplashHandoff?,
    awaitingSystemSplash: Boolean,
    modifier: Modifier = Modifier,
) {
    val animationsEnabled = rememberAnimationsEnabled()

    Box(modifier = modifier.fillMaxSize()) {
        if (startup is StartupUiState.Ready) {
            when (startup.destination) {
                StartDestination.Home -> HomeScreen()
                StartDestination.SignIn -> SignInContent(modifier = Modifier.fillMaxSize())
            }
        }
        AnimatedVisibility(
            visible = startup is StartupUiState.Resolving || (awaitingSystemSplash && splashHandoff == null),
            exit = if (animationsEnabled) fadeOut(tween(SPLASH_FADE_OUT_MILLIS)) else ExitTransition.None,
        ) {
            RolaboxSplash(
                startAngle = splashHandoff?.iconAngle ?: 0f,
                animate = animationsEnabled,
            )
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { RolaboxTopBar(title = stringResource(R.string.app_name)) },
    ) { innerPadding ->
        HomeContent(modifier = Modifier.padding(innerPadding))
    }
}
