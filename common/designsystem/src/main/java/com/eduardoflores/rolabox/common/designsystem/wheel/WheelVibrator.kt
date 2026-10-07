package com.eduardoflores.rolabox.common.designsystem.wheel

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * Makes the wheel's vibration (ADR-018). It drives the vibrator directly instead of going through
 * the view's haptic feedback, which is silent when the system's touch feedback is off, as it is on
 * many Samsung phones. The wheel's feel is part of the product, so it doesn't depend on that setting.
 */
internal class WheelVibrator(context: Context) {
    private val vibrator: Vibrator? by lazy {
        context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }
    }

    fun perform(haptic: WheelHaptic?) {
        val effect = when (haptic) {
            WheelHaptic.Tick -> VibrationEffect.EFFECT_TICK
            WheelHaptic.Press -> VibrationEffect.EFFECT_CLICK
            null -> return
        }
        vibrator?.vibrate(VibrationEffect.createPredefined(effect))
    }
}
