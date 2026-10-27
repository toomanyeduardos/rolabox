package com.eduardoflores.rolabox.common.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.common.designsystem.R

private const val REVOLUTION_MILLIS = 1800
private const val STOP_MILLIS = 700
private const val STOP_DEGREES = 70f
private const val FULL_TURN = 360f
private const val NANOS_PER_MILLI = 1_000_000f

/**
 * Header vinyl. Spins at 33⅓ rpm (1.8s/rev) while audio plays, eases to a stop on pause, and never
 * spins when system animations are off.
 *
 * The spin has no end, so it waits for its frames as an infinite animation: a Compose test cancels
 * those, and the record stays still there instead of keeping the test from ever being idle.
 */
@Composable
fun SpinningVinyl(isPlaying: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val reduceMotion = rememberAnimationsOff()
    val rotation = remember { Animatable(0f) }
    var hasPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        rotation.snapTo(rotation.value % FULL_TURN)
        if (isPlaying) {
            hasPlayed = true
            var last = withInfiniteAnimationFrameNanos { it }
            while (true) {
                val now = withInfiniteAnimationFrameNanos { it }
                val turned = (now - last) / NANOS_PER_MILLI / REVOLUTION_MILLIS * FULL_TURN
                rotation.snapTo((rotation.value + turned) % FULL_TURN)
                last = now
            }
        } else if (hasPlayed) {
            rotation.animateTo(rotation.value + STOP_DEGREES, tween(STOP_MILLIS, easing = LinearOutSlowInEasing))
        }
    }

    Image(
        painter = painterResource(R.drawable.ds_ic_vinyl),
        contentDescription = null,
        modifier = modifier.size(size).graphicsLayer { rotationZ = rotation.value },
    )
}
