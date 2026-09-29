package com.eduardoflores.rolabox.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eduardoflores.rolabox.core.designsystem.R
import kotlinx.coroutines.isActive

private const val REVOLUTION_MILLIS = 1800
private const val STOP_MILLIS = 700
private const val STOP_DEGREES = 70f
private const val FULL_TURN = 360f

/**
 * Header vinyl. Spins at 33⅓ rpm (1.8s/rev) while audio plays, eases to a stop on pause, and never
 * spins when system animations are off.
 */
@Composable
fun SpinningVinyl(isPlaying: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val rotation = remember { Animatable(0f) }
    var hasPlayed by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        rotation.snapTo(rotation.value % FULL_TURN)
        if (isPlaying) {
            hasPlayed = true
            while (isActive) {
                rotation.animateTo(rotation.value + FULL_TURN, tween(REVOLUTION_MILLIS, easing = LinearEasing))
            }
        } else if (hasPlayed) {
            rotation.animateTo(rotation.value + STOP_DEGREES, tween(STOP_MILLIS, easing = LinearOutSlowInEasing))
        }
    }

    Image(
        painter = painterResource(R.drawable.ic_vinyl),
        contentDescription = null,
        modifier = modifier.size(size).graphicsLayer { rotationZ = rotation.value },
    )
}
