package com.eduardoflores.rolabox.splash

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.R
import com.eduardoflores.rolabox.common.designsystem.R as DesignSystemR

private const val SPIN_MILLIS = 1_000
private const val FULL_TURN = 360f

/**
 * The splash the app shows after the system splash, until it knows where to route the user. It
 * draws the same record at the same size and position as the system splash (a 288dp canvas,
 * centered, on the same background), so the handoff can't be seen. The colors follow the system's light or dark
 * setting, like the system splash, not the in-app theme.
 *
 * @param startAngle the angle the record starts from, so it continues where the system splash's
 * animation was. With [animate] off it's the angle the record stays at.
 * @param animate whether the record spins. Off when the user removed animations: the record is
 * still. The wordmark is always fully visible, so even a splash that is on screen for a moment shows it.
 */
@Composable
internal fun RolaboxSplash(
    modifier: Modifier = Modifier,
    startAngle: Float = 0f,
    animate: Boolean = rememberAnimationsEnabled(),
) {
    val angle = remember { Animatable(startAngle) }

    LaunchedEffect(animate, startAngle) {
        angle.snapTo(startAngle)
        if (animate) {
            angle.animateTo(startAngle + FULL_TURN, infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing)))
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(DesignSystemR.color.ds_metal_body)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_splash_vinyl),
            contentDescription = null,
            modifier = Modifier
                .size(288.dp)
                .graphicsLayer { rotationZ = angle.value },
        )
        Image(
            painter = painterResource(R.drawable.splash_branding),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 36.dp),
        )
    }
}

/** False when the user removed animations in the system settings (animator duration scale 0). */
@Composable
internal fun rememberAnimationsEnabled(): Boolean = remember { ValueAnimator.areAnimatorsEnabled() }
