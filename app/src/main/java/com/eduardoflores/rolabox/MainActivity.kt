package com.eduardoflores.rolabox

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eduardoflores.rolabox.core.designsystem.theme.RolaboxTheme
import com.eduardoflores.rolabox.splash.SplashHandoff
import com.eduardoflores.rolabox.splash.systemSplashIconAngle
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainActivityViewModel by viewModels()
    private val startupViewModel: StartupViewModel by viewModels()
    private var firstFrameReady = false
    private var splashHandoff by mutableStateOf<SplashHandoff?>(null)

    // The system splash only shows when the app starts, not when the Activity is recreated (say, by a
    // rotation), which is when there is a saved state.
    private var awaitingSystemSplash = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        awaitingSystemSplash = savedInstanceState == null
        splashScreen.setKeepOnScreenCondition { !firstFrameReady }
        splashScreen.setOnExitAnimationListener(::handOffToComposeSplash)
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val darkTheme = uiState.shouldUseDarkTheme(isSystemInDarkTheme())

            // enableEdgeToEdge() picks bar icon colors from the system setting; re-apply it so
            // they follow the in-app preference when it differs from the system.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                )
                onDispose {}
            }

            val startup by startupViewModel.uiState.collectAsStateWithLifecycle()

            RolaboxTheme(darkTheme = darkTheme, accent = uiState.accent) {
                RolaboxApp(
                    startup = startup,
                    splashHandoff = splashHandoff,
                    awaitingSystemSplash = awaitingSystemSplash,
                )
            }
        }

        // The system splash stays only until the first frame, which already has RolaboxSplash in it.
        // The Compose splash then keeps the screen until the start destination is known.
        window.decorView.doOnPreDraw { firstFrameReady = true }
    }

    // Removes the system splash once RolaboxSplash has drawn the record at the angle the system
    // splash's animation had reached.
    private fun handOffToComposeSplash(provider: SplashScreenViewProvider) {
        splashHandoff = SplashHandoff(iconAngle = provider.iconAngle())
        window.decorView.doOnPreDraw { provider.remove() }
    }
}

private fun SplashScreenViewProvider.iconAngle(): Float {
    // Before Android 12 the system splash doesn't animate its icon, and with animations removed it doesn't
    // either, so the record is at its resting angle.
    val animated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        ValueAnimator.areAnimatorsEnabled() &&
        iconAnimationStartMillis > 0
    return if (animated) {
        systemSplashIconAngle(System.currentTimeMillis() - iconAnimationStartMillis, iconAnimationDurationMillis)
    } else {
        0f
    }
}

// Same defaults androidx.activity uses for the three-button navigation bar scrim.
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
